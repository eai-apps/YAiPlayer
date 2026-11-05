/**
 * YAiPlayer Web — Progressive Web App (PWA) Service Worker.
 *
 * Cache Strategy:
 * - App Shell static assets (HTML, JS, CSS, icons, fonts) are cached for offline resilience.
 * - EXPLICIT BYPASS: All API endpoints (/api/*), auth sessions, private telemetry,
 *   and video/audio media streams (*.m3u8, *.ts, *.mp4, /api/v1/proxy/*) are NEVER cached.
 * - Media streams and private handles must always bypass SW cache to prevent cross-account
 *   data leaks, stale auth tokens, and browser memory quota exhaustion.
 */

const CACHE_NAME = 'yai-player-shell-v1';
const SHELL_ASSETS = [
  '/',
  '/index.html',
  '/manifest.json',
  '/favicon.svg',
  '/icons.svg',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.filter((key) => key.startsWith('yai-player-shell-') && key !== CACHE_NAME).map((key) => caches.delete(key))
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  const url = new URL(request.url);

  // 1. NEVER intercept non-GET requests
  if (request.method !== 'GET') {
    return;
  }

  // 2. EXPLICIT BYPASS: Private API routes, auth endpoints, media proxies
  if (
    url.pathname.startsWith('/api/') ||
    url.pathname.startsWith('/stalker_portal/') ||
    url.searchParams.has('h') ||
    url.searchParams.has('token')
  ) {
    return; // Pass through straight to network
  }

  // 3. EXPLICIT BYPASS: Streaming media files & segments
  const mediaExtensions = ['.m3u8', '.ts', '.mp4', '.mkv', '.webm', '.aac', '.mp3', '.key'];
  if (mediaExtensions.some((ext) => url.pathname.toLowerCase().endsWith(ext))) {
    return; // Pass through straight to network
  }

  // 4. App shell cache-first with network fallback for local static assets
  if (url.origin === self.location.origin && !url.search && SHELL_ASSETS.includes(url.pathname)) {
    event.respondWith(
      caches.match(request).then((cachedResponse) => {
        if (cachedResponse) {
          // Revalidate in background
          fetch(request).then((networkResponse) => {
            if (networkResponse && networkResponse.status === 200) {
              caches.open(CACHE_NAME).then((cache) => cache.put(request, networkResponse));
            }
          }).catch(() => {});
          return cachedResponse;
        }
        return fetch(request);
      })
    );
  }
});
