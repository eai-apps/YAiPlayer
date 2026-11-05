import { t } from '../i18n/index.js';

export function renderNotFound() {
  return `
    <div class="docs-page">
      <div class="container" style="text-align: center; padding: 80px 20px;">
        <h1 class="docs-h1" style="font-size: 4rem; color: #ef4444; margin-bottom: 16px;">404</h1>
        <p class="docs-p" style="font-size: 1.25rem; margin-bottom: 32px;">Page not found</p>
        <a href="#/" class="btn btn-primary btn-lg">${t('common.backHome')}</a>
      </div>
    </div>
  `;
}
