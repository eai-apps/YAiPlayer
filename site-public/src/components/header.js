import { t, getLocale, setLocale } from '../i18n/index.js';
import { brand } from '../brand.js';

export function renderHeader(currentRoute = '') {
  const currentLang = getLocale();
  const activePath = currentRoute || '#/';

  const navItems = [
    { href: '#/', key: 'nav.home' },
    { href: '#/download', key: 'nav.download' },
    { href: '#/checklist', key: 'nav.checklist' },
    { href: '#/news', key: 'nav.news' },
    { href: '#/guide', key: 'nav.guide' },
    { href: '#/faq', key: 'nav.faq' }
  ];

  const navHtml = navItems.map(item => {
    const isActive = activePath === item.href || (item.href === '#/' && (activePath === '' || activePath === '#'));
    return `<a href="${item.href}" class="nav-link ${isActive ? 'active' : ''}">${t(item.key)}</a>`;
  }).join('');

  return `
    <header class="site-header">
      <div class="container">
        <a href="#/" class="brand-link">
          <img src="${brand.logo.path}" alt="${brand.name}" class="brand-logo-img" />
          <span>${brand.name}</span>
        </a>

        <button class="mobile-nav-toggle" id="mobileNavToggle" aria-label="Toggle navigation">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="3" y1="12" x2="21" y2="12"></line>
            <line x1="3" y1="6" x2="21" y2="6"></line>
            <line x1="3" y1="18" x2="21" y2="18"></line>
          </svg>
        </button>

        <nav class="site-nav" id="siteNav">
          ${navHtml}
        </nav>

        <div class="header-actions">
          <div class="lang-switch">
            <button type="button" class="lang-btn ${currentLang === 'ru' ? 'active' : ''}" data-lang="ru">RU</button>
            <button type="button" class="lang-btn ${currentLang === 'en' ? 'active' : ''}" data-lang="en">EN</button>
            <button type="button" class="lang-btn ${currentLang === 'de' ? 'active' : ''}" data-lang="de">DE</button>
          </div>

          <a href="${brand.links.telegramChannel}" target="_blank" rel="noopener noreferrer" class="btn-telegram-icon" title="Telegram Channel">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.64 6.8c-.15 1.58-.8 5.42-1.13 7.19-.14.75-.42 1-.68 1.03-.58.05-1.02-.38-1.58-.75-.88-.58-1.38-.94-2.23-1.5-.99-.65-.35-1.01.22-1.59.15-.15 2.71-2.48 2.76-2.69a.2.2 0 00-.05-.18c-.06-.05-.14-.03-.21-.02-.09.02-1.49.95-4.22 2.79-.4.27-.76.41-1.08.4-.36-.01-1.04-.2-1.55-.37-.63-.2-1.12-.31-1.08-.66.02-.18.27-.36.74-.55 2.92-1.27 4.86-2.11 5.83-2.51 2.78-1.16 3.35-1.36 3.73-1.36.08 0 .27.02.39.12.1.08.13.19.14.27-.01.06.01.24 0 .37z"/>
            </svg>
            <span>Telegram</span>
          </a>
        </div>
      </div>
    </header>
  `;
}

export function setupHeaderEvents() {
  const toggleBtn = document.getElementById('mobileNavToggle');
  const siteNav = document.getElementById('siteNav');

  if (toggleBtn && siteNav) {
    toggleBtn.addEventListener('click', () => {
      siteNav.classList.toggle('open');
    });

    siteNav.querySelectorAll('.nav-link').forEach(link => {
      link.addEventListener('click', () => {
        siteNav.classList.remove('open');
      });
    });
  }

  document.querySelectorAll('.lang-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const targetLang = e.currentTarget.getAttribute('data-lang');
      if (targetLang) {
        setLocale(targetLang);
      }
    });
  });
}
