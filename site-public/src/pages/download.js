import { t, getLocale } from '../i18n/index.js';
import { brand } from '../brand.js';
import { generateQrSvg } from '../qr.js';
import newsData from '../data/news.generated.json';

export function renderDownload() {
  const currentLang = getLocale();

  // Find latest tag name from newsData if available
  const latestRelease = Array.isArray(newsData) && newsData.length > 0 ? newsData[0] : null;
  const versionTag = latestRelease ? latestRelease.version : 'v3.0.28';

  const fallbackTvUrl = `${brand.links.releases}/download/${brand.apk.tv}`;
  const fallbackMobileUrl = `${brand.links.releases}/download/${brand.apk.mobile}`;

  // Badges for app stores
  const playBadge = brand.playUrl
    ? `<a href="${brand.playUrl}" target="_blank" rel="noopener noreferrer" class="store-badge store-badge-active">
         <span class="store-icon">▶️</span>
         <span>Google Play</span>
       </a>`
    : `<div class="store-badge store-badge-disabled">
         <span class="store-icon">⏳</span>
         <span>${t('download.playStoreSoon')}</span>
       </div>`;

  const rustoreBadge = brand.rustoreUrl
    ? `<a href="${brand.rustoreUrl}" target="_blank" rel="noopener noreferrer" class="store-badge store-badge-active">
         <span class="store-icon">🛍️</span>
         <span>RuStore</span>
       </a>`
    : `<div class="store-badge store-badge-disabled">
         <span class="store-icon">⏳</span>
         <span>${t('download.ruStoreSoon')}</span>
       </div>`;

  // Cabinet path logic
  const cabinetCard = brand.cabinetUrl
    ? `<div class="cabinet-card cabinet-card-ready">
         <div class="cabinet-icon">🔐</div>
         <h3>${t('download.path2Title')}</h3>
         <p>${t('download.cabinetSoonDesc')}</p>
         <div class="download-btn-wrap">
           <a href="${brand.cabinetUrl}" class="btn btn-primary btn-lg" target="_blank" rel="noopener noreferrer">
             <span>${t('download.cabinetBtn')}</span>
           </a>
         </div>
       </div>`
    : `<div class="cabinet-card cabinet-card-soon">
         <div class="cabinet-badge-wrap">
           <span class="badge badge-accent">${t('common.soon')}</span>
         </div>
         <div class="cabinet-icon">☁️</div>
         <h3>${t('download.cabinetSoonTitle')}</h3>
         <p class="cabinet-desc">${t('download.cabinetSoonDesc')}</p>
       </div>`;

  return `
    <div class="download-page">
      <div class="container download-container">
        <div class="download-header">
          <div class="download-badge-wrap">
            <span class="badge badge-version">${versionTag} • Android TV & Mobile</span>
          </div>
          <h1 class="download-title">${t('download.title')}</h1>
          <p class="download-subtitle">${t('download.subtitle')}</p>
        </div>

        <!-- TWO PATHS CONTAINER -->
        <div class="download-paths-grid">
          <!-- PATH 1: Instant download (No registration) -->
          <div class="download-path-card path-primary">
            <div class="path-badge">${t('download.path1Title')}</div>
            <p class="path-desc">${t('download.path1Subtitle')}</p>

            <!-- Telegram Bot Main CTA -->
            <div class="bot-cta-card">
              <div class="bot-cta-info">
                <div class="bot-icon">🤖</div>
                <div class="bot-text">
                  <h4>Telegram-бот выдачи APK</h4>
                  <p>Проверяет подписку на @YAiPlayer и выдаёт официальные сборки</p>
                </div>
              </div>
              <a href="${brand.links.telegramBot}" target="_blank" rel="noopener noreferrer" class="btn btn-accent btn-lg btn-bot">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.64 6.8c-.15 1.58-.8 5.42-1.13 7.19-.14.75-.42 1-.68 1.03-.58.05-1.02-.38-1.58-.75-.88-.58-1.38-.94-2.23-1.5-.99-.65-.35-1.01.22-1.59.15-.15 2.71-2.48 2.76-2.69a.2.2 0 00-.05-.18c-.06-.05-.14-.03-.21-.02-.09.02-1.49.95-4.22 2.79-.4.27-.76.41-1.08.4-.36-.01-1.04-.2-1.55-.37-.63-.2-1.12-.31-1.08-.66.02-.18.27-.36.74-.55 2.92-1.27 4.86-2.11 5.83-2.51 2.78-1.16 3.35-1.36 3.73-1.36.08 0 .27.02.39.12.1.08.13.19.14.27-.01.06.01.24 0 .37z"/>
                </svg>
                <span>${t('download.botBtn')}</span>
              </a>
            </div>

            <!-- QR code card for Android TV -->
            <div class="qr-card">
              <div class="qr-visual-wrap" id="qrContainer" aria-label="QR Code for Bot">
                <div class="qr-loading">...</div>
              </div>
              <div class="qr-meta">
                <h4>${t('download.qrTitle')}</h4>
                <p>${t('download.qrDesc')}</p>
              </div>
            </div>

            <!-- Direct links fallback -->
            <div class="direct-links-card">
              <h4>${t('download.directLinksTitle')}</h4>
              <div class="direct-btn-row">
                <a href="${fallbackTvUrl}" class="btn btn-outline btn-sm" download>
                  <span>📺 ${t('download.tvBtn')}</span>
                </a>
                <a href="${fallbackMobileUrl}" class="btn btn-outline btn-sm" download>
                  <span>📱 ${t('download.mobileBtn')}</span>
                </a>
              </div>
            </div>

            <!-- App Store Badges -->
            <div class="store-badges-section">
              <h4>${t('download.storesTitle')}</h4>
              <div class="store-badges-grid">
                ${playBadge}
                ${rustoreBadge}
              </div>
            </div>

            <!-- Community Links -->
            <div class="community-row">
              <a href="${brand.links.telegramChannel}" target="_blank" rel="noopener noreferrer" class="link-pill">
                📢 @YAiPlayer
              </a>
              <a href="${brand.links.telegramChat}" target="_blank" rel="noopener noreferrer" class="link-pill">
                💬 @YAiPlayer_Chat
              </a>
              <a href="${brand.links.repo}" target="_blank" rel="noopener noreferrer" class="link-pill">
                ⭐ GitHub
              </a>
            </div>
          </div>

          <!-- PATH 2: With Account (Cabinet) -->
          <div class="download-path-card path-secondary">
            ${cabinetCard}
          </div>
        </div>

        <!-- 3-Step Unknown Sources Guide -->
        <div class="guide-steps-card">
          <div class="guide-card-header">
            <span class="guide-icon">🛡️</span>
            <h3>${t('download.installGuideTitle')}</h3>
          </div>
          <div class="steps-list">
            <div class="step-item">
              <div class="step-num">1</div>
              <div class="step-text">${t('download.step1')}</div>
            </div>
            <div class="step-item">
              <div class="step-num">2</div>
              <div class="step-text">${t('download.step2')}</div>
            </div>
            <div class="step-item">
              <div class="step-num">3</div>
              <div class="step-text">${t('download.step3')}</div>
            </div>
          </div>
        </div>

        <!-- In-app Update Card -->
        <div class="inapp-update-card">
          <div class="inapp-icon">🔄</div>
          <div class="inapp-text">
            <h4>${t('download.inAppUpdateTitle')}</h4>
            <p>${t('download.inAppUpdateText')}</p>
          </div>
        </div>
      </div>
    </div>
  `;
}

export function setupDownloadPage() {
  const qrContainer = document.getElementById('qrContainer');
  if (qrContainer) {
    generateQrSvg(brand.links.telegramBot, { width: 170, darkColor: '#0b0f19', lightColor: '#ffffff' })
      .then(svg => {
        qrContainer.innerHTML = svg;
      })
      .catch(e => {
        console.warn('QR render error:', e);
      });
  }
}
