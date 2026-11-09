import './styles/main.css';
import './styles/landing.css';
import './styles/download.css';
import './styles/checklist.css';
import './styles/news.css';
import './styles/docs.css';

import { applyBrandTheme } from './brand.js';
import { initRouter } from './router.js';

document.addEventListener('DOMContentLoaded', () => {
  applyBrandTheme();
  initRouter();
});
