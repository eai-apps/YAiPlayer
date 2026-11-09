import { t, getLocale } from '../i18n/index.js';
import { brand } from '../brand.js';
import { formatDate } from '../util.js';
import newsData from '../data/news.generated.json';

export function renderNews() {
  const currentLang = getLocale();
  const releases = Array.isArray(newsData) ? newsData : [];

  const cardsHtml = releases.map((rel, rIndex) => {
    const formattedDate = formatDate(rel.publishedAt, currentLang);
    const itemsHtml = rel.items.map(item => {
      let badgeClass = 'badge-improvement';
      if (item.type === 'new') badgeClass = 'badge-new';
      else if (item.type === 'fix') badgeClass = 'badge-fix';

      return `
        <li class="news-item-row" data-type="${item.type}">
          <span class="news-item-badge ${badgeClass}">${item.icon} ${item.label}</span>
          <span class="news-item-text">${item.text}</span>
        </li>
      `;
    }).join('');

    return `
      <article class="news-card" data-version="${rel.version}">
        <div class="news-card-header">
          <div class="news-version-wrap">
            <span class="badge badge-accent news-tag">${t('news.releaseTag')} ${rel.version}</span>
            <time class="news-date" datetime="${rel.publishedAt}">${formattedDate}</time>
          </div>
          <a href="${rel.htmlUrl || brand.links.releases}" target="_blank" rel="noopener noreferrer" class="news-github-link">
            <span>${t('news.githubRelease')}</span>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path>
              <polyline points="15 3 21 3 21 9"></polyline>
              <line x1="10" y1="14" x2="21" y2="3"></line>
            </svg>
          </a>
        </div>
        <ul class="news-items-list">
          ${itemsHtml}
        </ul>
      </article>
    `;
  }).join('');

  return `
    <div class="news-page">
      <div class="container news-container">
        <div class="news-header">
          <h1 class="news-title">${t('news.title')}</h1>
          <p class="news-subtitle">${t('news.subtitle')}</p>

          <div class="news-toolbar">
            <div class="news-filters" id="newsFilters">
              <button type="button" class="btn btn-sm btn-outline filter-btn active" data-filter="all">
                ${t('news.filterAll')}
              </button>
              <button type="button" class="btn btn-sm btn-outline filter-btn" data-filter="new">
                ${t('news.filterNew')}
              </button>
              <button type="button" class="btn btn-sm btn-outline filter-btn" data-filter="improvement">
                ${t('news.filterImprovement')}
              </button>
              <button type="button" class="btn btn-sm btn-outline filter-btn" data-filter="fix">
                ${t('news.filterFix')}
              </button>
            </div>

            <a href="./news.xml" target="_blank" class="btn btn-sm btn-outline rss-link" title="RSS">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M4 11a9 9 0 0 1 9 9"></path>
                <path d="M4 4a16 16 0 0 1 16 16"></path>
                <circle cx="5" cy="19" r="1"></circle>
              </svg>
              <span>${t('news.rssButton')}</span>
            </a>
          </div>
        </div>

        <div class="news-list" id="newsList">
          ${cardsHtml.length > 0 ? cardsHtml : `<div class="news-empty">${t('news.empty')}</div>`}
        </div>
      </div>
    </div>
  `;
}

export function setupNewsPage() {
  const filterBtns = document.querySelectorAll('.filter-btn');
  const items = document.querySelectorAll('.news-item-row');
  const cards = document.querySelectorAll('.news-card');

  filterBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      filterBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      const filter = btn.getAttribute('data-filter');

      cards.forEach(card => {
        const cardItems = card.querySelectorAll('.news-item-row');
        let visibleInCard = 0;

        cardItems.forEach(item => {
          const type = item.getAttribute('data-type');
          if (filter === 'all' || type === filter) {
            item.style.display = 'flex';
            visibleInCard++;
          } else {
            item.style.display = 'none';
          }
        });

        // Hide card if no items match filter
        card.style.display = visibleInCard > 0 ? 'block' : 'none';
      });
    });
  });
}
