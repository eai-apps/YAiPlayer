import { getLocale, t } from '../i18n/index.js';
import docsData from '../data/docs.generated.json';

export function renderLegalPage(docKey) {
  const currentLang = getLocale();
  const categoryDocs = docsData && docsData.legal && docsData.legal[docKey];
  const doc = (categoryDocs && categoryDocs[currentLang]) ||
              (categoryDocs && categoryDocs.ru) ||
              { html: '<p>Document not available.</p>', toc: [] };

  const navKeyMap = {
    privacy: 'nav.privacy',
    terms: 'nav.terms',
    impressum: 'nav.impressum',
    opensource: 'nav.opensource'
  };

  const titleKey = navKeyMap[docKey] || 'nav.home';

  const tocHtml = doc.toc && doc.toc.length > 0
    ? `
      <aside class="docs-sidebar">
        <h4>${t(titleKey)}</h4>
        <ul class="toc-list">
          ${doc.toc.map(item => `
            <li>
              <a href="#${item.id}" class="toc-link ${item.level === 3 ? 'toc-h3' : ''}" data-target="${item.id}">
                ${item.title}
              </a>
            </li>
          `).join('')}
        </ul>
      </aside>
    `
    : '';

  return `
    <div class="docs-page">
      <div class="container">
        <div class="docs-layout ${!tocHtml ? 'full-width' : ''}">
          ${tocHtml}
          <main class="docs-content">
            ${doc.html}
          </main>
        </div>
      </div>
    </div>
  `;
}

export function setupLegalPage() {
  document.querySelectorAll('.docs-sidebar .toc-link').forEach(link => {
    link.addEventListener('click', (e) => {
      const targetId = link.getAttribute('data-target');
      if (targetId) {
        const el = document.getElementById(targetId);
        if (el) {
          e.preventDefault();
          el.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
      }
    });
  });
}
