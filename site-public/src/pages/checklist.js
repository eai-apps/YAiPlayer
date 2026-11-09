import { t } from '../i18n/index.js';
import { brand } from '../brand.js';

const STORAGE_KEY = 'yai_checklist_state';

function getSavedState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    return JSON.parse(raw) || {};
  } catch (e) {
    console.warn('localStorage error:', e);
    return {};
  }
}

function saveState(state) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch (e) {
    console.warn('localStorage error:', e);
  }
}

export function renderChecklist() {
  const steps = [
    { id: 'step1', titleKey: 'checklist.step1Title', descKey: 'checklist.step1Desc', icon: '⚡' },
    { id: 'step2', titleKey: 'checklist.step2Title', descKey: 'checklist.step2Desc', icon: '📋' },
    { id: 'step3', titleKey: 'checklist.step3Title', descKey: 'checklist.step3Desc', icon: '⏪' },
    { id: 'step4', titleKey: 'checklist.step4Title', descKey: 'checklist.step4Desc', icon: '📅' },
    { id: 'step5', titleKey: 'checklist.step5Title', descKey: 'checklist.step5Desc', icon: '📥' },
    { id: 'step6', titleKey: 'checklist.step6Title', descKey: 'checklist.step6Desc', icon: '➕' },
    { id: 'step7', titleKey: 'checklist.step7Title', descKey: 'checklist.step7Desc', icon: '🚀' }
  ];

  const stepsHtml = steps.map((s, index) => {
    return `
      <div class="checklist-item" data-step="${s.id}" id="card_${s.id}">
        <label class="checklist-label">
          <input type="checkbox" class="checklist-checkbox" id="chk_${s.id}" data-id="${s.id}" />
          <div class="checklist-box-visual" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
              <polyline points="20 6 9 17 4 12"></polyline>
            </svg>
          </div>
          <div class="checklist-content">
            <div class="checklist-item-header">
              <span class="checklist-icon">${s.icon}</span>
              <h3 class="checklist-item-title">${t(s.titleKey)}</h3>
            </div>
            <p class="checklist-item-desc">${t(s.descKey)}</p>
          </div>
        </label>
      </div>
    `;
  }).join('');

  return `
    <div class="checklist-page">
      <div class="container checklist-container">
        <div class="checklist-header">
          <div class="checklist-badge-wrap">
            <span class="badge badge-accent">Лид-магнит • 5 минут</span>
          </div>
          <h1 class="checklist-title">${t('checklist.title')}</h1>
          <p class="checklist-subtitle">${t('checklist.subtitle')}</p>

          <div class="checklist-progress-card">
            <div class="progress-info-row">
              <span class="progress-label">${t('checklist.progressLabel')}</span>
              <span class="progress-stat" id="progressStat">0 / 7 (0%)</span>
            </div>
            <div class="progress-bar-track">
              <div class="progress-bar-fill" id="progressBarFill" style="width: 0%"></div>
            </div>
            <div class="progress-actions">
              <button type="button" class="btn btn-outline btn-sm" id="btnResetChecklist">
                ${t('checklist.resetBtn')}
              </button>
            </div>
          </div>
        </div>

        <div class="checklist-list" id="checklistList">
          ${stepsHtml}
        </div>

        <!-- Completion celebration card (hidden by default) -->
        <div class="checklist-complete-card" id="checklistCompleteCard" style="display: none;">
          <div class="complete-icon">🎉</div>
          <h2>${t('checklist.completeTitle')}</h2>
          <p>${t('checklist.completeDesc')}</p>
          <div class="complete-cta-group">
            <a href="#/download" class="btn btn-primary btn-lg">
              <span>${t('checklist.btnDownload')}</span>
            </a>
            <a href="${brand.links.telegramChannel}" target="_blank" rel="noopener noreferrer" class="btn btn-outline btn-lg">
              <span>${t('checklist.btnTelegram')}</span>
            </a>
          </div>
        </div>
      </div>
    </div>
  `;
}

export function setupChecklistPage() {
  const state = getSavedState();
  const checkboxes = document.querySelectorAll('.checklist-checkbox');
  const progressStat = document.getElementById('progressStat');
  const progressBarFill = document.getElementById('progressBarFill');
  const completeCard = document.getElementById('checklistCompleteCard');
  const resetBtn = document.getElementById('btnResetChecklist');

  function updateUi() {
    let checkedCount = 0;
    checkboxes.forEach(chk => {
      const id = chk.getAttribute('data-id');
      const isChecked = Boolean(state[id]);
      chk.checked = isChecked;
      const card = document.getElementById(`card_${id}`);
      if (card) {
        card.classList.toggle('checked', isChecked);
      }
      if (isChecked) checkedCount++;
    });

    const total = checkboxes.length || 7;
    const percent = Math.round((checkedCount / total) * 100);

    if (progressStat) {
      progressStat.textContent = `${checkedCount} / ${total} (${percent}%)`;
    }
    if (progressBarFill) {
      progressBarFill.style.width = `${percent}%`;
    }
    if (completeCard) {
      completeCard.style.display = checkedCount === total ? 'block' : 'none';
    }
  }

  checkboxes.forEach(chk => {
    chk.addEventListener('change', (e) => {
      const id = e.target.getAttribute('data-id');
      state[id] = e.target.checked;
      saveState(state);
      updateUi();
    });
  });

  if (resetBtn) {
    resetBtn.addEventListener('click', () => {
      Object.keys(state).forEach(k => delete state[k]);
      saveState(state);
      updateUi();
    });
  }

  updateUi();
}
