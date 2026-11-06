/**
 * Escape HTML to prevent XSS vulnerabilities
 */
export function escapeHtml(str) {
  if (str === null || str === undefined) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

/**
 * Format ISO date string according to locale
 */
export function formatDate(isoStr, lang = 'ru') {
  if (!isoStr) return '';
  try {
    const d = new Date(isoStr);
    if (isNaN(d.getTime())) return isoStr;
    const localeMap = { ru: 'ru-RU', de: 'de-DE', en: 'en-US' };
    return d.toLocaleDateString(localeMap[lang] || 'en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  } catch {
    return isoStr;
  }
}

/**
 * Format file size in megabytes
 */
export function formatBytes(bytes) {
  if (!bytes || isNaN(bytes)) return '';
  const mb = bytes / (1024 * 1024);
  return `${mb.toFixed(1)} MB`;
}
