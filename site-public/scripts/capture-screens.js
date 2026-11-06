import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer-core';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');
const distDir = path.resolve(projectRoot, 'dist');
const screensDir = path.resolve(projectRoot, '_screens');

if (!fs.existsSync(screensDir)) {
  fs.mkdirSync(screensDir, { recursive: true });
}

const MIME_TYPES = {
  '.html': 'text/html',
  '.js': 'application/javascript',
  '.css': 'text/css',
  '.json': 'application/json',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon'
};

function startStaticServer(port = 8092) {
  return new Promise((resolve) => {
    const server = http.createServer((req, res) => {
      let reqPath = req.url.split('?')[0];
      if (reqPath === '/' || !path.extname(reqPath)) {
        reqPath = '/index.html';
      }
      const filePath = path.join(distDir, reqPath);

      fs.readFile(filePath, (err, data) => {
        if (err) {
          fs.readFile(path.join(distDir, 'index.html'), (indexErr, indexData) => {
            if (indexErr) {
              res.writeHead(404);
              res.end('Not found');
            } else {
              res.writeHead(200, { 'Content-Type': 'text/html' });
              res.end(indexData);
            }
          });
          return;
        }
        const ext = path.extname(filePath);
        res.writeHead(200, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' });
        res.end(data);
      });
    });

    server.listen(port, () => {
      console.log(`Static server running on http://localhost:${port}`);
      resolve(server);
    });
  });
}

function findBrowserPath() {
  const possiblePaths = [
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe'
  ];
  for (const p of possiblePaths) {
    if (fs.existsSync(p)) return p;
  }
  throw new Error('No compatible browser executable found.');
}

async function capture() {
  const server = await startStaticServer(8092);
  const browserPath = findBrowserPath();
  console.log(`Using browser at: ${browserPath}`);

  const browser = await puppeteer.launch({
    executablePath: browserPath,
    headless: true,
    args: ['--no-sandbox', '--disable-gpu', '--hide-scrollbars']
  });

  const page = await browser.newPage();

  const scenarios = [
    { name: '01_home_desktop', url: 'http://localhost:8092/#/', width: 1440, height: 900, fullPage: true },
    { name: '02_home_mobile', url: 'http://localhost:8092/#/', width: 375, height: 812, fullPage: false },
    { name: '03_download_desktop', url: 'http://localhost:8092/#/download', width: 1440, height: 900, fullPage: true },
    { name: '04_download_mobile', url: 'http://localhost:8092/#/download', width: 375, height: 812, fullPage: true },
    { name: '05_guide_desktop', url: 'http://localhost:8092/#/guide', width: 1440, height: 900, fullPage: false },
    { name: '06_guide_mobile', url: 'http://localhost:8092/#/guide', width: 375, height: 812, fullPage: false },
    { name: '07_faq_desktop', url: 'http://localhost:8092/#/faq', width: 1440, height: 900, fullPage: true },
    { name: '08_faq_mobile', url: 'http://localhost:8092/#/faq', width: 375, height: 812, fullPage: true },
    { name: '09_privacy_desktop', url: 'http://localhost:8092/#/privacy', width: 1440, height: 900, fullPage: false },
    { name: '10_terms_desktop', url: 'http://localhost:8092/#/terms', width: 1440, height: 900, fullPage: false },
    { name: '11_impressum_desktop', url: 'http://localhost:8092/#/impressum', width: 1440, height: 900, fullPage: false },
    { name: '12_opensource_desktop', url: 'http://localhost:8092/#/opensource', width: 1440, height: 900, fullPage: false },
    { name: '13_home_de_desktop', url: 'http://localhost:8092/#/', lang: 'de', width: 1440, height: 900, fullPage: true },
    { name: '14_download_en_desktop', url: 'http://localhost:8092/#/download', lang: 'en', width: 1440, height: 900, fullPage: true }
  ];

  for (const s of scenarios) {
    await page.setViewport({ width: s.width, height: s.height, deviceScaleFactor: 2 });
    await page.goto(s.url, { waitUntil: 'networkidle0' });

    if (s.lang) {
      await page.evaluate((targetLang) => {
        const btn = document.querySelector(`.lang-btn[data-lang="${targetLang}"]`);
        if (btn) btn.click();
      }, s.lang);
      await new Promise(r => setTimeout(r, 200));
    } else {
      await new Promise(r => setTimeout(r, 200));
    }

    const outPath = path.join(screensDir, `${s.name}.png`);
    await page.screenshot({ path: outPath, fullPage: s.fullPage });
    console.log(`Saved screenshot: ${s.name}.png`);
  }

  await browser.close();
  server.close();
  console.log('All screenshots captured successfully.');
}

capture().catch(err => {
  console.error('Error capturing screenshots:', err);
  process.exit(1);
});
