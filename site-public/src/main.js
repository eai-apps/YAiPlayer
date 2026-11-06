import './styles/main.css';
import './styles/landing.css';
import './styles/download.css';
import './styles/docs.css';

import { applyBrandTheme } from './brand.js';
import { initRouter } from './router.js';

document.addEventListener('DOMContentLoaded', () => {
  applyBrandTheme();
  initRouter();
});
