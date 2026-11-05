import { t, getLocale } from '../i18n/index.js';
import { brand } from '../brand.js';
import { escapeHtml, formatDate } from '../util.js';

export function renderDownload() {
  const currentLang = getLocale();

  // Fallback download URLs
  const fallbackTvUrl = `${brand.links.releases}/download/${brand.apk.tv}`;
  const fallbackMobileUrl = `${brand.links.releases}/download/${brand.apk.mobile}`;

  return `
    <div class="download-page">
      <div class="container">
        <div class="download-header">
          <h1 class="docs-h1">${t('download.title')}</h1>
          <p class="docs-p">${t('download.subtitle')}</p>

          <div class="release-meta-badge" id="releaseMetaBadge">
            <span class="release-version" id="releaseVersionTag">${t('common.version')} v0.12.0</span>
            <span style="opacity: 0.4">•</span>
            <span class="release-date" id="releaseDateTag">${t('common.loading')}</span>
          </div>
        </div>

        <div class="download-cards-grid">
          <!-- Android TV Card -->
          <div class="download-card download-card-featured">
            <div class="download-device-icon">📺</div>
            <h3>${t('download.tvTitle')}</h3>
            <p class="download-card-desc">${t('download.tvDesc')}</p>
            <div class="download-btn-wrap">
              <a href="${fallbackTvUrl}" id="btnDownloadTv" class="btn btn-primary btn-lg" download>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                  <polyline points="7 10 12 15 17 10"></polyline>
                  <line x1="12" y1="15" x2="12" y2="3"></line>
                </svg>
                <span>${t('download.tvBtn')}</span>
              </a>
            </div>
            <div class="download-fallback-link">
              <a href="${brand.links.releases}" target="_blank" rel="noopener noreferrer">${t('download.manualLinks')}</a>
            </div>
          </div>

          <!-- Android Mobile Card -->
          <div class="download-card">
            <div class="download-device-icon">📱</div>
            <h3>${t('download.mobileTitle')}</h3>
            <p class="download-card-desc">${t('download.mobileDesc')}</p>
            <div class="download-btn-wrap">
              <a href="${fallbackMobileUrl}" id="btnDownloadMobile" class="btn btn-accent btn-lg" download>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                  <polyline points="7 10 12 15 17 10"></polyline>
                  <line x1="12" y1="15" x2="12" y2="3"></line>
                </svg>
                <span>${t('download.mobileBtn')}</span>
              </a>
            </div>
            <div class="download-fallback-link">
              <a href="${brand.links.releases}" target="_blank" rel="noopener noreferrer">${t('download.manualLinks')}</a>
            </div>
          </div>
        </div>

        <!-- 3-Step Unknown Sources Guide -->
        <div class="guide-steps-card">
          <h3>
            <span>🛡️</span>
            <span>${t('download.installGuideTitle')}</span>
          </h3>

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
          <div>
            <h4>${t('download.inAppUpdateTitle')}</h4>
            <p>${t('download.inAppUpdateText')}</p>
          </div>
        </div>
      </div>
    </div>
  `;
}

export function setupDownloadPage() {
  const currentLang = getLocale();
  const versionEl = document.getElementById('releaseVersionTag');
  const dateEl = document.getElementById('releaseDateTag');
  const btnTv = document.getElementById('btnDownloadTv');
  const btnMobile = document.getElementById('btnDownloadMobile');

  // Fetch latest release info from GitHub API
  fetch(brand.links.releasesApi)
    .then(res => {
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return res.json();
    })
    .then(data => {
      if (data && data.tag_name) {
        if (versionEl) {
          versionEl.textContent = `${t('common.version')} ${escapeHtml(data.tag_name)}`;
        }
        if (dateEl && data.published_at) {
          dateEl.textContent = `${t('download.publishedAt')}: ${escapeHtml(formatDate(data.published_at, currentLang))}`;
        }

        // Match assets
        if (Array.isArray(data.assets)) {
          const tvAsset = data.assets.find(a => 
            a.name && (a.name.toLowerCase() === 'yaiplayer-tv.apk' || a.name.toLowerCase().includes('tv.apk'))
          );
          if (tvAsset && tvAsset.browser_download_url && btnTv) {
            btnTv.href = tvAsset.browser_download_url;
          }

          const mobileAsset = data.assets.find(a => 
            a.name && (a.name.toLowerCase() === 'yaiplayer-mobile.apk' || a.name.toLowerCase().includes('mobile.apk'))
          );
          if (mobileAsset && mobileAsset.browser_download_url && btnMobile) {
            btnMobile.href = mobileAsset.browser_download_url;
          }
        }
      }
    })
    .catch(() => {
      // Graceful fallback to static data
      if (versionEl) {
        versionEl.textContent = `${t('common.version')} v0.12.0 (latest)`;
      }
      if (dateEl) {
        dateEl.textContent = `${brand.name} GitHub Releases`;
      }
    });
}
