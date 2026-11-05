import brandConfig from '../brand.json';

export const brand = brandConfig;

export function applyBrandTheme() {
  const root = document.documentElement;
  const colors = brandConfig.colors;

  if (colors) {
    if (colors.primary) root.style.setProperty('--color-primary', colors.primary);
    if (colors.primaryHover) root.style.setProperty('--color-primary-hover', colors.primaryHover);
    if (colors.primaryLight) root.style.setProperty('--color-primary-light', colors.primaryLight);
    if (colors.accent) root.style.setProperty('--color-accent', colors.accent);
    if (colors.accentHover) root.style.setProperty('--color-accent-hover', colors.accentHover);
    if (colors.accentLight) root.style.setProperty('--color-accent-light', colors.accentLight);
    if (colors.success) root.style.setProperty('--color-success', colors.success);
    if (colors.warning) root.style.setProperty('--color-warning', colors.warning);
    if (colors.danger) root.style.setProperty('--color-danger', colors.danger);
  }

  // Update favicon if path provided
  const favicon = document.querySelector("link[rel~='icon']");
  if (favicon && brandConfig.logo && brandConfig.logo.path) {
    favicon.href = brandConfig.logo.path;
  }
}

export function getBrandSlogan(lang = 'ru') {
  if (typeof brandConfig.slogan === 'object') {
    return brandConfig.slogan[lang] || brandConfig.slogan.en || brandConfig.tagline;
  }
  return brandConfig.slogan || brandConfig.tagline;
}
