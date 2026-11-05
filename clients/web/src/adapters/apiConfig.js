/**
 * YAiPlayer Web — API Transport & Configuration
 * Provides safe bounded HTTP transport to the YAiPlayer Cloudflare Worker.
 * Enforces single API origin, timeouts, byte limits, and sanitized errors.
 */

const RAW_API_URL = import.meta.env?.VITE_API_URL || '';

function resolveBaseUrl(raw) {
  if (!raw) return '';
  try {
    const parsed = new URL(raw);
    // Allow https or localhost dev http
    if (!parsed.username && !parsed.password && !parsed.search && !parsed.hash && parsed.pathname === '/' &&
        (parsed.protocol === 'https:' || (parsed.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(parsed.hostname)))) {
      return parsed.origin;
    }
  } catch {}
  return '';
}

export const API_BASE_URL = resolveBaseUrl(RAW_API_URL);

export function isApiConfigured() {
  return Boolean(API_BASE_URL);
}

export function getApiOrigin() {
  return API_BASE_URL;
}

/**
 * Safe fetch with AbortSignal, timeout, and byte limit bounds.
 * Prevents memory exhaustion and unhandled fetch rejections.
 */
export async function safeFetch(path, options = {}) {
  const {
    method = 'GET',
    body,
    token,
    signal,
    timeoutMs = 15000,
    maxBytes = 65536,
    headers = {},
  } = options;

  if (!API_BASE_URL) {
    throw new Error('Сервер API YAiPlayer не настроен.');
  }

  if (typeof path !== 'string' || !/^[a-z0-9/?=&_-]+$/i.test(path) || path.includes('..') || path.startsWith('/')) {
    throw new Error('Некорректный путь API');
  }
  const encodedBody = body !== undefined && typeof body === 'object' ? JSON.stringify(body) : body;
  if (encodedBody != null && new TextEncoder().encode(encodedBody).byteLength > 65536) throw new Error('Запрос превышает 64 КиБ');
  const controller = new AbortController();
  const onParentAbort = () => controller.abort();
  if (signal) {
    if (signal.aborted) controller.abort();
    else signal.addEventListener('abort', onParentAbort, { once: true });
  }

  const timer = setTimeout(() => controller.abort(), timeoutMs);

  const reqHeaders = { ...headers };
  if (body !== undefined && typeof body === 'object') {
    reqHeaders['Content-Type'] = 'application/json';
  }
  if (token) {
    reqHeaders['Authorization'] = `Bearer ${token}`;
  }

  const url = `${API_BASE_URL}/api/v1/${path.replace(/^\/+/, '')}`;

  try {
    const res = await fetch(url, {
      method,
      headers: reqHeaders,
      body: encodedBody,
      redirect: 'error',
      credentials: 'omit',
      cache: 'no-store',
      referrerPolicy: 'no-referrer',
      signal: controller.signal,
    });

    if (!res.ok) {
      void res.body?.cancel().catch(() => {});
      const status = res.status;
      const failure = new Error(status === 401 ? 'Сессия недействительна' : status === 403 ? 'Доступ запрещён' : `Ошибка API (${status})`);
      failure.status = status;
      throw failure;

    }

    const reader = res.body?.getReader();
    if (!reader) throw new Error('Пустой ответ сервера');

    let totalBytes = 0;
    const chunks = [];
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        totalBytes += value.byteLength;
        if (totalBytes > maxBytes) {
          await reader.cancel();
          throw new Error('Ответ сервера превысил допустимый лимит безопасности');
        }
        chunks.push(value);
      }
    } finally {
      reader.releaseLock();
    }

    const merged = new Uint8Array(totalBytes);
    let offset = 0;
    for (const chunk of chunks) {
      merged.set(chunk, offset);
      offset += chunk.byteLength;
    }

    const text = new TextDecoder('utf-8', { fatal: true }).decode(merged);
    try {
      return JSON.parse(text);
    } catch {
      throw new Error('Некорректный JSON ответ API');
    }
  } catch (err) {
    if (controller.signal.aborted) {
      throw new Error(signal?.aborted ? 'Запрос отменен пользователем.' : 'Превышено время ожидания ответа сервера.');
    }
    if (err instanceof TypeError) {
      throw new Error('Не удалось связаться с сервером YAiPlayer.');
    }
    throw err;
  } finally {
    clearTimeout(timer);
    if (signal) signal.removeEventListener('abort', onParentAbort);
  }
}
