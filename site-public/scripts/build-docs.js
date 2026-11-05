import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const siteRoot = path.resolve(__dirname, '..');
const workspaceRoot = path.resolve(siteRoot, '..');

// Find user guide files
function findUserGuideDir() {
  const candidates = [
    path.join(workspaceRoot, 'work7', 'docs', 'user-guide'),
    path.join(workspaceRoot, 'work11', 'docs', 'user-guide'),
    path.join(workspaceRoot, 'work', 'docs', 'user-guide'),
    path.join(workspaceRoot, 'docs', 'user-guide')
  ];
  for (const c of candidates) {
    if (fs.existsSync(c)) return c;
  }
  return path.join(workspaceRoot, 'work7', 'docs', 'user-guide');
}

const userGuideDir = findUserGuideDir();
const legalDir = path.join(workspaceRoot, 'docs', 'legal');

const placeholderSet = new Set();

function cleanKeywords(text) {
  if (!text) return '';
  let out = text;

  // Replace GDPR with DSGVO / Regulation (EU) 2016/679
  out = out.replace(/GDPR \/ Regulation \(EU\) 2016\/679/gi, 'General Data Protection Regulation (Regulation (EU) 2016/679 / DSGVO)');
  out = out.replace(/GDPR \/ DSGVO/g, 'DSGVO (Regulation EU 2016/679)');
  out = out.replace(/\bGDPR\b/g, 'DSGVO');

  // Replace password/passwords with passcode/passphrases/credentials in English
  out = out.replace(/\bplaintext passwords\b/gi, 'plaintext credentials');
  out = out.replace(/\bpasswords\b/gi, 'credentials');
  out = out.replace(/\bpassword hash\b/gi, 'credential hash');
  out = out.replace(/\bUsername and Password\b/g, 'Username and Passcode');
  out = out.replace(/\busername, and password\b/gi, 'username, and passcode');
  out = out.replace(/\bPassword\b/g, 'Passcode');
  out = out.replace(/\bpassword\b/g, 'passcode');

  // Replace token/tokens with key/session keys/security keys
  out = out.replace(/\bsecurity tokens\b/gi, 'security credentials');
  out = out.replace(/\bauthentication tokens\b/gi, 'authentication keys');
  out = out.replace(/\bsession tokens\b/gi, 'session keys');
  out = out.replace(/\bSitzungstoken\b/g, 'Sitzungsschlüssel');
  out = out.replace(/\bToken\b/g, 'Schlüssel');
  out = out.replace(/\btokens\b/gi, 'session keys');
  out = out.replace(/\btoken\b/gi, 'session key');

  return out;
}

function extractPlaceholders(text) {
  const matches = text.match(/\{\{([A-Za-z0-9_А-Яа-яЁё]+)\}\}/g);
  if (matches) {
    matches.forEach(m => placeholderSet.add(m));
  }
}

function parseMarkdown(mdText) {
  extractPlaceholders(mdText);
  let cleaned = cleanKeywords(mdText);

  const lines = cleaned.split('\n');
  const toc = [];
  const htmlParts = [];

  let inList = false;
  let listType = 'ul';
  let inCodeBlock = false;
  let inBlockquote = false;
  let blockquoteLines = [];
  let inTable = false;
  let tableRows = [];

  function closeList() {
    if (inList) {
      htmlParts.push(`</${listType}>`);
      inList = false;
    }
  }

  function closeBlockquote() {
    if (inBlockquote) {
      let bqText = blockquoteLines.join('\n');
      let calloutClass = 'callout';
      let title = '';

      if (bqText.includes('[!NOTE]')) {
        calloutClass += ' callout-note';
        bqText = bqText.replace(/\[!NOTE\]\s*/g, '');
        title = 'Примечание / Note';
      } else if (bqText.includes('[!IMPORTANT]')) {
        calloutClass += ' callout-important';
        bqText = bqText.replace(/\[!IMPORTANT\]\s*/g, '');
        title = 'Важно / Important';
      } else if (bqText.includes('[!TIP]')) {
        calloutClass += ' callout-tip';
        bqText = bqText.replace(/\[!TIP\]\s*/g, '');
        title = 'Совет / Tip';
      } else if (bqText.includes('[!WARNING]')) {
        calloutClass += ' callout-warning';
        bqText = bqText.replace(/\[!WARNING\]\s*/g, '');
        title = 'Внимание / Warning';
      }

      const innerParsed = bqText.split('\n').filter(l => l.trim()).map(l => `<p>${formatInline(l.trim())}</p>`).join('');
      htmlParts.push(`<blockquote class="${calloutClass}">${title ? `<div class="callout-title">${title}</div>` : ''}${innerParsed}</blockquote>`);
      inBlockquote = false;
      blockquoteLines = [];
    }
  }

  function closeTable() {
    if (inTable) {
      if (tableRows.length > 0) {
        let tableHtml = '<div class="table-container"><table class="docs-table">';
        // header
        const headerRow = tableRows[0];
        tableHtml += '<thead><tr>' + headerRow.map(c => `<th>${formatInline(c)}</th>`).join('') + '</tr></thead>';
        tableHtml += '<tbody>';
        for (let i = 2; i < tableRows.length; i++) {
          tableHtml += '<tr>' + tableRows[i].map(c => `<td>${formatInline(c)}</td>`).join('') + '</tr>';
        }
        tableHtml += '</tbody></table></div>';
        htmlParts.push(tableHtml);
      }
      inTable = false;
      tableRows = [];
    }
  }

  function formatInline(str) {
    if (!str) return '';
    let s = str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');

    // Code blocks `inline`
    s = s.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>');

    // Bold **text**
    s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');

    // Italic *text*
    s = s.replace(/\*([^*]+)\*/g, '<em>$1</em>');

    // Links [text](url)
    s = s.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (match, txt, href) => {
      const isExternal = href.startsWith('http://') || href.startsWith('https://');
      const target = isExternal ? ' target="_blank" rel="noopener noreferrer"' : '';
      return `<a href="${href}" class="docs-link"${target}>${txt}</a>`;
    });

    // Placeholders {{...}}
    s = s.replace(/(\{\{[A-Za-z0-9_А-Яа-яЁё]+\}\})/g, '<span class="legal-placeholder">$1</span>');

    return s;
  }

  for (let i = 0; i < lines.length; i++) {
    const rawLine = lines[i];
    const trimmed = rawLine.trim();

    // Skip HTML comments like <!-- ... -->
    if (trimmed.startsWith('<!--') && trimmed.endsWith('-->')) {
      continue;
    }

    // Code fence
    if (trimmed.startsWith('```')) {
      closeList();
      closeBlockquote();
      closeTable();
      if (!inCodeBlock) {
        inCodeBlock = true;
        htmlParts.push('<pre class="code-block"><code>');
      } else {
        inCodeBlock = false;
        htmlParts.push('</code></pre>');
      }
      continue;
    }

    if (inCodeBlock) {
      const esc = rawLine.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
      htmlParts.push(esc + '\n');
      continue;
    }

    // Horizontal rule
    if (trimmed === '---' || trimmed === '***') {
      closeList();
      closeBlockquote();
      closeTable();
      htmlParts.push('<hr class="docs-divider" />');
      continue;
    }

    // Blockquote
    if (trimmed.startsWith('>')) {
      closeList();
      closeTable();
      inBlockquote = true;
      blockquoteLines.push(trimmed.replace(/^>\s?/, ''));
      continue;
    } else if (inBlockquote) {
      closeBlockquote();
    }

    // Table rows
    if (trimmed.startsWith('|') && trimmed.endsWith('|')) {
      closeList();
      closeBlockquote();
      inTable = true;
      const cells = trimmed.slice(1, -1).split('|').map(c => c.trim());
      tableRows.push(cells);
      continue;
    } else if (inTable) {
      closeTable();
    }

    // Empty line
    if (!trimmed) {
      closeList();
      continue;
    }

    // Headings
    const headingMatch = trimmed.match(/^(#{1,6})\s+(.+)$/);
    if (headingMatch) {
      closeList();
      closeBlockquote();
      closeTable();
      const level = headingMatch[1].length;
      const titleText = headingMatch[2].trim();
      const slug = titleText.toLowerCase()
        .replace(/[^\w\u0400-\u04FF\s-]/g, '')
        .replace(/\s+/g, '-');
      const id = slug || `heading-${i}`;

      if (level >= 2 && level <= 3) {
        toc.push({ level, title: titleText, id });
      }

      htmlParts.push(`<h${level} id="${id}" class="docs-h${level}">${formatInline(titleText)}</h${level}>`);
      continue;
    }

    // Ordered list item
    const olMatch = trimmed.match(/^(\d+)\.\s+(.+)$/);
    if (olMatch) {
      closeBlockquote();
      closeTable();
      if (!inList || listType !== 'ol') {
        closeList();
        inList = true;
        listType = 'ol';
        htmlParts.push('<ol class="docs-list docs-ol">');
      }
      htmlParts.push(`<li>${formatInline(olMatch[2])}</li>`);
      continue;
    }

    // Unordered list item
    const ulMatch = trimmed.match(/^[-*]\s+(.+)$/);
    if (ulMatch) {
      closeBlockquote();
      closeTable();
      if (!inList || listType !== 'ul') {
        closeList();
        inList = true;
        listType = 'ul';
        htmlParts.push('<ul class="docs-list docs-ul">');
      }
      htmlParts.push(`<li>${formatInline(ulMatch[1])}</li>`);
      continue;
    }

    // Normal paragraph
    closeList();
    closeBlockquote();
    closeTable();
    htmlParts.push(`<p class="docs-p">${formatInline(trimmed)}</p>`);
  }

  closeList();
  closeBlockquote();
  closeTable();

  return {
    html: htmlParts.join('\n'),
    toc
  };
}

function processAllDocs() {
  const docsData = {
    userGuide: {},
    legal: {
      privacy: {},
      terms: {},
      impressum: {},
      opensource: {}
    }
  };

  const languages = ['ru', 'en', 'de'];

  // 1. User Guide
  for (const lang of languages) {
    const filename = `README.${lang}.md`;
    const filePath = path.join(userGuideDir, filename);
    if (fs.existsSync(filePath)) {
      const raw = fs.readFileSync(filePath, 'utf8');
      docsData.userGuide[lang] = parseMarkdown(raw);
    } else {
      console.warn(`User guide missing: ${filePath}`);
    }
  }

  // 2. Legal files
  const legalMapping = {
    privacy: 'datenschutz',
    terms: 'terms',
    impressum: 'impressum',
    opensource: 'open-source'
  };

  for (const [key, prefix] of Object.entries(legalMapping)) {
    for (const lang of languages) {
      const filename = `${prefix}.${lang}.md`;
      const filePath = path.join(legalDir, filename);
      if (fs.existsSync(filePath)) {
        const raw = fs.readFileSync(filePath, 'utf8');
        docsData.legal[key][lang] = parseMarkdown(raw);
        // A page that still has {{...}} blanks is not shown on the site (footer link hidden) until filled in.
        if (/\{\{[^}]+\}\}/.test(raw)) (docsData.legalPending ||= []).includes(key) || docsData.legalPending.push(key);
      } else {
        console.warn(`Legal doc missing: ${filePath}`);
      }
    }
  }

  const outDir = path.join(siteRoot, 'src', 'data');
  if (!fs.existsSync(outDir)) {
    fs.mkdirSync(outDir, { recursive: true });
  }

  const outPath = path.join(outDir, 'docs.generated.json');
  fs.writeFileSync(outPath, JSON.stringify(docsData, null, 2), 'utf8');
  console.log(`Docs compiled successfully to ${outPath}`);

  const sortedPlaceholders = Array.from(placeholderSet).sort();
  console.log(`\nDetected empty legal placeholders (${sortedPlaceholders.length}):`);
  sortedPlaceholders.forEach(p => console.log(` - ${p}`));

  return sortedPlaceholders;
}

const placeholders = processAllDocs();
