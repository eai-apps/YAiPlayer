import { renderHeader, setupHeaderEvents } from './components/header.js';
import { renderFooter } from './components/footer.js';
import { renderHome, setupHomePage } from './pages/home.js';
import { renderDownload, setupDownloadPage } from './pages/download.js';
import { renderChecklist, setupChecklistPage } from './pages/checklist.js';
import { renderNews, setupNewsPage } from './pages/news.js';
import { renderGuide, setupGuidePage } from './pages/guide.js';
import { renderFaq, setupFaqPage } from './pages/faq.js';
import { renderLegalPage, setupLegalPage } from './pages/legalPage.js';
import { renderNotFound } from './pages/notFound.js';
import { t, onLocaleChange } from './i18n/index.js';
import { brand } from './brand.js';

function getHashRoute() {
  const hash = window.location.hash || '#/';
  // split query or secondary anchor
  const base = hash.split('?')[0];
  return base;
}

export function route() {
  const currentHash = getHashRoute();
  const appEl = document.getElementById('app');
  if (!appEl) return;

  let contentHtml = '';
  let setupFn = null;
  let pageTitle = brand.name;

  switch (currentHash) {
    case '':
    case '#':
    case '#/':
    case '#/home':
      contentHtml = renderHome();
      setupFn = setupHomePage;
      pageTitle = `${brand.name} • ${t('home.heroTitle')}`;
      break;
    case '#/download':
      contentHtml = renderDownload();
      setupFn = setupDownloadPage;
      pageTitle = `${t('nav.download')} • ${brand.name}`;
      break;
    case '#/checklist':
      contentHtml = renderChecklist();
      setupFn = setupChecklistPage;
      pageTitle = `${t('nav.checklist')} • ${brand.name}`;
      break;
    case '#/news':
      contentHtml = renderNews();
      setupFn = setupNewsPage;
      pageTitle = `${t('nav.news')} • ${brand.name}`;
      break;
    case '#/guide':
      contentHtml = renderGuide();
      setupFn = setupGuidePage;
      pageTitle = `${t('nav.guide')} • ${brand.name}`;
      break;
    case '#/faq':
      contentHtml = renderFaq();
      setupFn = setupFaqPage;
      pageTitle = `${t('nav.faq')} • ${brand.name}`;
      break;
    case '#/privacy':
      contentHtml = renderLegalPage('privacy');
      setupFn = setupLegalPage;
      pageTitle = `${t('nav.privacy')} • ${brand.name}`;
      break;
    case '#/terms':
      contentHtml = renderLegalPage('terms');
      setupFn = setupLegalPage;
      pageTitle = `${t('nav.terms')} • ${brand.name}`;
      break;
    case '#/impressum':
      contentHtml = renderLegalPage('impressum');
      setupFn = setupLegalPage;
      pageTitle = `${t('nav.impressum')} • ${brand.name}`;
      break;
    case '#/opensource':
      contentHtml = renderLegalPage('opensource');
      setupFn = setupLegalPage;
      pageTitle = `${t('nav.opensource')} • ${brand.name}`;
      break;
    default:
      contentHtml = renderNotFound();
      pageTitle = `404 • ${brand.name}`;
      break;
  }

  document.title = pageTitle;

  appEl.innerHTML = `
    ${renderHeader(currentHash)}
    <main class="main-content">
      ${contentHtml}
    </main>
    ${renderFooter()}
  `;

  // Attach event handlers
  setupHeaderEvents();
  if (setupFn) {
    setupFn();
  }

  // Scroll to top on route change unless hash is anchor
  if (!window.location.hash.includes('-') && !window.location.hash.includes('features')) {
    window.scrollTo({ top: 0, behavior: 'instant' });
  }
}

export function initRouter() {
  window.addEventListener('hashchange', route);
  onLocaleChange(() => {
    route();
  });
  route();
}
