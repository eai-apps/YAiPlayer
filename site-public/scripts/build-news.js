import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');
const outputFile = path.resolve(projectRoot, 'src', 'data', 'news.generated.json');
const rssFile = path.resolve(projectRoot, 'public', 'news.xml');

function categorizeItem(text) {
  const lower = text.toLowerCase();
  if (
    lower.startsWith('добавлен') ||
    lower.startsWith('новый') ||
    lower.startsWith('новое') ||
    lower.startsWith('новая') ||
    lower.startsWith('раздел') ||
    lower.startsWith('появил') ||
    lower.startsWith('создан') ||
    lower.startsWith('поддержк') ||
    lower.startsWith('кнопка') ||
    lower.includes('новый раздел')
  ) {
    return { type: 'new', icon: '🆕', label: 'новое' };
  }
  if (
    lower.includes('исправлен') ||
    lower.includes('починен') ||
    lower.includes('ошибк') ||
    lower.includes('сбой') ||
    lower.includes('crash') ||
    lower.includes('не играл') ||
    lower.includes('баг') ||
    lower.includes('fix')
  ) {
    return { type: 'fix', icon: '🛠', label: 'исправлено' };
  }
  return { type: 'improvement', icon: '✨', label: 'лучше' };
}

function parseBody(body, seenItems) {
  if (!body) return [];
  const lines = body.split(/\r?\n/);
  const items = [];

  for (const rawLine of lines) {
    let line = rawLine.trim();
    if (!line) continue;
    // Strip leading markdown bullets
    line = line.replace(/^[-*•]\s+/, '').trim();
    if (!line) continue;
    // Skip github action technical lines if any
    if (line.startsWith('**Full Changelog**') || line.includes('github.com/eai-apps/YAiPlayer/compare/')) {
      continue;
    }

    const normKey = line.toLowerCase().replace(/[^a-zа-яё0-9]/g, '');
    if (seenItems.has(normKey)) {
      continue;
    }
    seenItems.add(normKey);

    const cat = categorizeItem(line);
    items.push({
      text: line,
      type: cat.type,
      icon: cat.icon,
      label: cat.label
    });
  }

  return items;
}

function generateRss(releases) {
  const siteUrl = 'https://yaiplayer.pages.dev';
  const itemsXml = releases.slice(0, 15).map(r => {
    const pubDate = new Date(r.publishedAt).toUTCString();
    const content = r.items.map(it => `${it.icon} ${it.text}`).join('<br/>\n');
    return `    <item>
      <title>YAiPlayer ${r.version}</title>
      <link>${siteUrl}/#/news</link>
      <guid isPermaLink="false">${r.version}</guid>
      <pubDate>${pubDate}</pubDate>
      <description><![CDATA[${content}]]></description>
    </item>`;
  }).join('\n');

  return `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>YAiPlayer — Новости и релизы</title>
    <link>${siteUrl}/#/news</link>
    <description>Официальные обновления, новые возможности и исправления IPTV-плеера YAiPlayer.</description>
    <language>ru</language>
    <lastBuildDate>${new Date().toUTCString()}</lastBuildDate>
${itemsXml}
  </channel>
</rss>
`;
}

async function fetchReleases() {
  const url = 'https://api.github.com/repos/eai-apps/YAiPlayer/releases?per_page=30';
  const headers = {
    'User-Agent': 'YAiPlayer-Build',
    'Accept': 'application/vnd.github.v3+json'
  };

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 6000);

  try {
    const res = await fetch(url, { headers, signal: controller.signal });
    clearTimeout(timer);
    if (!res.ok) {
      console.warn(`[build-news] GitHub API returned status ${res.status}. Falling back to cached news.`);
      return null;
    }
    const data = await res.json();
    if (!Array.isArray(data)) return null;
    return data;
  } catch (err) {
    clearTimeout(timer);
    console.warn(`[build-news] Could not fetch GitHub releases: ${err.message}. Using cache.`);
    return null;
  }
}

async function main() {
  const rawReleases = await fetchReleases();
  let news = [];

  if (rawReleases) {
    const seenItems = new Set();
    for (const r of rawReleases) {
      if (!r.body || !r.body.trim()) continue;
      const items = parseBody(r.body, seenItems);
      if (items.length === 0) continue;

      news.push({
        version: r.tag_name || r.name,
        publishedAt: r.published_at || new Date().toISOString(),
        htmlUrl: r.html_url || 'https://github.com/eai-apps/YAiPlayer/releases',
        items
      });
    }

    if (news.length > 0) {
      fs.mkdirSync(path.dirname(outputFile), { recursive: true });
      fs.writeFileSync(outputFile, JSON.stringify(news, null, 2), 'utf-8');
      console.log(`[build-news] Successfully generated news for ${news.length} releases -> ${outputFile}`);

      try {
        fs.mkdirSync(path.dirname(rssFile), { recursive: true });
        fs.writeFileSync(rssFile, generateRss(news), 'utf-8');
        console.log(`[build-news] Generated RSS feed -> ${rssFile}`);
      } catch (e) {
        console.warn(`[build-news] RSS generation warning: ${e.message}`);
      }
      return;
    }
  }

  // Fallback: check if existing file exists
  if (fs.existsSync(outputFile)) {
    console.log(`[build-news] Existing cached news preserved (${outputFile}).`);
  } else {
    // Generate minimal baseline if totally missing
    const fallbackNews = [
      {
        version: "v3.0.28",
        publishedAt: "2026-11-09T12:02:17Z",
        htmlUrl: "https://github.com/eai-apps/YAiPlayer/releases/tag/v3.0.28",
        items: [
          {
            text: "Архив в плейлистах теперь запрашивается в стандартной форме и работает у провайдеров, где раньше включался обычный эфир.",
            type: "fix",
            icon: "🛠",
            label: "исправлено"
          }
        ]
      },
      {
        version: "v3.0.27",
        publishedAt: "2026-11-09T11:09:43Z",
        htmlUrl: "https://github.com/eai-apps/YAiPlayer/releases/tag/v3.0.27",
        items: [
          {
            text: "Раздел «Избранное»: витрина любимых каналов с верхним рядом «Все избранные», группировкой по категориям, меткой «Архив» и общим избранным из всех плейлистов.",
            type: "new",
            icon: "🆕",
            label: "новое"
          }
        ]
      },
      {
        version: "v3.0.26",
        publishedAt: "2026-11-09T09:57:52Z",
        htmlUrl: "https://github.com/eai-apps/YAiPlayer/releases/tag/v3.0.26",
        items: [
          {
            text: "Архив передач теперь распознаётся и в плейлистах, где у канала указано только число дней архива (tvg-rec): значок архива и просмотр прошедших передач появятся после обновления плейлиста.",
            type: "improvement",
            icon: "✨",
            label: "лучше"
          }
        ]
      }
    ];
    fs.mkdirSync(path.dirname(outputFile), { recursive: true });
    fs.writeFileSync(outputFile, JSON.stringify(fallbackNews, null, 2), 'utf-8');
    fs.mkdirSync(path.dirname(rssFile), { recursive: true });
    fs.writeFileSync(rssFile, generateRss(fallbackNews), 'utf-8');
    console.log(`[build-news] Created default baseline news.`);
  }
}

main().catch(err => {
  console.error('[build-news] Error:', err);
  process.exit(0); // Do not break build if news fetch fails
});
