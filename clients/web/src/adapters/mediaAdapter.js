/**
 * YAiPlayer Web — Media & Network Adapter (ADAPT-09.2 / 09.3).
 * Handles browser media capabilities, opaque media handle routing through
 * approved YAiPlayer Cloudflare Worker, protocol/container probing,
 * universal abort signal composition, and generation-safe media owner lifecycle management.
 */

import { safeFetch, API_BASE_URL, isApiConfigured } from './apiConfig.js';
import { authAdapter } from './authAdapter.js';

let currentMediaOwner = null;
let currentMediaEpoch = 0;
const activeHandles = new Map(); // handleId -> { token, sourceId, streamUrl, expiresAtMs, ownerId }

/**
 * Universal abort signal compositor.
 * Does NOT rely on AbortSignal.any or AbortSignal.timeout, ensuring compatibility
 * across older browsers and WebViews while guaranteeing complete listener and timer cleanup.
 */
export function composeAbortSignals(signals, timeoutMs) {
  const controller = new AbortController();
  let timer = null;
  const cleanups = [];

  const onAbort = () => {
    controller.abort();
    cleanup();
  };

  function cleanup() {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
    for (const c of cleanups) {
      c();
    }
    cleanups.length = 0;
  }

  for (const sig of signals) {
    if (sig) {
      if (sig.aborted) {
        controller.abort();
        cleanup();
        return { signal: controller.signal, cleanup };
      }
      sig.addEventListener('abort', onAbort, { once: true });
      cleanups.push(() => sig.removeEventListener('abort', onAbort));
    }
  }

  if (typeof timeoutMs === 'number' && timeoutMs > 0) {
    timer = setTimeout(() => {
      controller.abort();
      cleanup();
    }, timeoutMs);
  }

  return { signal: controller.signal, cleanup };
}

/**
 * Checks detailed browser media capabilities for HTML5 Video, MSE, HLS, and MPEG-TS.
 */
export function checkBrowserMediaCapabilities() {
  const video = document.createElement('video');
  const hasMSE = typeof window.MediaSource !== 'undefined';
  const nativeHls = video.canPlayType('application/vnd.apple.mpegurl') !== '';
  const nativeMp4 = video.canPlayType('video/mp4') !== '';
  const nativeTs = video.canPlayType('video/mp2t') !== '';

  const isIos =
    typeof navigator !== 'undefined' &&
    (/iPad|iPhone|iPod/.test(navigator.userAgent) ||
      (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1));

  let canMpegts = false;
  if (hasMSE) {
    if (typeof window.MediaSource.isTypeSupported === 'function') {
      canMpegts =
        window.MediaSource.isTypeSupported('video/mp4; codecs="avc1.42E01E"');
    } else {
      canMpegts = true;
    }
  }

  return {
    hasMSE,
    nativeHls,
    nativeMp4,
    nativeTs,
    canHlsJs: hasMSE || nativeHls,
    canMpegts,
    isIos,
    isHttpsPage: typeof window !== 'undefined' && window.location.protocol === 'https:',
  };
}

export function isMixedContent(url) {
  if (!url || typeof window === 'undefined') return false;
  return window.location.protocol === 'https:' && url.startsWith('http://');
}

/**
 * Explicitly revokes an active opaque media handle through the Worker API.
 * Captures and uses the originating user session token.
 */
export async function revokeMediaHandle(handle) {
  if (!handle || typeof handle !== 'string') return false;
  const meta = activeHandles.get(handle);
  activeHandles.delete(handle);
  const token = meta?.token;
  if (!token || !isApiConfigured()) return false;
  try {
    const res = await safeFetch('proxy/media-handle/revoke', {
      method: 'POST',
      body: { handle },
      token,
      timeoutMs: 5000,
    });
    return res?.success === true;
  } catch {
    return false;
  }
}

/**
 * Revokes all currently tracked active handles (called during logout or account switch).
 */
export async function revokeAllActiveHandles(overrideToken) {
  const entries = Array.from(activeHandles.entries());
  activeHandles.clear();
  if (!isApiConfigured()) return;
  await Promise.all(entries.map(([handle, meta]) => {
    const token = meta.token || overrideToken;
    return token ? safeFetch('proxy/media-handle/revoke', { method: 'POST', body: {handle}, token, timeoutMs: 5000 }).catch(() => {}) : Promise.resolve();
  }));
}

/**
 * Resolves safe stream URL for playback.
 * If mixed content, CORS, or explicit forceProxy requires proxying, generates an opaque encrypted media handle
 * through the approved YAiPlayer Worker API.
 * Never connects to author Koyeb or local proxy.
 * Eliminates provider tokens from browser URL/query strings.
 */
export async function resolveStreamPlaybackUrl(streamUrl, sourceId = 'web-source', signal, options = {}) {
  if (!streamUrl) throw new Error('URL потока не указан');

  // Prevent circular proxying or re-injecting internal proxy stream endpoints
  try {
    const parsed = new URL(streamUrl, typeof window !== 'undefined' ? window.location.href : 'http://localhost');
    if (parsed.pathname.includes('/api/v1/proxy/stream') || parsed.pathname.includes('/proxy/stream')) {
      throw new Error('Недопустимый адрес потока: попытка повторного воспроизведения прокси-адреса');
    }
  } catch (urlErr) {
    if (urlErr.message?.includes('повторного')) throw urlErr;
  }

  const mixed = isMixedContent(streamUrl);
  const forceProxy = options.forceProxy === true;

  // If stream is direct HTTPS and does not require proxying, play directly
  if (!forceProxy && !mixed && streamUrl.startsWith('https://')) {
    return {
      playbackUrl: streamUrl,
      proxied: false,
      handle: null,
      expiresAtMs: null,
      streamUrl,
      sourceId,
    };
  }

  // Mixed content or HTTP/CORS stream requires configured YAiPlayer Worker proxy
  if (!isApiConfigured()) {
    throw new Error(
      'Для воспроизведения HTTP/CORS потока в браузере требуется настроенный YAiPlayer Worker API.'
    );
  }

  const token = authAdapter.getToken();
  if (!token) {
    throw new Error(
      'Для проксирования медиа-потока через защищенный Worker требуется авторизация в аккаунте YAiPlayer.'
    );
  }

  const epoch = authAdapter.getEpoch();
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(String(sourceId)));
  const safeSourceId = 'web_' + Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');

  const validityMs = options.validityMs || 15 * 60 * 1000;
  const res = await safeFetch('proxy/media-handle/create', {
    method: 'POST',
    body: {
      sourceId: safeSourceId,
      streamUrl,
      validityMs,
    },
    token,
    signal,
    timeoutMs: 15000,
  });

  const revoke = () => {
    if (res?.handle) void safeFetch('proxy/media-handle/revoke', {method: 'POST', body: {handle: res.handle}, token, timeoutMs: 5000}).catch(() => {});
  };

  if (signal?.aborted || epoch !== authAdapter.getEpoch() || token !== authAdapter.getToken()) {
    revoke();
    throw new Error('Сессия изменилась или запрос отменен');
  }

  if (
    !res ||
    typeof res.handle !== 'string' ||
    !res.handle.startsWith('mh_') ||
    res.ownerId !== authAdapter.getUser()?.id ||
    res.sourceId !== safeSourceId ||
    !Number.isSafeInteger(res.expiresAtMs)
  ) {
    revoke();
    throw new Error('Некорректный медиа-доступ от сервера');
  }

  const target = new URL(res.playbackUrl, API_BASE_URL);
  if (
    target.origin !== new URL(API_BASE_URL).origin ||
    target.pathname !== '/api/v1/proxy/stream' ||
    target.searchParams.get('h') !== res.handle
  ) {
    revoke();
    throw new Error('Некорректный адрес плеера');
  }

  // Register in active handles map for lifecycle tracking
  activeHandles.set(res.handle, {
    token,
    sourceId: safeSourceId,
    streamUrl,
    expiresAtMs: res.expiresAtMs,
    ownerId: res.ownerId,
  });

  signal?.addEventListener('abort', revoke, { once: true });

  return {
    playbackUrl: target.href,
    proxied: true,
    handle: res.handle,
    expiresAtMs: res.expiresAtMs,
    streamUrl,
    sourceId: safeSourceId,
  };
}

/**
 * Renews an active stream handle before its 15-minute TTL expires,
 * enabling uninterrupted long-running playback sessions without stale authorization leaks.
 */
export async function renewMediaHandle(oldHandle, streamUrl, sourceId, signal) {
  if (!streamUrl) throw new Error('Параметры продления доступа не указаны');
  const token = authAdapter.getToken();
  if (!token) throw new Error('Авторизация отсутствует');

  const fresh = await resolveStreamPlaybackUrl(streamUrl, sourceId, signal, { forceProxy: true });

  if (oldHandle && oldHandle !== fresh.handle) {
    void revokeMediaHandle(oldHandle).catch(() => {});
  }

  return fresh;
}

/**
 * Safe fetch for external playlist (M3U), EPG (XMLTV), or Xtream Codes metadata.
 * Tries direct fetch on HTTPS first; routes through authenticated Worker /api/v1/proxy/fetch
 * with 16 MiB bound when CORS or mixed content blocks direct browser access.
 * Timeout encompasses both headers and body read.
 * Authoritative 4xx errors (401/403/404) are NOT retried via proxy.
 */
export async function safeProxyFetch(url, options = {}) {
  if (!url) throw new Error('URL не указан');
  const { signal, method = 'GET', headers = {} } = options;

  // 1. If direct HTTPS and not mixed content, try direct fetch first
  if (!isMixedContent(url) && url.startsWith('https://')) {
    const { signal: fetchSignal, cleanup } = composeAbortSignals([signal], 15000);
    try {
      const direct = await fetch(url, {
        method,
        headers,
        signal: fetchSignal,
        credentials: 'omit',
        referrerPolicy: 'no-referrer',
      });

      // 4xx errors (e.g. 401 Unauthorized, 403 Forbidden, 404 Not Found)
      // are authoritative responses from upstream — DO NOT retry or fall back to proxy!
      if (direct.status >= 400 && direct.status < 500) {
        await direct.body?.cancel().catch(() => {});
        throw new Error(`HTTP ${direct.status}`);
      }

      if (direct.ok) {
        const reader = direct.body?.getReader();
        if (!reader) throw new Error('Пустой ответ');
        const chunks = [];
        let bytes = 0;
        try {
          while (true) {
            const part = await reader.read();
            if (part.done) break;
            bytes += part.value.byteLength;
            if (bytes > 16 * 1024 * 1024) throw new Error('Ответ превышает допустимый лимит (16 МиБ)');
            chunks.push(part.value);
          }
        } finally {
          await reader.cancel().catch(() => {});
          reader.releaseLock();
        }
        return new Response(new Blob(chunks), {
          status: direct.status,
          headers: direct.headers,
        });
      }
      await direct.body?.cancel().catch(() => {});
    } catch (e) {
      if (signal?.aborted) throw e;
      // If error was 4xx, rethrow immediately without proxy fallback
      if (e.message?.startsWith('HTTP 4')) throw e;
      // Network/CORS failure falls through to proxy
    } finally {
      cleanup();
    }
  }

  // 2. Fall back to authenticated Worker proxy fetch
  if (!isApiConfigured()) {
    throw new Error('Для загрузки внешнего HTTP/CORS ресурса требуется настроенный YAiPlayer Worker API.');
  }

  const token = authAdapter.getToken();
  if (!token) {
    throw new Error('Для безопасного проксирования внешних данных требуется вход в аккаунт YAiPlayer.');
  }

  const { signal: proxySignal, cleanup: proxyCleanup } = composeAbortSignals([signal], 15000);
  try {
    const res = await fetch(`${API_BASE_URL}/api/v1/proxy/fetch`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ url, method, headers }),
      signal: proxySignal,
      credentials: 'omit',
      referrerPolicy: 'no-referrer',
    });

    if (!res.ok) {
      await res.body?.cancel().catch(() => {});
      throw new Error(`Прокси-сервер вернул ошибку (${res.status})`);
    }

    const reader = res.body?.getReader();
    if (!reader) throw new Error('Пустой ответ прокси');
    const chunks = [];
    let bytes = 0;
    try {
      while (true) {
        const part = await reader.read();
        if (part.done) break;
        bytes += part.value.byteLength;
        if (bytes > 16 * 1024 * 1024) throw new Error('Ответ превышает лимит (16 МиБ)');
        chunks.push(part.value);
      }
    } finally {
      await reader.cancel().catch(() => {});
      reader.releaseLock();
    }

    return new Response(new Blob(chunks), {
      status: res.status,
      headers: res.headers,
    });
  } finally {
    proxyCleanup();
  }
}

/**
 * Proxy Stalker Portal requests through YAiPlayer Worker /api/v1/proxy/stalker.
 */
export async function proxyStalkerPortal(endpoint, params, mac, portalToken, signal) {
  if (!isApiConfigured()) {
    throw new Error('Сервер YAiPlayer не настроен для работы со Stalker Portal.');
  }

  const yaiToken = authAdapter.getToken();
  if (!yaiToken) {
    throw new Error('Для работы с порталом Stalker требуется вход в учетную запись YAiPlayer.');
  }

  const res = await safeFetch('proxy/stalker', {
    method: 'POST',
    body: {
      endpoint,
      params,
      mac,
      token: portalToken || undefined,
    },
    token: yaiToken,
    signal,
    timeoutMs: 15000,
  });

  return res;
}

/**
 * Probes the stream format and sync bytes without guessing purely by file extension.
 * Inspects Content-Type and MPEG-TS sync bytes (0x47 at 188-byte intervals).
 * Employs universal composeAbortSignals without relying on AbortSignal.any.
 */
export async function probeStreamFormat(url, headers = {}, signal) {
  if (!url) return 'unknown';

  const cleanPath = url.split(/[?#]/)[0].toLowerCase();
  if (cleanPath.endsWith('.m3u8')) return 'hls';
  if (['.mp4', '.mkv', '.webm', '.avi', '.mov', '.mp3', '.aac'].some((ext) => cleanPath.endsWith(ext))) {
    return 'direct';
  }
  if (cleanPath.endsWith('.ts')) return 'mpeg_ts';

  // Bound headers AND body read during an ambiguous-format probe
  const { signal: probeSignal, cleanup } = composeAbortSignals([signal], 4000);
  try {
    const res = await fetch(url, {
      method: 'GET',
      headers: { ...headers, Range: 'bytes=0-563' },
      signal: probeSignal,
      credentials: 'omit',
    });

    const contentType = res.headers.get('content-type') || '';
    if (
      contentType.includes('application/vnd.apple.mpegurl') ||
      contentType.includes('application/x-mpegurl')
    ) {
      await res.body?.cancel().catch(() => {});
      return 'hls';
    }
    if (contentType.includes('video/mp2t')) {
      await res.body?.cancel().catch(() => {});
      return 'mpeg_ts';
    }
    if (contentType.includes('video/mp4')) {
      await res.body?.cancel().catch(() => {});
      return 'direct';
    }

    const reader = res.body?.getReader();
    if (!reader) return 'unknown';
    const buffer = new Uint8Array(564);
    let bytesRead = 0;
    try {
      while (bytesRead < 564) {
        const { done, value } = await reader.read();
        if (done || !value) break;
        const take = Math.min(value.length, 564 - bytesRead);
        buffer.set(value.subarray(0, take), bytesRead);
        bytesRead += take;
      }
    } finally {
      await reader.cancel().catch(() => {});
      reader.releaseLock();
    }

    // Verify MPEG-TS sync byte 0x47 at 188-byte intervals
    if (bytesRead >= 188 && buffer[0] === 0x47) {
      if (bytesRead >= 376 && buffer[188] !== 0x47) return 'unknown';
      if (bytesRead >= 564 && buffer[376] !== 0x47) return 'unknown';
      return 'mpeg_ts';
    }

    // Check for #EXTM3U text in header
    const textSample = new TextDecoder().decode(buffer.subarray(0, Math.min(bytesRead, 128)));
    if (textSample.includes('#EXTM3U') || textSample.includes('#EXT-X-')) {
      return 'hls';
    }
  } catch {
    if (signal?.aborted) throw new DOMException('Playback cancelled', 'AbortError');
  } finally {
    cleanup();
  }

  return 'unknown';
}

/**
 * Media playback lifetime manager.
 * Enforces single active playback owner and provides monotonic epoch validation.
 * Unmounting, channel switching, or logout increments epoch and releases previous engine.
 */
export function registerMediaOwner(instance) {
  const epoch = ++currentMediaEpoch;
  if (currentMediaOwner && currentMediaOwner !== instance) {
    try {
      currentMediaOwner.destroy();
    } catch {}
  }
  currentMediaOwner = instance;

  return {
    epoch,
    isCurrent: () => currentMediaEpoch === epoch && currentMediaOwner === instance,
    release: () => {
      if (currentMediaOwner === instance) {
        currentMediaOwner = null;
      }
    },
  };
}

export function stopAllMediaPlayback() {
  ++currentMediaEpoch;
  if (currentMediaOwner) {
    try {
      currentMediaOwner.destroy();
    } catch {}
    currentMediaOwner = null;
  }
  void revokeAllActiveHandles().catch(() => {});
}

// Automatically stop playback and revoke handles when user logs out or session expires
authAdapter.subscribe((user) => {
  if (!user) {
    stopAllMediaPlayback();
  }
});

