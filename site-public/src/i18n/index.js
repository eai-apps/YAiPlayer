import ru from './ru.json';
import en from './en.json';
import de from './de.json';

const translations = { ru, en, de };
const supportedLocales = ['ru', 'en', 'de'];

function detectInitialLocale() {
  try {
    const saved = localStorage.getItem('yai_public_lang');
    if (saved && supportedLocales.includes(saved)) {
      return saved;
    }
  } catch {
    // localStorage may be unavailable
  }

  const browserLang = (navigator.language || navigator.userLanguage || 'ru').toLowerCase();
  if (browserLang.startsWith('ru') || browserLang.startsWith('be') || browserLang.startsWith('kk') || browserLang.startsWith('uk')) {
    return 'ru';
  }
  if (browserLang.startsWith('de')) {
    return 'de';
  }
  return 'en';
}

let currentLocale = detectInitialLocale();
const listeners = new Set();

export function getLocale() {
  return currentLocale;
}

export function setLocale(newLocale) {
  if (!supportedLocales.includes(newLocale)) return;
  if (currentLocale === newLocale) return;
  currentLocale = newLocale;

  try {
    localStorage.setItem('yai_public_lang', newLocale);
  } catch {
    // localStorage might fail in private browsing
  }

  document.documentElement.lang = newLocale;
  listeners.forEach(fn => fn(newLocale));
}

export function onLocaleChange(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

export function t(path, fallback = '') {
  const parts = path.split('.');
  let obj = translations[currentLocale] || translations.ru;

  for (const part of parts) {
    if (obj && Object.prototype.hasOwnProperty.call(obj, part)) {
      obj = obj[part];
    } else {
      // fallback to English or Russian
      let alt = (translations.en && getDeep(translations.en, parts)) ||
                (translations.ru && getDeep(translations.ru, parts));
      return alt || fallback || path;
    }
  }

  return obj || fallback || path;
}

function getDeep(obj, parts) {
  let curr = obj;
  for (const p of parts) {
    if (curr && Object.prototype.hasOwnProperty.call(curr, p)) {
      curr = curr[p];
    } else {
      return null;
    }
  }
  return curr;
}
