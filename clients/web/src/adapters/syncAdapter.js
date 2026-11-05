// YAiPlayer contributors, 2026-11-04: canonical bounded metadata only.
import { safeFetch, API_BASE_URL } from './apiConfig.js';
import { authAdapter } from './authAdapter.js';
export function getUserProfileScope() { return JSON.stringify([API_BASE_URL, authAdapter.getUser()?.id || 'guest']); }
export async function pullCloudRegistry(signal) {
  const token = authAdapter.getToken(), owner = authAdapter.getUser(), epoch = authAdapter.getEpoch();
  if (!token || !owner) throw new Error('Требуется вход');
  let res;
  try { res = await safeFetch('sync/pull?sinceRevision=0', { token, signal, maxBytes: 262144 }); }
  catch (error) { if (error.status === 401) authAdapter.invalidateIfCurrent(epoch, token); throw error; }
  if (signal?.aborted || epoch !== authAdapter.getEpoch() || token !== authAdapter.getToken()) throw new Error('Сессия изменилась');
  const p = res?.payload;
  if (!p || p.userId !== owner.id || !Number.isSafeInteger(res.currentRevision) || res.currentRevision < 0 ||
      p.revision !== res.currentRevision || !Array.isArray(p.sources) || p.sources.length > 200 ||
      new Set(p.sources.map(s => s?.id)).size !== p.sources.length || !Array.isArray(p.tombstones) || p.tombstones.length > 500) throw new Error('Некорректный реестр');
  const sources = p.sources.map(s => {
    if (!s || typeof s.id !== 'string' || !s.id || s.id.length > 256 || typeof s.name !== 'string' || s.name.length > 128 ||
        !['m3u', 'stalker'].includes(s.type)) throw new Error('Некорректный источник');
    return { id: s.id, name: s.name, type: s.type, enabled: s.isEnabled !== false, needsLocalSetup: true };
  });
  return { revision: res.currentRevision, sources, tombstonesCount: p.tombstones.length };
}
