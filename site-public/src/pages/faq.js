import { t } from '../i18n/index.js';

export function renderFaq() {
  const faqItems = [
    { q: 'faq.q1', a: 'faq.a1' },
    { q: 'faq.q2', a: 'faq.a2' },
    { q: 'faq.q3', a: 'faq.a3' },
    { q: 'faq.q4', a: 'faq.a4' },
    { q: 'faq.q5', a: 'faq.a5' },
    { q: 'faq.q6', a: 'faq.a6' }
  ];

  const itemsHtml = faqItems.map((item, idx) => `
    <div class="faq-item ${idx === 0 ? 'open' : ''}" data-faq-index="${idx}">
      <button class="faq-question" type="button" aria-expanded="${idx === 0 ? 'true' : 'false'}">
        <span>${t(item.q)}</span>
        <span class="faq-chevron">▼</span>
      </button>
      <div class="faq-answer">
        <p>${t(item.a)}</p>
      </div>
    </div>
  `).join('');

  return `
    <div class="faq-section">
      <div class="container">
        <div class="section-header">
          <h1 class="docs-h1">${t('faq.title')}</h1>
          <p class="section-subtitle">${t('faq.subtitle')}</p>
        </div>

        <div class="faq-list">
          ${itemsHtml}
        </div>
      </div>
    </div>
  `;
}

export function setupFaqPage() {
  document.querySelectorAll('.faq-question').forEach(btn => {
    btn.addEventListener('click', () => {
      const parent = btn.closest('.faq-item');
      if (parent) {
        const isOpen = parent.classList.contains('open');
        parent.classList.toggle('open');
        btn.setAttribute('aria-expanded', !isOpen);
      }
    });
  });
}
