/**
 * YAiPlayer Web — Cooperative M3U Parser (ADAPT-09.2).
 * Non-blocking chunked parser with binary charset & BOM detection (UTF-8, UTF-16, Windows-1251).
 * Cooperative event loop yielding prevents main-thread freezes on large playlists (up to 20,000 channels).
 * Supports cancellation via AbortSignal.
 */

export function decodeM3UBuffer(bytes) {
  if (typeof bytes === 'string') return bytes;
  const u8 = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);

  // 1. BOM Detection
  if (u8.length >= 3 && u8[0] === 0xef && u8[1] === 0xbb && u8[2] === 0xbf) {
    return new TextDecoder('utf-8').decode(u8.subarray(3));
  }
  if (u8.length >= 2 && u8[0] === 0xfe && u8[1] === 0xff) {
    return new TextDecoder('utf-16be').decode(u8.subarray(2));
  }
  if (u8.length >= 2 && u8[0] === 0xff && u8[1] === 0xfe) {
    return new TextDecoder('utf-16le').decode(u8.subarray(2));
  }

  // 2. Strict UTF-8 with fallback to Windows-1251 (standard IPTV playlist charset)
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(u8);
  } catch {
    try {
      return new TextDecoder('windows-1251').decode(u8);
    } catch {
      return new TextDecoder('utf-8').decode(u8);
    }
  }
}

/**
 * Parses M3U playlist asynchronously in cooperative chunks.
 * Yields execution to the browser event loop every `chunkSize` lines to prevent UI stutter.
 */
export async function parseM3UAsync(input, options = {}) {
  const { signal, onProgress, chunkSize = 500, maxChannels = 20000, maxBytes = 16 * 1024 * 1024 } = options;

  let text;
  if (typeof input === 'string') {
    text = input;
  } else {
    const byteLen = input.byteLength || input.length || 0;
    if (byteLen > maxBytes) {
      throw new Error(`Плейлист превышает допустимый размер (${Math.round(maxBytes / 1024 / 1024)} МиБ)`);
    }
    text = decodeM3UBuffer(input);
  }

  if (text.length > maxBytes) {
    throw new Error('Текст плейлиста превышает допустимый лимит безопасности');
  }

  const lines = text.split(/\r?\n/);
  const totalLines = lines.length;
  const out = [];
  let cur = null;

  for (let i = 0; i < totalLines; i++) {
    if (signal?.aborted) {
      throw new DOMException('Парсинг плейлиста отменен', 'AbortError');
    }

    // Cooperative yielding to event loop
    if (i > 0 && i % chunkSize === 0) {
      if (onProgress) {
        onProgress(out.length, Math.round((i / totalLines) * 100));
      }
      await new Promise((resolve) => setTimeout(resolve, 0));
    }

    const line = lines[i].trim();
    if (line.startsWith('#EXTINF')) {
      const name = (line.match(/,(.+)$/) || [])[1]?.trim() || 'Unknown';
      const logo = (line.match(/tvg-logo="([^"]+)"/) || [])[1] || null;
      const group = (line.match(/group-title="([^"]+)"/) || [])[1] || 'Uncategorized';
      const epgId = (line.match(/tvg-id="([^"]+)"/) || [])[1] || null;
      const numStr = (line.match(/tvg-chno="([^"]+)"/) || [])[1];
      const num = numStr ? parseInt(numStr, 10) : null;
      cur = { name, logo, group, epgId, num, type: 'live' };
    } else if (line && !line.startsWith('#') && cur) {
      cur.url = line;
      cur.id = cur.url;
      out.push(cur);
      cur = null;

      if (out.length > maxChannels) {
        throw new Error(`В плейлисте обнаружено слишком много каналов (максимум ${maxChannels}); разделите плейлист`);
      }
    }
  }

  if (onProgress) {
    onProgress(out.length, 100);
  }

  return out;
}
