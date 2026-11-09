import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer-core';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');
const repoRoot = path.resolve(projectRoot, '..');
const distDir = path.resolve(projectRoot, 'dist');
const previewDir = path.resolve(repoRoot, 'docs', 'site-preview');

if (!fs.existsSync(previewDir)) {
  fs.mkdirSync(previewDir, { recursive: true });
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
    // Main landing required viewports
    { name: '01_home_desktop_1440', url: 'http://localhost:8092/#/', width: 1440, height: 900, fullPage: true },
    { name: '02_home_tablet_820', url: 'http://localhost:8092/#/', width: 820, height: 1180, fullPage: true },
    { name: '03_home_mobile_390', url: 'http://localhost:8092/#/', width: 390, height: 844, fullPage: true },

    // Additional key pages
    { name: '04_download_desktop_1440', url: 'http://localhost:8092/#/download', width: 1440, height: 900, fullPage: true },
    { name: '05_checklist_desktop_1440', url: 'http://localhost:8092/#/checklist', width: 1440, height: 900, fullPage: true },
    { name: '06_news_desktop_1440', url: 'http://localhost:8092/#/news', width: 1440, height: 900, fullPage: true }
  ];

  for (const s of scenarios) {
    await page.setViewport({ width: s.width, height: s.height, deviceScaleFactor: 2 });
    await page.goto(s.url, { waitUntil: 'networkidle0' });
    await new Promise(r => setTimeout(r, 400));

    const outPath = path.join(previewDir, `${s.name}.png`);
    await page.screenshot({ path: outPath, fullPage: s.fullPage });
    console.log(`Saved screenshot: ${s.name}.png -> ${outPath}`);
  }

  await browser.close();
  server.close();
  console.log(`All screenshots captured successfully to ${previewDir}`);
}

capture().catch(err => {
  console.error('Error capturing screenshots:', err);
  process.exit(1);
});
