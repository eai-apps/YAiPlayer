import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const source = await readFile(new URL('./worker.js', import.meta.url), 'utf8');
const { default: worker } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
async function menu(config) {
  const db = new Map(config ? [['cfg', JSON.stringify(config)]] : []);
  const env = { DB: { get: async k => db.get(k) || null, put: async (k,v) => db.set(k,v), delete: async k => db.delete(k) }, BOT_TOKEN: 'test', WEBHOOK_SECRET: 'test', CHANNEL: '@channel', CHAT: '@chat', ADMIN_ID: '1' };
  const sent=[]; const old=globalThis.fetch; globalThis.fetch=async (url,opts) => { sent.push(JSON.parse(opts.body)); return Response.json({ok:true}); };
  try { let job; await worker.fetch(new Request('https://test/', {method:'POST',headers:{'x-telegram-bot-api-secret-token':'test'},body:JSON.stringify({message:{text:'/start',chat:{id:2},from:{id:2}}})}),env,{waitUntil:p=>{job=p}}); await job; return sent[0].reply_markup.inline_keyboard.flat(); } finally { globalThis.fetch=old; }
}
test('new and existing menus retain entries and contain one radio button', async () => {
  for (const cfg of [null,{v:2,next:10,root:['1'],nodes:{1:{t:'Owner button',k:'url',u:'https://example.org/'}}}]) {
    const buttons=await menu(cfg); assert.equal(buttons.filter(b=>b.url==='https://yaiplay-radio.pages.dev/').length,1); if(cfg) assert.ok(buttons.some(b=>b.text==='Owner button'));
  }
});
test('existing radio link is not duplicated', async () => {
 const buttons=await menu({v:2,next:10,root:['1'],nodes:{1:{t:'Existing radio',k:'url',u:'https://yaiplay-radio.pages.dev'}}}); assert.equal(buttons.length,1);
});
