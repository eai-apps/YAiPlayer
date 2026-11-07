import { t } from '../i18n/index.js';
import { brand } from '../brand.js';
import docsData from '../data/docs.generated.json';

// Legal pages with unfilled {{...}} blanks stay out of the footer until the owner data is entered.
const pending = new Set((docsData && docsData.legalPending) || []);
const legalLink = (key, href, label) => (pending.has(key) ? '' : `<li><a href="${href}">${label}</a></li>`);

export function renderFooter() {
  const currentYear = new Date().getFullYear();

  return `
    <footer class="site-footer">
      <div class="container">
        <div class="footer-grid">
          <div class="footer-brand">
            <a href="#/" class="brand-link">
              <img src="${brand.logo.path}" alt="${brand.name}" class="brand-logo-img" />
              <span>${brand.name}</span>
            </a>
            <p>${t('footer.desc')}</p>
            <div class="badge badge-open-source">GPL-3.0 Open Source</div>
          </div>

          <div class="footer-col">
            <h4>${t('footer.navTitle')}</h4>
            <ul>
              <li><a href="#/">${t('nav.home')}</a></li>
              <li><a href="#/download">${t('nav.download')}</a></li>
              <li><a href="#/guide">${t('nav.guide')}</a></li>
              <li><a href="#/faq">${t('nav.faq')}</a></li>
            </ul>
          </div>

          <div class="footer-col">
            <h4>${t('footer.legalTitle')}</h4>
            <ul>
              ${legalLink('privacy', '#/privacy', t('nav.privacy'))}
              ${legalLink('terms', '#/terms', t('nav.terms'))}
              ${legalLink('impressum', '#/impressum', t('nav.impressum'))}
              ${legalLink('opensource', '#/opensource', t('nav.opensource'))}
            </ul>
          </div>

          <div class="footer-col">
            <h4>${t('footer.communityTitle')}</h4>
            <ul>
              <li>
                <a href="${brand.links.telegramChannel}" target="_blank" rel="noopener noreferrer">
                  Telegram: ${t('common.channel')} @YAiPlayer
                </a>
              </li>
              <li>
                <a href="${brand.links.telegramBot}" target="_blank" rel="noopener noreferrer">
                  Telegram: ${t('common.bot')} @YAiPlayer_App_bot
                </a>
              </li>
              <li>
                <a href="${brand.links.repo}" target="_blank" rel="noopener noreferrer">
                  GitHub: eai-apps/YAiPlayer
                </a>
              </li>
            </ul>
          </div>
        </div>

        <div class="footer-bottom">
          <div>${t('footer.notice')}</div>
          <div>© ${currentYear} ${brand.name}. ${t('footer.copyright')}</div>
        </div>
      </div>
    </footer>
  `;
}
