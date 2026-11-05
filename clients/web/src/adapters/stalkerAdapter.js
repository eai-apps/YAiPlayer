/**
 * YAiPlayer Web — Stalker / Ministra Portal Adapter (ADAPT-09.2 / 09.3).
 * Handles handshake, authentication, session lifecycle, channel catalog pagination,
 * and create_link playback stream resolution through approved YAiPlayer Worker proxy.
 *
 * Security & Reliability:
 * - Session state is strictly in-memory (never persisted in plain localStorage/IDB).
 * - YAi platform tokens strictly separated from IPTV provider portal tokens.
 * - Prevents returning portal /c/ web UI as playable streams.
 * - Monotonic generation IDs prevent stale background fetch operations from corrupting state.
 * - Missing profile is rejected; unknown status is never default-authorized.
 * - Bounded pagination budget (max 50 pages) with explicit partial-catalog reporting.
 */

import { proxyStalkerPortal, resolveStreamPlaybackUrl } from './mediaAdapter.js';

let activeGeneration = 0;
const sessions = new Map();

/**
 * Increments generation and clears all in-memory portal sessions (on logout or account switch).
 */
export function invalidateStalkerSessions() {
  activeGeneration++;
  sessions.clear();
}

function normalizeMac(mac) {
  if (!mac || typeof mac !== 'string') return '';
  return mac.trim().toLowerCase();
}

function resolveSessionKey(server, mac) {
  return `${server.trim()}::${normalizeMac(mac)}`;
}

/**
 * Normalizes portal server URL into candidate load.php API endpoints.
 */
function getEndpointCandidates(serverUrl) {
  let clean = serverUrl.trim().replace(/\/+$/, '');
  if (clean.endsWith('load.php')) return [clean];

  const baseWithoutC = clean.replace(/\/c$/, '');
  return [
    `${clean}/load.php`,
    `${baseWithoutC}/c/load.php`,
    `${baseWithoutC}/stalker_portal/server/load.php`,
    `${clean}/server/load.php`,
  ];
}

/**
 * Discovers and establishes a valid Stalker portal session with handshake and profile verification.
 */
export async function connectStalkerPortalSession(conn, signal) {
  const currentGen = activeGeneration;
  const server = conn.server?.trim();
  const mac = normalizeMac(conn.mac);

  if (!server || !mac) {
    throw new Error('Необходимо указать адрес портала Stalker и MAC-адрес устройства');
  }

  const sessionKey = resolveSessionKey(server, mac);
  const existing = sessions.get(sessionKey);
  if (existing && Date.now() - existing.lastActiveMs < 15 * 60 * 1000) {
    existing.lastActiveMs = Date.now();
    return existing;
  }

  const candidates = getEndpointCandidates(server);
  let session = null;
  let lastError = null;

  for (const endpoint of candidates) {
    if (signal?.aborted || currentGen !== activeGeneration) {
      throw new DOMException('Подключение к порталу отменено', 'AbortError');
    }

    try {
      const hsRes = await proxyStalkerPortal(endpoint, { type: 'stb', action: 'handshake' }, mac, null, signal);
      const hsData = hsRes?.data || hsRes;
      const portalToken = hsData?.js?.token || hsData?.token;

      if (!portalToken) {
        lastError = new Error('Портал не вернул токен сессии при рукопожатии (handshake)');
        continue;
      }

      session = {
        endpoint,
        token: portalToken,
        mac,
        server,
        generation: currentGen,
        lastActiveMs: Date.now(),
      };
      break;
    } catch (err) {
      lastError = err;
    }
  }

  if (!session) {
    throw lastError || new Error('Не удалось связаться со Stalker порталом по указанному адресу');
  }

  // Profile classification
  const profParams = {
    type: 'stb',
    action: 'get_profile',
    auth_second_step: '0',
  };
  if (conn.serial) profParams.sn = conn.serial;
  if (conn.deviceId) profParams.device_id = conn.deviceId;
  if (conn.deviceId2) profParams.device_id2 = conn.deviceId2;

  const profRes = await proxyStalkerPortal(session.endpoint, profParams, mac, session.token, signal);
  const profData = profRes?.data || profRes;
  const js = profData?.js;
  if (!js || typeof js !== 'object' || Array.isArray(js) || Object.keys(js).length === 0) {
    throw new Error('Портал не вернул профиль устройства');
  }

  const rawStatus = typeof js.status === 'number' ? js.status : parseInt(js.status, 10);

  if (rawStatus === 1 || js.error) {
    throw new Error(js.msg || js.error || 'Доступ к порталу заблокирован или подписка истекла');
  }

  if (rawStatus === 2) {
    // Portal requires login / password credentials
    if (!conn.user || !conn.pass) {
      throw new Error('Данный портал требует авторизации по логину и паролю. Укажите их в дополнительных настройках подключения.');
    }
    const authRes = await proxyStalkerPortal(
      session.endpoint,
      { type: 'stb', action: 'do_auth', login: conn.user, password: conn.pass },
      mac,
      session.token,
      signal
    );
    const authData = authRes?.data || authRes;
    if (authData?.js === true || authData?.js?.status === 'OK' || authData?.js?.status === 0) {
      // Re-check profile step 2
      profParams.auth_second_step = '1';
      const step2 = await proxyStalkerPortal(session.endpoint, profParams, mac, session.token, signal);
      const confirmed = (step2?.data || step2)?.js;
      if (
        !confirmed ||
        typeof confirmed !== 'object' ||
        Array.isArray(confirmed) ||
        Object.keys(confirmed).length === 0 ||
        [1, 2].includes(Number(confirmed.status))
      ) {
        throw new Error('Портал не подтвердил доступ после выполнения авторизации');
      }
      session.profile = confirmed;
    } else {
      throw new Error('Неверный логин или пароль для Stalker портала');
    }
  } else if (rawStatus === 0) {
    session.profile = js;
  } else if (Number.isNaN(rawStatus)) {
    // Status not present as integer: check if valid profile object (id/name/login) and no error
    if ((js.id || js.login || js.name || js.sname) && !js.error) {
      session.profile = js;
    } else {
      throw new Error('Портал вернул неподтвержденный или ошибочный профиль');
    }
  } else {
    throw new Error(`Неизвестный статус авторизации Stalker портала (${rawStatus})`);
  }

  sessions.set(sessionKey, session);
  return session;
}

/**
 * Loads genres and channels with bounded pagination budget (max 50 pages) from Stalker portal.
 * Explicitly marks if catalog is partial or total is unknown.
 */
export async function loadStalkerCatalog(conn, signal, onProgress) {
  const session = await connectStalkerPortalSession(conn, signal);
  const currentGen = session.generation;

  // 1. Load genres
  let genreMap = new Map();
  try {
    const genresRes = await proxyStalkerPortal(
      session.endpoint,
      { type: 'itv', action: 'get_genres' },
      session.mac,
      session.token,
      signal
    );
    const genresData = (genresRes?.data || genresRes)?.js;
    if (Array.isArray(genresData)) {
      for (const g of genresData) {
        if (g?.id && g?.title) genreMap.set(String(g.id), String(g.title));
      }
    }
  } catch {}

  // 2. Load channels with server-driven bounded pagination (max 50 pages)
  const allChannels = [];
  let page = 1;
  const maxPages = 50;
  let totalItems = Infinity;

  while (allChannels.length < totalItems && page <= maxPages) {
    if (signal?.aborted || currentGen !== activeGeneration) {
      throw new DOMException('Загрузка каналов отменена', 'AbortError');
    }

    const pageRes = await proxyStalkerPortal(
      session.endpoint,
      {
        type: 'itv',
        action: 'get_ordered_list',
        genre: '*',
        p: String(page),
      },
      session.mac,
      session.token,
      signal
    );

    const pageData = (pageRes?.data || pageRes)?.js;
    if (!pageData) break;

    if (typeof pageData.total_items === 'number') {
      totalItems = pageData.total_items;
    }

    const rawItems = Array.isArray(pageData.data)
      ? pageData.data
      : Array.isArray(pageData)
      ? pageData
      : [];

    if (rawItems.length === 0) break;

    for (const item of rawItems) {
      const cmd = item.cmd ? String(item.cmd).trim() : '';
      const name = item.name ? String(item.name).trim() : 'Channel';
      const cleanCmd = cmd.replace(/^(ffmpeg|auto|ffrt)\s+/i, '').trim();
      const isDirectHttp = cleanCmd.startsWith('http://') || cleanCmd.startsWith('https://');

      const genreTitle = genreMap.get(String(item.tv_genre_id)) || 'General';

      allChannels.push({
        id: String(item.id || allChannels.length + 1),
        name,
        group: genreTitle,
        logo: item.logo ? String(item.logo) : null,
        num: typeof item.number === 'number' ? item.number : null,
        epgId: item.tvg_id ? String(item.tvg_id) : null,
        _stalkerCmd: cmd,
        url: isDirectHttp ? cleanCmd : null,
        type: 'live',
      });
    }

    if (onProgress) {
      onProgress(allChannels.length, Number.isFinite(totalItems) ? totalItems : allChannels.length);
    }

    page++;
  }

  // Attach metadata regarding pagination completeness
  const isPartial = page > maxPages && allChannels.length < totalItems;
  allChannels.isPartial = isPartial;
  allChannels.totalReported = Number.isFinite(totalItems) ? totalItems : null;

  return allChannels;
}

/**
 * Loads categories for Stalker VOD or Series.
 */
export async function loadStalkerSectionCategories(conn, sec, signal) {
  const session = await connectStalkerPortalSession(conn, signal);
  const action = 'get_categories';

  const res = await proxyStalkerPortal(
    session.endpoint,
    { type: 'vod', action },
    session.mac,
    session.token,
    signal
  );

  const data = (res?.data || res)?.js;
  if (!Array.isArray(data)) return [];

  return data.map((c) => ({
    id: String(c.id),
    title: String(c.title || c.name || 'Category'),
  }));
}

/**
 * Loads items for a specific Stalker category.
 */
export async function loadStalkerSectionItems(conn, sec, catId, catTitle, signal) {
  const session = await connectStalkerPortalSession(conn, signal);

  const res = await proxyStalkerPortal(
    session.endpoint,
    {
      type: 'vod',
      action: 'get_ordered_list',
      category: String(catId),
      p: '1',
    },
    session.mac,
    session.token,
    signal
  );

  const data = (res?.data || res)?.js;
  const rawItems = Array.isArray(data?.data) ? data.data : Array.isArray(data) ? data : [];

  return rawItems.map((item) => {
    const cmd = item.cmd ? String(item.cmd).trim() : '';
    const cleanCmd = cmd.replace(/^(ffmpeg|auto|ffrt)\s+/i, '').trim();
    const isDirect = cleanCmd.startsWith('http://') || cleanCmd.startsWith('https://');

    return {
      id: String(item.id || uid()),
      name: String(item.name || item.title || 'Video'),
      logo: item.screenshot_uri || item.logo ? String(item.screenshot_uri || item.logo) : null,
      group: catTitle,
      year: item.year ? String(item.year) : undefined,
      rating: item.rating_imdb || item.rating_kinopoisk ? String(item.rating_imdb || item.rating_kinopoisk) : undefined,
      _stalkerCmd: cmd,
      url: isDirect ? cleanCmd : null,
      type: sec === 'series' ? 'series' : 'vod',
    };
  });
}

function uid() {
  return Math.random().toString(36).slice(2, 10);
}

/**
 * Resolves temporary playable stream link for channel or VOD through create_link.
 * Strips command prefixes, rejects portal UI URLs, and returns resolved stream URL
 * so that Player's mediaAdapter pipeline generates and owns the session handle lifecycle.
 */
export async function resolveStalkerStreamLink(conn, rawCmd, sourceId = 'stalker', signal) {
  if (!rawCmd) throw new Error('Команда потока Stalker не указана');
  const session = await connectStalkerPortalSession(conn, signal);

  // If already a direct HTTP/HTTPS stream
  let cleanCmd = rawCmd.replace(/^(ffmpeg|auto|ffrt)\s+/i, '').trim();
  if (cleanCmd.startsWith('http://') || cleanCmd.startsWith('https://')) {
    // Check it's not the portal /c/ page
    if (cleanCmd.includes('/c/') && cleanCmd.endsWith('/c/')) {
      throw new Error('Портал вернул адрес интерфейса вместо медиа-потока');
    }
    return {
      playbackUrl: cleanCmd,
      streamUrl: cleanCmd,
      proxied: false,
      handle: null,
    };
  }

  // Request create_link
  const res = await proxyStalkerPortal(
    session.endpoint,
    {
      type: 'itv',
      action: 'create_link',
      cmd: rawCmd,
      forced_storage: '0',
      disable_ad: '0',
    },
    session.mac,
    session.token,
    signal
  );

  const data = (res?.data || res)?.js;
  const rawUrl = typeof data === 'string' ? data : data?.cmd ? String(data.cmd) : null;

  if (!rawUrl) {
    throw new Error('Портал не вернул ссылку на поток (create_link returned empty stream)');
  }

  const streamUrl = rawUrl.replace(/^(ffmpeg|auto|ffrt)\s+/i, '').trim();

  // Guard against portal UI / localhost
  if (
    streamUrl.includes('localhost') ||
    streamUrl.includes('127.0.0.1') ||
    (streamUrl.includes('/c/') && streamUrl.endsWith('/c/'))
  ) {
    throw new Error('Портал вернул недопустимый или локальный адрес потока');
  }

  return {
    playbackUrl: streamUrl,
    streamUrl,
    proxied: false,
    handle: null,
  };
}
