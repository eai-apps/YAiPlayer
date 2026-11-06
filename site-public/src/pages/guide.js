import { getLocale, t } from '../i18n/index.js';
import docsData from '../data/docs.generated.json';

export function renderGuide() {
  const currentLang = getLocale();
  const guideDoc = (docsData && docsData.userGuide && docsData.userGuide[currentLang]) ||
                   (docsData && docsData.userGuide && docsData.userGuide.ru) ||
                   { html: '<p>Guide not available.</p>', toc: [] };

  const tocHtml = guideDoc.toc && guideDoc.toc.length > 0
    ? `
      <aside class="docs-sidebar">
        <h4>${t('nav.guide')}</h4>
        <ul class="toc-list">
          ${guideDoc.toc.map(item => `
            <li>
              <a href="#guide-${item.id}" class="toc-link ${item.level === 3 ? 'toc-h3' : ''}" data-target="${item.id}">
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
            ${guideDoc.html}
          </main>
        </div>
      </div>
    </div>
  `;
}

export function setupGuidePage() {
  // Smooth scroll for TOC links
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
