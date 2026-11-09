import { t, getLocale } from '../i18n/index.js';
import { brand, getBrandSlogan } from '../brand.js';
import { formatDate } from '../util.js';
import newsData from '../data/news.generated.json';

export function renderHome() {
  const currentLang = getLocale();
  const slogan = getBrandSlogan(currentLang);

  // Latest 3 releases for news block
  const latestReleases = Array.isArray(newsData) ? newsData.slice(0, 3) : [];
  const newsCardsHtml = latestReleases.map(rel => {
    const formattedDate = formatDate(rel.publishedAt, currentLang);
    const topItems = rel.items.slice(0, 2).map(it => {
      let badgeClass = 'badge-improvement';
      if (it.type === 'new') badgeClass = 'badge-new';
      else if (it.type === 'fix') badgeClass = 'badge-fix';

      return `
        <li class="home-news-item">
          <span class="news-item-badge ${badgeClass}">${it.icon} ${it.label}</span>
          <span>${it.text}</span>
        </li>
      `;
    }).join('');

    return `
      <div class="home-news-card">
        <div class="home-news-header">
          <span class="badge badge-accent">${rel.version}</span>
          <time class="home-news-date" datetime="${rel.publishedAt}">${formattedDate}</time>
        </div>
        <ul class="home-news-list">
          ${topItems}
        </ul>
      </div>
    `;
  }).join('');

  return `
    <div class="home-page">
      <!-- 1. HERO SECTION -->
      <section class="hero-section">
        <div class="hero-glow"></div>
        <div class="container hero-container">
          <div class="hero-text-col">
            <div class="hero-badge-wrap">
              <span class="badge badge-version">${t('home.heroBadge')}</span>
            </div>

            <h1 class="hero-title">${t('home.heroTitle')}</h1>
            <p class="hero-subtitle">${t('home.heroSubtitle')}</p>

            <div class="hero-cta-group">
              <a href="${brand.links.telegramBot}" target="_blank" rel="noopener noreferrer" class="btn btn-primary btn-lg btn-hero-glow">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.64 6.8c-.15 1.58-.8 5.42-1.13 7.19-.14.75-.42 1-.68 1.03-.58.05-1.02-.38-1.58-.75-.88-.58-1.38-.94-2.23-1.5-.99-.65-.35-1.01.22-1.59.15-.15 2.71-2.48 2.76-2.69a.2.2 0 00-.05-.18c-.06-.05-.14-.03-.21-.02-.09.02-1.49.95-4.22 2.79-.4.27-.76.41-1.08.4-.36-.01-1.04-.2-1.55-.37-.63-.2-1.12-.31-1.08-.66.02-.18.27-.36.74-.55 2.92-1.27 4.86-2.11 5.83-2.51 2.78-1.16 3.35-1.36 3.73-1.36.08 0 .27.02.39.12.1.08.13.19.14.27-.01.06.01.24 0 .37z"/>
                </svg>
                <span>${t('home.ctaTelegram')}</span>
              </a>

              <a href="#features" class="btn btn-outline btn-lg" id="btnExploreFeatures">
                <span>⚡ ${t('home.ctaFeatures')}</span>
              </a>
            </div>

            <div class="hero-secondary-links">
              <a href="#/download" class="link-download-all">
                <span>📥 ${t('home.ctaDownloadPage')}</span>
              </a>
              <span class="dot-sep">•</span>
              <a href="#/checklist" class="link-checklist">
                <span>📋 ${t('nav.checklist')}</span>
              </a>
            </div>
          </div>

          <!-- Hero Visual: Animated Device Mockups -->
          <div class="hero-visual-col" aria-hidden="true">
            <div class="device-showcase-wrap">
              <!-- TV Mockup Frame -->
              <div class="mockup-tv">
                <div class="tv-bezel">
                  <div class="tv-screen">
                    <!-- Abstract TV Interface -->
                    <div class="mock-tv-ui">
                      <div class="mock-tv-sidebar">
                        <div class="mock-icon-pill active">📺</div>
                        <div class="mock-icon-pill">⭐</div>
                        <div class="mock-icon-pill">🎬</div>
                        <div class="mock-icon-pill">⚙️</div>
                      </div>
                      <div class="mock-tv-content">
                        <div class="mock-channel-hero">
                          <div class="mock-live-badge">LIVE</div>
                          <div class="mock-channel-title">HD 1 • Discovery Nature</div>
                          <div class="mock-prog-title">National Parks & Wildlife 4K</div>
                          <div class="mock-time-bar">
                            <div class="mock-time-fill" style="width: 65%;"></div>
                          </div>
                          <div class="mock-meta-row">
                            <span>14:00 - 15:30</span>
                            <span class="mock-catchup-pill">Архив 7 дн.</span>
                          </div>
                        </div>
                        <div class="mock-tv-grid">
                          <div class="mock-card mock-focus">
                            <div class="mock-thumb">🌿</div>
                            <div class="mock-name">Discovery</div>
                          </div>
                          <div class="mock-card">
                            <div class="mock-thumb">⚽</div>
                            <div class="mock-name">Sport Plus</div>
                          </div>
                          <div class="mock-card">
                            <div class="mock-thumb">🍿</div>
                            <div class="mock-name">Cinema HD</div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <div class="tv-stand"></div>
              </div>

              <!-- Floating Mobile Mockup -->
              <div class="mockup-phone floating-animation">
                <div class="phone-bezel">
                  <div class="phone-notch"></div>
                  <div class="phone-screen">
                    <div class="mock-mobile-header">
                      <span>YAiPlayer</span>
                      <span class="pip-icon">🔲</span>
                    </div>
                    <div class="mock-mobile-player">
                      <div class="mock-player-visual">
                        <div class="mock-play-btn">▶</div>
                      </div>
                    </div>
                    <div class="mock-mobile-list">
                      <div class="mock-row active">
                        <span class="mock-dot"></span>
                        <div class="mock-col">
                          <b>Discovery HD</b>
                          <small>Осталось 28 мин</small>
                        </div>
                        <span class="mock-rec">REC</span>
                      </div>
                      <div class="mock-row">
                        <span class="mock-dot"></span>
                        <div class="mock-col">
                          <b>Cinema Premier</b>
                          <small>14:30 Начало</small>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 2. TRUST STRIP & HONESTY NOTICE -->
      <section class="trust-section">
        <div class="container">
          <div class="trust-pills-row">
            <div class="trust-pill"><span>💚</span> <span>${t('home.trust.free')}</span></div>
            <div class="trust-pill"><span>🚫</span> <span>${t('home.trust.noAds')}</span></div>
            <div class="trust-pill"><span>🛡️</span> <span>${t('home.trust.noTracking')}</span></div>
            <div class="trust-pill"><span>📜</span> <span>${t('home.trust.openSource')}</span></div>
            <div class="trust-pill"><span>🔒</span> <span>${t('home.trust.localData')}</span></div>
          </div>

          <!-- Player Honesty Notice Card -->
          <div class="player-notice-card">
            <div class="player-notice-icon">ℹ️</div>
            <div class="player-notice-content">
              <h4>${t('home.playerNoticeTitle')}</h4>
              <p>${t('home.playerNoticeText')}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- 3. FEATURES GRID (Animated Cards) -->
      <section class="features-section" id="features">
        <div class="container">
          <div class="section-header">
            <h2 class="section-title">${t('home.features.title')}</h2>
            <p class="section-subtitle">${t('home.features.subtitle')}</p>
          </div>

          <div class="features-grid">
            <div class="feature-card animate-on-scroll">
              <div class="feature-icon-wrapper">📡</div>
              <h3 class="feature-title">${t('home.features.f1Title')}</h3>
              <p class="feature-desc">${t('home.features.f1Desc')}</p>
            </div>

            <div class="feature-card animate-on-scroll">
              <div class="feature-icon-wrapper">⏪</div>
              <h3 class="feature-title">${t('home.features.f2Title')}</h3>
              <p class="feature-desc">${t('home.features.f2Desc')}</p>
            </div>

            <div class="feature-card animate-on-scroll">
              <div class="feature-icon-wrapper">🎬</div>
              <h3 class="feature-title">${t('home.features.f3Title')}</h3>
              <p class="feature-desc">${t('home.features.f3Desc')}</p>
            </div>

            <div class="feature-card animate-on-scroll">
              <div class="feature-icon-wrapper">📅</div>
              <h3 class="feature-title">${t('home.features.f4Title')}</h3>
              <p class="feature-desc">${t('home.features.f4Desc')}</p>
            </div>

            <div class="feature-card animate-on-scroll">
              <div class="feature-icon-wrapper">🔌</div>
              <h3 class="feature-title">${t('home.features.f5Title')}</h3>
              <p class="feature-desc">${t('home.features.f5Desc')}</p>
            </div>

            <div class="feature-card animate-on-scroll">
              <div class="feature-icon-wrapper">⚡</div>
              <h3 class="feature-title">${t('home.features.f6Title')}</h3>
              <p class="feature-desc">${t('home.features.f6Desc')}</p>
            </div>

            <div class="feature-card animate-on-scroll">
              <div class="feature-icon-wrapper">🔍</div>
              <h3 class="feature-title">${t('home.features.f7Title')}</h3>
              <p class="feature-desc">${t('home.features.f7Desc')}</p>
            </div>

            <div class="feature-card animate-on-scroll">
              <div class="feature-icon-wrapper">⭐</div>
              <h3 class="feature-title">${t('home.features.f8Title')}</h3>
              <p class="feature-desc">${t('home.features.f8Desc')}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- 4. INTERACTIVE DEVICE SHOWCASE (Tabs) -->
      <section class="interactive-section">
        <div class="container">
          <div class="section-header">
            <h2 class="section-title">${t('home.interactive.title')}</h2>
            <p class="section-subtitle">${t('home.interactive.subtitle')}</p>
          </div>

          <div class="showcase-tabs-header" role="tablist">
            <button type="button" class="tab-btn active" id="tabMobileBtn" role="tab" aria-selected="true" data-tab="mobile">
              ${t('home.interactive.tabMobile')}
            </button>
            <button type="button" class="tab-btn" id="tabTvBtn" role="tab" aria-selected="false" data-tab="tv">
              ${t('home.interactive.tabTv')}
            </button>
          </div>

          <div class="showcase-content-wrap">
            <!-- Mobile Tab Content -->
            <div class="showcase-pane active" id="paneMobile">
              <div class="pane-grid">
                <div class="pane-text">
                  <h3>${t('home.interactive.mobileAdvTitle')}</h3>
                  <ul class="pane-list">
                    <li><span class="check-icon">✓</span> ${t('home.interactive.mobileAdv1')}</li>
                    <li><span class="check-icon">✓</span> ${t('home.interactive.mobileAdv2')}</li>
                    <li><span class="check-icon">✓</span> ${t('home.interactive.mobileAdv3')}</li>
                    <li><span class="check-icon">✓</span> ${t('home.interactive.mobileAdv4')}</li>
                  </ul>
                  <a href="#/download" class="btn btn-primary btn-sm">
                    <span>${t('home.ctaTelegram')}</span>
                  </a>
                </div>
                <div class="pane-visual">
                  <div class="interactive-card-mobile">
                    <div class="mock-card-header">
                      <span class="live-indicator">● LIVE</span>
                      <span class="channel-badge">${t('home.interactive.mockCategory')}</span>
                    </div>
                    <h4 class="card-title">${t('home.interactive.mockChannel')}</h4>
                    <p class="card-prog">${t('home.interactive.mockProg')}</p>
                    <div class="card-meta">
                      <span>${t('home.interactive.mockTime')}</span>
                      <span class="badge badge-accent">${t('home.interactive.mockArchive')}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- TV Tab Content -->
            <div class="showcase-pane" id="paneTv">
              <div class="pane-grid">
                <div class="pane-text">
                  <h3>${t('home.interactive.tvAdvTitle')}</h3>
                  <ul class="pane-list">
                    <li><span class="check-icon">✓</span> ${t('home.interactive.tvAdv1')}</li>
                    <li><span class="check-icon">✓</span> ${t('home.interactive.tvAdv2')}</li>
                    <li><span class="check-icon">✓</span> ${t('home.interactive.tvAdv3')}</li>
                    <li><span class="check-icon">✓</span> ${t('home.interactive.tvAdv4')}</li>
                  </ul>
                  <a href="#/download" class="btn btn-primary btn-sm">
                    <span>${t('home.ctaTelegram')}</span>
                  </a>
                </div>
                <div class="pane-visual">
                  <div class="interactive-card-tv">
                    <div class="tv-top-bar">
                      <span class="rail-shortcut">◀ Меню [D-Pad]</span>
                      <span class="profile-pill">👤 Главный профиль</span>
                    </div>
                    <div class="tv-program-focus">
                      <span class="live-indicator">● В ЭФИРЕ</span>
                      <h3>Discovery Planet HD</h3>
                      <p>Тайны глубокого океана • 4K HDR</p>
                      <div class="tv-actions-row">
                        <span class="btn-mock-focus">[ OK ] Во весь экран</span>
                        <span class="btn-mock">[ ⏪ ] С начала</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Coming Soon Cards inside Showcase -->
          <div class="soon-block">
            <div class="soon-block-header">
              <span class="badge badge-accent">${t('common.soon')}</span>
              <h3>${t('home.interactive.soonTitle')}</h3>
            </div>
            <div class="soon-grid">
              <div class="soon-card">
                <div class="soon-icon">🎮</div>
                <h4>${t('home.interactive.soon1Title')}</h4>
                <p>${t('home.interactive.soon1Desc')}</p>
              </div>
              <div class="soon-card">
                <div class="soon-icon">🖼️</div>
                <h4>${t('home.interactive.soon2Title')}</h4>
                <p>${t('home.interactive.soon2Desc')}</p>
              </div>
              <div class="soon-card">
                <div class="soon-icon">📲</div>
                <h4>${t('home.interactive.soon3Title')}</h4>
                <p>${t('home.interactive.soon3Desc')}</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 5. INTERACTIVE QUIZ LEAD MAGNET -->
      <section class="quiz-section" id="quizSection">
        <div class="container">
          <div class="quiz-card">
            <div class="quiz-header">
              <div class="quiz-badge">
                <span class="badge badge-accent">Интерактивный подбор</span>
              </div>
              <h2 class="quiz-title">${t('home.quiz.title')}</h2>
              <p class="quiz-subtitle">${t('home.quiz.subtitle')}</p>
            </div>

            <div class="quiz-body" id="quizBody">
              <!-- Question 1 -->
              <div class="quiz-step active" id="quizStep1" data-step="1">
                <h3 class="quiz-question">${t('home.quiz.q1')}</h3>
                <div class="quiz-options-grid">
                  <button type="button" class="quiz-opt-btn" data-q="1" data-val="m3u">
                    <span class="opt-icon">📄</span>
                    <span>${t('home.quiz.q1_opt1')}</span>
                  </button>
                  <button type="button" class="quiz-opt-btn" data-q="1" data-val="xtream">
                    <span class="opt-icon">🔑</span>
                    <span>${t('home.quiz.q1_opt2')}</span>
                  </button>
                  <button type="button" class="quiz-opt-btn" data-q="1" data-val="stalker">
                    <span class="opt-icon">📡</span>
                    <span>${t('home.quiz.q1_opt3')}</span>
                  </button>
                </div>
              </div>

              <!-- Question 2 -->
              <div class="quiz-step" id="quizStep2" data-step="2">
                <h3 class="quiz-question">${t('home.quiz.q2')}</h3>
                <div class="quiz-options-grid">
                  <button type="button" class="quiz-opt-btn" data-q="2" data-val="tv">
                    <span class="opt-icon">📺</span>
                    <span>${t('home.quiz.q2_opt1')}</span>
                  </button>
                  <button type="button" class="quiz-opt-btn" data-q="2" data-val="mobile">
                    <span class="opt-icon">📱</span>
                    <span>${t('home.quiz.q2_opt2')}</span>
                  </button>
                </div>
              </div>

              <!-- Question 3 -->
              <div class="quiz-step" id="quizStep3" data-step="3">
                <h3 class="quiz-question">${t('home.quiz.q3')}</h3>
                <div class="quiz-options-grid">
                  <button type="button" class="quiz-opt-btn" data-q="3" data-val="has_epg">
                    <span class="opt-icon">🗓️</span>
                    <span>${t('home.quiz.q3_opt1')}</span>
                  </button>
                  <button type="button" class="quiz-opt-btn" data-q="3" data-val="need_epg">
                    <span class="opt-icon">🤖</span>
                    <span>${t('home.quiz.q3_opt2')}</span>
                  </button>
                </div>
              </div>

              <!-- Result View -->
              <div class="quiz-result" id="quizResult" style="display: none;">
                <div class="result-badge-wrap">
                  <span class="badge badge-version">Результат подбора</span>
                </div>
                <h3 class="result-title">${t('home.quiz.resultTitle')}</h3>
                <div class="result-tip-box" id="resultTipBox">
                  <!-- Injected dynamically -->
                </div>
                <div class="result-actions">
                  <a href="#/download" class="btn btn-primary btn-lg">
                    <span>${t('home.ctaTelegram')}</span>
                  </a>
                  <button type="button" class="btn btn-outline btn-lg" id="btnRestartQuiz">
                    <span>🔄 ${t('home.quiz.restart')}</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 6. 3-STEP QUICK START SCENARIO -->
      <section class="steps-section">
        <div class="container">
          <div class="section-header">
            <h2 class="section-title">${t('home.steps.title')}</h2>
            <p class="section-subtitle">${t('home.steps.subtitle')}</p>
          </div>

          <div class="steps-cards-grid">
            <div class="scenario-card animate-on-scroll">
              <div class="scenario-num">1</div>
              <div class="scenario-icon">📥</div>
              <h3 class="scenario-title">${t('home.steps.step1Title')}</h3>
              <p class="scenario-desc">${t('home.steps.step1Desc')}</p>
            </div>

            <div class="scenario-card animate-on-scroll">
              <div class="scenario-num">2</div>
              <div class="scenario-icon">🔗</div>
              <h3 class="scenario-title">${t('home.steps.step2Title')}</h3>
              <p class="scenario-desc">${t('home.steps.step2Desc')}</p>
            </div>

            <div class="scenario-card animate-on-scroll">
              <div class="scenario-num">3</div>
              <div class="scenario-icon">🎉</div>
              <h3 class="scenario-title">${t('home.steps.step3Title')}</h3>
              <p class="scenario-desc">${t('home.steps.step3Desc')}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- 7. HONEST COMPARISON TABLE -->
      <section class="compare-section">
        <div class="container">
          <div class="section-header">
            <h2 class="section-title">${t('home.compare.title')}</h2>
            <p class="section-subtitle">${t('home.compare.subtitle')}</p>
          </div>

          <div class="compare-table-wrap">
            <table class="compare-table">
              <thead>
                <tr>
                  <th class="th-param">${t('home.compare.colParam')}</th>
                  <th class="th-yai">${t('home.compare.colYai')}</th>
                  <th class="th-others">${t('home.compare.colOthers')}</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="td-param">${t('home.compare.stalker')}</td>
                  <td class="td-yai"><span class="check-green">✓</span> ${t('home.compare.stalkerYai')}</td>
                  <td class="td-others">${t('home.compare.stalkerOthers')}</td>
                </tr>
                <tr>
                  <td class="td-param">${t('home.compare.multiEpg')}</td>
                  <td class="td-yai"><span class="check-green">✓</span> ${t('home.compare.multiEpgYai')}</td>
                  <td class="td-others">${t('home.compare.multiEpgOthers')}</td>
                </tr>
                <tr>
                  <td class="td-param">${t('home.compare.catchup')}</td>
                  <td class="td-yai"><span class="check-green">✓</span> ${t('home.compare.catchupYai')}</td>
                  <td class="td-others">${t('home.compare.catchupOthers')}</td>
                </tr>
                <tr>
                  <td class="td-param">${t('home.compare.ads')}</td>
                  <td class="td-yai"><span class="check-green">✓</span> ${t('home.compare.adsYai')}</td>
                  <td class="td-others">${t('home.compare.adsOthers')}</td>
                </tr>
                <tr>
                  <td class="td-param">${t('home.compare.trackers')}</td>
                  <td class="td-yai"><span class="check-green">✓</span> ${t('home.compare.trackersYai')}</td>
                  <td class="td-others">${t('home.compare.trackersOthers')}</td>
                </tr>
                <tr>
                  <td class="td-param">${t('home.compare.opensource')}</td>
                  <td class="td-yai"><span class="check-green">✓</span> ${t('home.compare.opensourceYai')}</td>
                  <td class="td-others">${t('home.compare.opensourceOthers')}</td>
                </tr>
                <tr>
                  <td class="td-param">${t('home.compare.parity')}</td>
                  <td class="td-yai"><span class="check-green">✓</span> ${t('home.compare.parityYai')}</td>
                  <td class="td-others">${t('home.compare.parityOthers')}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- 8. BETA COMMUNITY FEEDBACK BLOCK -->
      <section class="beta-section">
        <div class="container">
          <div class="beta-card">
            <div class="beta-icon">💬</div>
            <div class="beta-content">
              <h3>${t('home.beta.title')}</h3>
              <p>${t('home.beta.desc')}</p>
            </div>
            <a href="${brand.links.telegramChat}" target="_blank" rel="noopener noreferrer" class="btn btn-accent btn-lg btn-beta-chat">
              <span>${t('home.beta.btnChat')}</span>
            </a>
          </div>
        </div>
      </section>

      <!-- 9. LATEST NEWS BLOCK -->
      <section class="news-block-section">
        <div class="container">
          <div class="section-header-row">
            <div>
              <h2 class="section-title">${t('home.newsBlock.title')}</h2>
              <p class="section-subtitle">${t('home.newsBlock.subtitle')}</p>
            </div>
            <a href="#/news" class="btn btn-outline btn-sm">
              <span>${t('home.newsBlock.allNews')}</span>
            </a>
          </div>

          <div class="home-news-grid">
            ${newsCardsHtml}
          </div>
        </div>
      </section>

      <!-- 10. FAQ ACCORDION -->
      <section class="faq-section" id="faqSection">
        <div class="container">
          <div class="section-header">
            <h2 class="section-title">${t('faq.title')}</h2>
            <p class="section-subtitle">${t('faq.subtitle')}</p>
          </div>

          <div class="faq-accordion">
            <div class="faq-item">
              <button type="button" class="faq-question" aria-expanded="false">
                <span>${t('faq.q1')}</span>
                <span class="faq-arrow">▼</span>
              </button>
              <div class="faq-answer">
                <p>${t('faq.a1')}</p>
              </div>
            </div>

            <div class="faq-item">
              <button type="button" class="faq-question" aria-expanded="false">
                <span>${t('faq.q2')}</span>
                <span class="faq-arrow">▼</span>
              </button>
              <div class="faq-answer">
                <p>${t('faq.a2')}</p>
              </div>
            </div>

            <div class="faq-item">
              <button type="button" class="faq-question" aria-expanded="false">
                <span>${t('faq.q3')}</span>
                <span class="faq-arrow">▼</span>
              </button>
              <div class="faq-answer">
                <p>${t('faq.a3')}</p>
              </div>
            </div>

            <div class="faq-item">
              <button type="button" class="faq-question" aria-expanded="false">
                <span>${t('faq.q4')}</span>
                <span class="faq-arrow">▼</span>
              </button>
              <div class="faq-answer">
                <p>${t('faq.a4')}</p>
              </div>
            </div>

            <div class="faq-item">
              <button type="button" class="faq-question" aria-expanded="false">
                <span>${t('faq.q5')}</span>
                <span class="faq-arrow">▼</span>
              </button>
              <div class="faq-answer">
                <p>${t('faq.a5')}</p>
              </div>
            </div>

            <div class="faq-item">
              <button type="button" class="faq-question" aria-expanded="false">
                <span>${t('faq.q6')}</span>
                <span class="faq-arrow">▼</span>
              </button>
              <div class="faq-answer">
                <p>${t('faq.a6')}</p>
              </div>
            </div>

            <div class="faq-item">
              <button type="button" class="faq-question" aria-expanded="false">
                <span>${t('faq.q7')}</span>
                <span class="faq-arrow">▼</span>
              </button>
              <div class="faq-answer">
                <p>${t('faq.a7')}</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 11. FINAL CTA -->
      <section class="final-cta-section">
        <div class="container">
          <div class="final-cta-card">
            <h2 class="final-title">${t('home.finalCta.title')}</h2>
            <p class="final-subtitle">${t('home.finalCta.subtitle')}</p>
            <div class="final-btn-group">
              <a href="${brand.links.telegramBot}" target="_blank" rel="noopener noreferrer" class="btn btn-primary btn-lg">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.64 6.8c-.15 1.58-.8 5.42-1.13 7.19-.14.75-.42 1-.68 1.03-.58.05-1.02-.38-1.58-.75-.88-.58-1.38-.94-2.23-1.5-.99-.65-.35-1.01.22-1.59.15-.15 2.71-2.48 2.76-2.69a.2.2 0 00-.05-.18c-.06-.05-.14-.03-.21-.02-.09.02-1.49.95-4.22 2.79-.4.27-.76.41-1.08.4-.36-.01-1.04-.2-1.55-.37-.63-.2-1.12-.31-1.08-.66.02-.18.27-.36.74-.55 2.92-1.27 4.86-2.11 5.83-2.51 2.78-1.16 3.35-1.36 3.73-1.36.08 0 .27.02.39.12.1.08.13.19.14.27-.01.06.01.24 0 .37z"/>
                </svg>
                <span>${t('home.finalCta.btnBot')}</span>
              </a>

              <a href="#/checklist" class="btn btn-outline btn-lg">
                <span>📋 ${t('home.finalCta.btnChecklist')}</span>
              </a>
            </div>
          </div>
        </div>
      </section>
    </div>
  `;
}

export function setupHomePage() {
  // 1. Device Tabs Setup
  const tabMobileBtn = document.getElementById('tabMobileBtn');
  const tabTvBtn = document.getElementById('tabTvBtn');
  const paneMobile = document.getElementById('paneMobile');
  const paneTv = document.getElementById('paneTv');

  function setTab(tab) {
    if (tab === 'tv') {
      tabMobileBtn?.classList.remove('active');
      tabMobileBtn?.setAttribute('aria-selected', 'false');
      tabTvBtn?.classList.add('active');
      tabTvBtn?.setAttribute('aria-selected', 'true');
      paneMobile?.classList.remove('active');
      paneTv?.classList.add('active');
    } else {
      tabTvBtn?.classList.remove('active');
      tabTvBtn?.setAttribute('aria-selected', 'false');
      tabMobileBtn?.classList.add('active');
      tabMobileBtn?.setAttribute('aria-selected', 'true');
      paneTv?.classList.remove('active');
      paneMobile?.classList.add('active');
    }
  }

  tabMobileBtn?.addEventListener('click', () => setTab('mobile'));
  tabTvBtn?.addEventListener('click', () => setTab('tv'));

  // 2. Interactive Quiz Logic
  const quizAnswers = { q1: null, q2: null, q3: null };
  const step1 = document.getElementById('quizStep1');
  const step2 = document.getElementById('quizStep2');
  const step3 = document.getElementById('quizStep3');
  const quizResult = document.getElementById('quizResult');
  const tipBox = document.getElementById('resultTipBox');
  const restartBtn = document.getElementById('btnRestartQuiz');

  function renderQuizResult() {
    step1.style.display = 'none';
    step2.style.display = 'none';
    step3.style.display = 'none';
    quizResult.style.display = 'block';

    let tipHtml = '';
    if (quizAnswers.q1 === 'stalker') {
      tipHtml += `<div class="tip-item"><span class="tip-icon">📡</span><p>${t('home.quiz.stalkerTip')}</p></div>`;
    } else if (quizAnswers.q1 === 'xtream') {
      tipHtml += `<div class="tip-item"><span class="tip-icon">🔑</span><p>${t('home.quiz.xtreamTip')}</p></div>`;
    } else {
      tipHtml += `<div class="tip-item"><span class="tip-icon">📄</span><p>${t('home.quiz.m3uTip')}</p></div>`;
    }

    if (quizAnswers.q3 === 'need_epg') {
      tipHtml += `<div class="tip-item"><span class="tip-icon">🤖</span><p>${t('home.quiz.epgTip')}</p></div>`;
    }

    if (tipBox) {
      tipBox.innerHTML = tipHtml;
    }
  }

  document.querySelectorAll('.quiz-opt-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const q = btn.getAttribute('data-q');
      const val = btn.getAttribute('data-val');
      quizAnswers[`q${q}`] = val;

      if (q === '1') {
        step1.classList.remove('active');
        step1.style.display = 'none';
        step2.classList.add('active');
        step2.style.display = 'block';
      } else if (q === '2') {
        step2.classList.remove('active');
        step2.style.display = 'none';
        step3.classList.add('active');
        step3.style.display = 'block';
      } else if (q === '3') {
        renderQuizResult();
      }
    });
  });

  restartBtn?.addEventListener('click', () => {
    quizAnswers.q1 = null;
    quizAnswers.q2 = null;
    quizAnswers.q3 = null;
    quizResult.style.display = 'none';
    step2.style.display = 'none';
    step3.style.display = 'none';
    step1.style.display = 'block';
    step1.classList.add('active');
  });

  // 3. FAQ Accordion Logic
  document.querySelectorAll('.faq-question').forEach(btn => {
    btn.addEventListener('click', () => {
      const isExpanded = btn.getAttribute('aria-expanded') === 'true';
      btn.setAttribute('aria-expanded', !isExpanded);
      const answer = btn.nextElementSibling;
      if (answer) {
        answer.classList.toggle('open', !isExpanded);
      }
      btn.classList.toggle('active', !isExpanded);
    });
  });

  // 4. Scroll Animation with IntersectionObserver
  if ('IntersectionObserver' in window) {
    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('visible');
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.1 });

    document.querySelectorAll('.animate-on-scroll').forEach(el => {
      observer.observe(el);
    });
  } else {
    document.querySelectorAll('.animate-on-scroll').forEach(el => {
      el.classList.add('visible');
    });
  }
}
