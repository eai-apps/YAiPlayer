import { t, getLocale } from '../i18n/index.js';
import { brand, getBrandSlogan } from '../brand.js';

export function renderHome() {
  const currentLang = getLocale();
  const slogan = getBrandSlogan(currentLang);

  return `
    <div class="home-page">
      <!-- Hero Section -->
      <section class="hero-section">
        <div class="hero-glow"></div>
        <div class="container hero-content">
          <div class="hero-badge-wrap">
            <span class="badge badge-version">v0.12.0 • Android TV & Mobile</span>
          </div>

          <h1 class="hero-title">${t('home.heroTitle')}</h1>
          <p class="hero-subtitle">${slogan}</p>

          <!-- Player Honesty Notice -->
          <div class="player-notice-card">
            <div class="player-notice-icon">ℹ️</div>
            <div class="player-notice-content">
              <h4>${t('home.playerNoticeTitle')}</h4>
              <p>${t('home.playerNoticeText')}</p>
            </div>
          </div>

          <div class="hero-cta-group">
            <a href="#/download" class="btn btn-primary btn-lg">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="2" y="7" width="20" height="15" rx="2" ry="2"></rect>
                <polyline points="17 2 12 7 7 2"></polyline>
              </svg>
              <span>${t('home.ctaTv')}</span>
            </a>

            <a href="#/download" class="btn btn-accent btn-lg">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="5" y="2" width="14" height="20" rx="2" ry="2"></rect>
                <line x1="12" y1="18" x2="12.01" y2="18"></line>
              </svg>
              <span>${t('home.ctaMobile')}</span>
            </a>

            <a href="#/guide" class="btn btn-outline btn-lg">
              <span>📖 ${t('home.ctaGuide')}</span>
            </a>
          </div>
        </div>
      </section>

      <!-- Features Section -->
      <section class="features-section">
        <div class="container">
          <div class="section-header">
            <h2 class="section-title">${t('home.featuresTitle')}</h2>
            <p class="section-subtitle">${t('home.heroSubtitle')}</p>
          </div>

          <div class="features-grid">
            <div class="feature-card">
              <div class="feature-icon-wrapper">📡</div>
              <h3 class="feature-title">${t('home.f1Title')}</h3>
              <p class="feature-desc">${t('home.f1Desc')}</p>
            </div>

            <div class="feature-card">
              <div class="feature-icon-wrapper">📅</div>
              <h3 class="feature-title">${t('home.f2Title')}</h3>
              <p class="feature-desc">${t('home.f2Desc')}</p>
            </div>

            <div class="feature-card">
              <div class="feature-icon-wrapper">🎮</div>
              <h3 class="feature-title">${t('home.f3Title')}</h3>
              <p class="feature-desc">${t('home.f3Desc')}</p>
            </div>

            <div class="feature-card">
              <div class="feature-icon-wrapper">🛡️</div>
              <h3 class="feature-title">${t('home.f4Title')}</h3>
              <p class="feature-desc">${t('home.f4Desc')}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- Coming Soon Section -->
      <section class="soon-section">
        <div class="container">
          <div class="section-header">
            <h2 class="section-title">${t('home.soonTitle')}</h2>
            <p class="section-subtitle">${t('home.soonSubtitle')}</p>
          </div>

          <div class="soon-grid">
            <div class="soon-card">
              <div class="soon-header">
                <span class="badge badge-soon">${t('common.soon')}</span>
                <span style="font-size: 1.5rem">☁️</span>
              </div>
              <h3>${t('home.soon1Title')}</h3>
              <p>${t('home.soon1Desc')}</p>
            </div>

            <div class="soon-card">
              <div class="soon-header">
                <span class="badge badge-soon">${t('common.soon')}</span>
                <span style="font-size: 1.5rem">📲</span>
              </div>
              <h3>${t('home.soon2Title')}</h3>
              <p>${t('home.soon2Desc')}</p>
            </div>
          </div>

          <div class="soon-disclaimer">
            ⚠️ ${t('home.soonNotice')}
          </div>
        </div>
      </section>

      <!-- Telegram Community Section -->
      <section class="community-section">
        <div class="container">
          <div class="community-box">
            <h2>${t('home.communityTitle')}</h2>
            <p>${t('home.communityText')}</p>

            <div class="community-buttons">
              <a href="${brand.links.telegramChannel}" target="_blank" rel="noopener noreferrer" class="btn btn-primary btn-lg">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.64 6.8c-.15 1.58-.8 5.42-1.13 7.19-.14.75-.42 1-.68 1.03-.58.05-1.02-.38-1.58-.75-.88-.58-1.38-.94-2.23-1.5-.99-.65-.35-1.01.22-1.59.15-.15 2.71-2.48 2.76-2.69a.2.2 0 00-.05-.18c-.06-.05-.14-.03-.21-.02-.09.02-1.49.95-4.22 2.79-.4.27-.76.41-1.08.4-.36-.01-1.04-.2-1.55-.37-.63-.2-1.12-.31-1.08-.66.02-.18.27-.36.74-.55 2.92-1.27 4.86-2.11 5.83-2.51 2.78-1.16 3.35-1.36 3.73-1.36.08 0 .27.02.39.12.1.08.13.19.14.27-.01.06.01.24 0 .37z"/>
                </svg>
                <span>${t('home.btnTelegramChannel')}</span>
              </a>

              <a href="${brand.links.telegramBot}" target="_blank" rel="noopener noreferrer" class="btn btn-outline btn-lg">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.64 6.8c-.15 1.58-.8 5.42-1.13 7.19-.14.75-.42 1-.68 1.03-.58.05-1.02-.38-1.58-.75-.88-.58-1.38-.94-2.23-1.5-.99-.65-.35-1.01.22-1.59.15-.15 2.71-2.48 2.76-2.69a.2.2 0 00-.05-.18c-.06-.05-.14-.03-.21-.02-.09.02-1.49.95-4.22 2.79-.4.27-.76.41-1.08.4-.36-.01-1.04-.2-1.55-.37-.63-.2-1.12-.31-1.08-.66.02-.18.27-.36.74-.55 2.92-1.27 4.86-2.11 5.83-2.51 2.78-1.16 3.35-1.36 3.73-1.36.08 0 .27.02.39.12.1.08.13.19.14.27-.01.06.01.24 0 .37z"/>
                </svg>
                <span>${t('home.btnTelegramBot')}</span>
              </a>
            </div>
          </div>
        </div>
      </section>
    </div>
  `;
}
