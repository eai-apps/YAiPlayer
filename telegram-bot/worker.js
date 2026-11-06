// YAiPlayer bot v0.3 — Cloudflare Worker (Telegram webhook + cron).
// Secrets: BOT_TOKEN, WEBHOOK_SECRET.  Vars: CHANNEL, CHAT, ADMIN_ID.  KV binding: DB.
// Everything in the menu is editable by the admin with buttons (no commands): see the "🛠 Админка" button.
const VERSION = "0.3.1";
const SUPPORT_URL = "https://t.me/YAiPlayer?direct"; // the channel's own direct messages = tech support
const REPO = "eai-apps/YAiPlayer";
const DL = (f) => `https://github.com/${REPO}/releases/latest/download/${f}`;
const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
const tme = (u) => `https://t.me/${u.replace("@", "")}`;
const isAdmin = (env, id) => String(id) === String(env.ADMIN_ID);
const BATCH = 40; // Workers free plan: 50 subrequests per run

const api = (env, method, body) =>
  fetch(`https://api.telegram.org/bot${env.BOT_TOKEN}/${method}`, {
    method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body),
  }).then((r) => r.json());

// ---------- config (menu tree) ----------
const defaultCfg = (env) => ({
  welcome: "👋 <b>YAiPlayer</b> — IPTV-плеер для Android TV и телефона.\n\nПлеер в бете: пишите о багах в чат. Выберите, что нужно:",
  req: [], // extra channels the user must join before the download link
  v: 2,
  next: 10,
  root: ["1", "2", "3", "4"],
  nodes: {
    1: { t: "📥 Скачать плеер", k: "download" },
    2: { t: "🌐 Каналы и проекты", k: "section", c: ["5", "6"] },
    3: { t: "☕ Поддержать автора", k: "donate" },
    4: { t: "🛟 Сообщить о проблеме", k: "section", c: ["7", "8"] },
    5: { t: "🔔 Канал YAiPlayer", k: "url", u: tme(env.CHANNEL) },
    6: { t: "💬 Чат", k: "url", u: tme(env.CHAT) },
    7: { t: "✉️ Написать в техподдержку", k: "url", u: SUPPORT_URL },
    8: { t: "💬 Спросить в чате", k: "url", u: tme(env.CHAT) },
  },
});
// v2: "Сообщить о проблеме" became a section (tech support + chat) instead of a plain chat link.
function migrate(env, cfg) {
  if (cfg.v >= 2) return cfg;
  cfg.v = 2;
  const old = cfg.nodes[4];
  if (old && old.k === "url" && old.u === tme(env.CHAT)) {
    cfg.nodes[7] = { t: "✉️ Написать в техподдержку", k: "url", u: SUPPORT_URL };
    cfg.nodes[8] = { t: "💬 Спросить в чате", k: "url", u: tme(env.CHAT) };
    cfg.nodes[4] = { t: old.t, k: "section", c: ["7", "8"] };
    cfg.next = Math.max(cfg.next || 10, 10);
  }
  return cfg;
}
const getCfg = async (env) => migrate(env, JSON.parse((await env.DB.get("cfg")) || "null") || defaultCfg(env));
const saveCfg = (env, c) => env.DB.put("cfg", JSON.stringify(c));
const childrenOf = (cfg, pid) => (pid === "root" ? cfg.root : cfg.nodes[pid]?.c || []);
const setChildren = (cfg, pid, list) => { if (pid === "root") cfg.root = list; else cfg.nodes[pid].c = list; };
const parentOf = (cfg, id) => {
  if (cfg.root.includes(id)) return "root";
  return Object.keys(cfg.nodes).find((k) => (cfg.nodes[k].c || []).includes(id)) || "root";
};
function removeNode(cfg, id) {
  for (const c of cfg.nodes[id]?.c || []) removeNode(cfg, c);
  cfg.root = cfg.root.filter((x) => x !== id);
  for (const k of Object.keys(cfg.nodes)) if (cfg.nodes[k].c) cfg.nodes[k].c = cfg.nodes[k].c.filter((x) => x !== id);
  delete cfg.nodes[id];
}

// ---------- storage ----------
const bump = async (env, key) => env.DB.put(key, String(Number((await env.DB.get(key)) || 0) + 1));
async function track(env, from) {
  if (!from || from.is_bot) return;
  const k = `u:${from.id}`;
  if (!(await env.DB.get(k))) await env.DB.put(k, "1");
}
async function countUsers(env) {
  let n = 0, cursor;
  do {
    const r = await env.DB.list({ prefix: "u:", cursor });
    n += r.keys.length;
    cursor = r.list_complete ? undefined : r.cursor;
  } while (cursor);
  return n;
}
const getState = async (env, uid) => JSON.parse((await env.DB.get(`st:${uid}`)) || "null");
const setState = (env, uid, s) => (s ? env.DB.put(`st:${uid}`, JSON.stringify(s)) : env.DB.delete(`st:${uid}`));

// ---------- sending ----------
async function send(env, chat_id, text, reply_markup) {
  const r = await api(env, "sendMessage", { chat_id, text, parse_mode: "HTML", disable_web_page_preview: true, reply_markup });
  if (!r.ok && /parse/i.test(r.description || "")) return api(env, "sendMessage", { chat_id, text, disable_web_page_preview: true, reply_markup });
  return r;
}
// Replace the message the button was pressed on (falls back to a new message).
async function show(env, q, text, reply_markup) {
  if (!q?.message) return null;
  const body = { chat_id: q.message.chat.id, message_id: q.message.message_id, text, parse_mode: "HTML", disable_web_page_preview: true, reply_markup };
  let r = await api(env, "editMessageText", body);
  if (!r.ok && /parse/i.test(r.description || "")) { delete body.parse_mode; r = await api(env, "editMessageText", body); }
  if (!r.ok && !/not modified/i.test(r.description || "")) return send(env, q.message.chat.id, text, reply_markup);
  return r;
}
const ack = (env, q, extra = {}) => api(env, "answerCallbackQuery", { callback_query_id: q.id, ...extra });

// ---------- user menu ----------
async function menuKeyboard(env, cfg, pid, uid) {
  const rows = childrenOf(cfg, pid).map((id) => {
    const n = cfg.nodes[id];
    return [n.k === "url" ? { text: n.t, url: n.u } : { text: n.t, callback_data: `n:${id}` }];
  });
  if (pid !== "root") rows.push([{ text: "⬅️ Назад", callback_data: `n:${parentOf(cfg, pid) === "root" ? "root" : parentOf(cfg, pid)}` }]);
  else if (isAdmin(env, uid)) rows.push([{ text: "🛠 Админка", callback_data: "a:home" }]);
  return { inline_keyboard: rows };
}
async function showNode(env, q, chat, uid, id) {
  const cfg = await getCfg(env);
  const render = async (text, kb) => (q ? show(env, q, text, kb) : send(env, chat, text, kb));
  if (id === "root") return render(cfg.welcome, await menuKeyboard(env, cfg, "root", uid));
  const n = cfg.nodes[id];
  if (!n) return render(cfg.welcome, await menuKeyboard(env, cfg, "root", uid));
  const back = { inline_keyboard: [[{ text: "⬅️ Назад", callback_data: `n:${parentOf(cfg, id)}` }]] };
  if (n.k === "section") return render(`<b>${esc(n.t)}</b>`, await menuKeyboard(env, cfg, id, uid));
  if (n.k === "text") return render(n.x || "—", back);
  if (n.k === "download") return render("Чтобы получить ссылку на скачивание, подпишитесь на канал, затем нажмите «Получить ссылку».", await gateKeyboard(env, cfg, id));
  if (n.k === "donate") return render("☕ <b>Поддержать автора</b>\nПлеер бесплатный. Если он вам нравится, можно поддержать разработку звёздами Telegram:", {
    inline_keyboard: [[{ text: "⭐ 50", callback_data: "star:50" }, { text: "⭐ 100", callback_data: "star:100" }, { text: "⭐ 250", callback_data: "star:250" }], back.inline_keyboard[0]],
  });
}

// ---------- download gate ----------
// Only the channel (and any extra channels the admin added) is required; the chat is optional.
const requiredChats = (env, cfg) => [env.CHANNEL, ...(cfg.req || [])];
async function gateKeyboard(env, cfg, id) {
  const urls = requiredChats(env, cfg).map((c, i) => ({ text: i === 0 ? "🔔 Подписаться на канал" : `➕ ${c}`, url: tme(c) }));
  const rows = [];
  for (let i = 0; i < urls.length; i += 2) rows.push(urls.slice(i, i + 2));
  rows.push([{ text: "✅ Получить ссылку", callback_data: "check" }]);
  rows.push([{ text: "⬅️ Меню", callback_data: "n:root" }]);
  return { inline_keyboard: rows };
}
async function isMember(env, chat, userId) {
  const r = await api(env, "getChatMember", { chat_id: chat, user_id: userId });
  if (!r.ok) return false;
  const s = r.result.status;
  return ["creator", "administrator", "member"].includes(s) || (s === "restricted" && r.result.is_member !== false);
}
async function onCheck(env, q) {
  const cfg = await getCfg(env);
  const chats = requiredChats(env, cfg);
  const res = await Promise.all(chats.map((c) => isMember(env, c, q.from.id)));
  if (res.every(Boolean)) {
    await ack(env, q);
    await bump(env, "stat:downloads");
    return show(env, q, "✅ Спасибо! Вот последняя версия:", {
      inline_keyboard: [
        [{ text: "📺 Скачать для ТВ", url: DL("YAiPlayer-TV.apk") }],
        [{ text: "📱 Скачать для телефона", url: DL("YAiPlayer-Mobile.apk") }],
        [{ text: "⬅️ Меню", callback_data: "n:root" }],
      ],
    });
  }
  const missing = chats.filter((_, i) => !res[i]).map((c) => c).join(", ");
  return ack(env, q, { show_alert: true, text: `Пока не вижу подписки: ${missing}. Подпишитесь и нажмите кнопку ещё раз.`.slice(0, 190) });
}

// ---------- admin panel (all buttons) ----------
const BTN = (t, d) => ({ text: t, callback_data: d });
const adminHome = async (env) => {
  const cfg = await getCfg(env);
  return {
    text: `🛠 <b>Админ-панель</b> · бот v${VERSION}\nВсё меняется кнопками.`,
    kb: { inline_keyboard: [
      [BTN("📊 Статистика", "a:stats")],
      [BTN("✏️ Меню бота", "a:m:root"), BTN("👋 Приветствие", "a:welcome")],
      [BTN(`📢 Обязательные каналы (${cfg.req.length})`, "a:req")],
      [BTN("📣 Рассылка", "a:bc")],
      [BTN("⬅️ В меню бота", "n:root")],
    ] },
  };
};
const KIND = { url: "🔗 ссылка", text: "📝 текст", section: "📂 раздел", download: "📥 скачивание (встроено)", donate: "☕ донаты (встроено)" };

async function adminMenuView(env, q, pid) {
  const cfg = await getCfg(env);
  const kids = childrenOf(cfg, pid);
  const rows = kids.map((id) => [BTN(`${cfg.nodes[id].t}  · ${KIND[cfg.nodes[id].k] || ""}`.slice(0, 60), `a:e:${id}`)]);
  rows.push([BTN("➕ Добавить кнопку сюда", `a:add:${pid}`)]);
  rows.push([BTN("⬅️ Назад", pid === "root" ? "a:home" : `a:e:${pid}`)]);
  const title = pid === "root" ? "Главное меню" : `Раздел «${esc(cfg.nodes[pid].t)}»`;
  return show(env, q, `✏️ <b>${title}</b>\nНажмите на кнопку, чтобы изменить её, или добавьте новую:`, { inline_keyboard: rows });
}
async function adminEditView(env, q, id) {
  const cfg = await getCfg(env);
  const n = cfg.nodes[id];
  if (!n) return adminMenuView(env, q, "root");
  const pid = parentOf(cfg, id);
  const sib = childrenOf(cfg, pid);
  const i = sib.indexOf(id);
  const detail = n.k === "url" ? `\nАдрес: ${esc(n.u)}` : n.k === "text" ? `\nТекст: ${esc((n.x || "").slice(0, 200))}` : "";
  const rows = [[BTN("✏️ Название", `a:t:${id}`)]];
  if (n.k === "url") rows.push([BTN("🔗 Изменить адрес", `a:v:${id}`)]);
  if (n.k === "text") rows.push([BTN("📝 Изменить текст", `a:v:${id}`)]);
  if (n.k === "section") rows.push([BTN("📂 Открыть раздел (кнопки внутри)", `a:m:${id}`)]);
  rows.push([BTN("⬆️ Выше", `a:up:${id}`), BTN("⬇️ Ниже", `a:dn:${id}`)]);
  rows.push([BTN("🗑 Удалить", `a:del:${id}`)]);
  rows.push([BTN("⬅️ Назад", `a:m:${pid}`)]);
  return show(env, q, `<b>${esc(n.t)}</b>\nТип: ${KIND[n.k] || n.k}${detail}\nПозиция: ${i + 1} из ${sib.length}`, { inline_keyboard: rows });
}

async function onAdminCb(env, q, d) {
  const uid = q.from.id;
  const chat = q.message.chat.id;
  const parts = d.split(":"); // a:<cmd>:<arg>
  const cmd = parts[1], arg = parts[2];
  await ack(env, q);

  if (cmd === "home") { await setState(env, uid, null); const h = await adminHome(env); return show(env, q, h.text, h.kb); }

  if (cmd === "stats") {
    const users = await countUsers(env);
    const dl = (await env.DB.get("stat:downloads")) || 0;
    const stars = (await env.DB.get("stat:stars_payments")) || 0;
    return show(env, q, `📊 <b>Статистика</b>\n\n👥 Пользователей: <b>${users}</b>\n📥 Выдано ссылок: <b>${dl}</b>\n⭐ Платежей звёздами: <b>${stars}</b>`, { inline_keyboard: [[BTN("⬅️ Назад", "a:home")]] });
  }

  if (cmd === "m") return adminMenuView(env, q, arg);
  if (cmd === "e") return adminEditView(env, q, arg);

  if (cmd === "t") { await setState(env, uid, { a: "title", id: arg }); return show(env, q, "Пришлите новое название кнопки (можно с эмодзи).", { inline_keyboard: [[BTN("Отмена", `a:e:${arg}`)]] }); }
  if (cmd === "v") {
    const cfg = await getCfg(env);
    await setState(env, uid, { a: "val", id: arg });
    return show(env, q, cfg.nodes[arg].k === "url" ? "Пришлите новый адрес (начинается с https://)." : "Пришлите новый текст сообщения (можно <b>жирный</b> и ссылки).", { inline_keyboard: [[BTN("Отмена", `a:e:${arg}`)]] });
  }

  if (cmd === "up" || cmd === "dn") {
    const cfg = await getCfg(env);
    const pid = parentOf(cfg, arg);
    const list = [...childrenOf(cfg, pid)];
    const i = list.indexOf(arg), j = cmd === "up" ? i - 1 : i + 1;
    if (j >= 0 && j < list.length) { [list[i], list[j]] = [list[j], list[i]]; setChildren(cfg, pid, list); await saveCfg(env, cfg); }
    return adminEditView(env, q, arg);
  }

  if (cmd === "del") {
    const cfg = await getCfg(env);
    const n = cfg.nodes[arg];
    if (!n) return adminMenuView(env, q, "root");
    return show(env, q, `Удалить «${esc(n.t)}»${n.c?.length ? " вместе со всем, что внутри" : ""}?`, { inline_keyboard: [[BTN("🗑 Да, удалить", `a:delok:${arg}`), BTN("Отмена", `a:e:${arg}`)]] });
  }
  if (cmd === "delok") {
    const cfg = await getCfg(env);
    const pid = parentOf(cfg, arg);
    removeNode(cfg, arg);
    await saveCfg(env, cfg);
    return adminMenuView(env, q, pid);
  }

  if (cmd === "add") {
    return show(env, q, "Какую кнопку добавить?", { inline_keyboard: [
      [BTN("🔗 Ссылка", `a:addk:url:${arg}`)], [BTN("📝 Текст (сообщение)", `a:addk:text:${arg}`)], [BTN("📂 Раздел (внутри ещё кнопки)", `a:addk:section:${arg}`)],
      [BTN("⬅️ Назад", `a:m:${arg}`)],
    ] });
  }
  if (cmd === "addk") {
    const kind = parts[2], pid = parts[3];
    await setState(env, uid, { a: "addt", kind, pid });
    return show(env, q, "Пришлите название новой кнопки (можно с эмодзи).", { inline_keyboard: [[BTN("Отмена", `a:m:${pid}`)]] });
  }

  if (cmd === "welcome") {
    const cfg = await getCfg(env);
    await setState(env, uid, { a: "welcome" });
    return show(env, q, `Сейчас приветствие:\n\n${cfg.welcome}\n\nПришлите новый текст (можно <b>жирный</b>, ссылки).`, { inline_keyboard: [[BTN("Отмена", "a:home")]] });
  }

  if (cmd === "req") {
    const cfg = await getCfg(env);
    const rows = cfg.req.map((c, i) => [BTN(`🗑 ${c}`, `a:reqdel:${i}`)]);
    rows.push([BTN("➕ Добавить канал", "a:reqadd")], [BTN("⬅️ Назад", "a:home")]);
    return show(env, q, `📢 <b>Обязательные каналы</b>\nКроме @YAiPlayer, человек должен подписаться на:\n${cfg.req.length ? cfg.req.map(esc).join("\n") : "— пока нет —"}\n\n⚠️ Бот должен быть администратором в этих каналах, иначе проверка не сработает.`, { inline_keyboard: rows });
  }
  if (cmd === "reqadd") { await setState(env, uid, { a: "reqadd" }); return show(env, q, "Пришлите адрес канала, например @mychannel.", { inline_keyboard: [[BTN("Отмена", "a:req")]] }); }
  if (cmd === "reqdel") {
    const cfg = await getCfg(env);
    cfg.req.splice(Number(arg), 1);
    await saveCfg(env, cfg);
    q.data = "a:req";
    return onAdminCb(env, q, "a:req");
  }

  if (cmd === "bc") {
    await setState(env, uid, { a: "bc" });
    return show(env, q, "📣 Пришлите текст рассылки. Его получат все, кто запускал бота (можно <b>жирный</b>, ссылки).", { inline_keyboard: [[BTN("Отмена", "a:home")]] });
  }
  if (cmd === "bcok") {
    const st = await getState(env, uid);
    if (!st?.text) return show(env, q, "Нет текста рассылки.", { inline_keyboard: [[BTN("⬅️ Назад", "a:home")]] });
    await env.DB.put("bc", JSON.stringify({ text: st.text, cursor: null, sent: 0, fail: 0, by: chat, done: false }));
    await setState(env, uid, null);
    await show(env, q, "🚀 Рассылка запущена. Я напишу, когда она закончится.", { inline_keyboard: [[BTN("⬅️ Назад", "a:home")]] });
    return processBroadcast(env);
  }
}

// Admin typed text while a flow is waiting for it.
async function onAdminText(env, m, st) {
  const uid = m.from.id, chat = m.chat.id, text = (m.text || "").trim();
  const done = (msg, kb) => send(env, chat, msg, kb || { inline_keyboard: [[BTN("🛠 Админка", "a:home")]] });
  if (!text) return;
  const cfg = await getCfg(env);

  if (st.a === "title") { if (cfg.nodes[st.id]) { cfg.nodes[st.id].t = text.slice(0, 40); await saveCfg(env, cfg); } await setState(env, uid, null); return done("✅ Название изменено.", { inline_keyboard: [[BTN("К кнопке", `a:e:${st.id}`)]] }); }

  if (st.a === "val") {
    const n = cfg.nodes[st.id];
    if (!n) return;
    if (n.k === "url") { if (!/^https?:\/\//.test(text)) return send(env, chat, "Адрес должен начинаться с https://. Пришлите ещё раз."); n.u = text; } else n.x = text;
    await saveCfg(env, cfg); await setState(env, uid, null);
    return done("✅ Сохранено.", { inline_keyboard: [[BTN("К кнопке", `a:e:${st.id}`)]] });
  }

  if (st.a === "addt") {
    const id = String(cfg.next++);
    cfg.nodes[id] = { t: text.slice(0, 40), k: st.kind };
    if (st.kind === "section") cfg.nodes[id].c = [];
    if (st.kind === "url" || st.kind === "text") {
      await saveCfg(env, cfg);
      await setState(env, uid, { a: "addv", id, pid: st.pid });
      return send(env, chat, st.kind === "url" ? "Пришлите адрес кнопки (https://…)." : "Пришлите текст, который покажет кнопка.");
    }
    setChildren(cfg, st.pid, [...childrenOf(cfg, st.pid), id]);
    await saveCfg(env, cfg); await setState(env, uid, null);
    return done("✅ Раздел добавлен.", { inline_keyboard: [[BTN("К кнопке", `a:e:${id}`)]] });
  }

  if (st.a === "addv") {
    const n = cfg.nodes[st.id];
    if (n.k === "url") { if (!/^https?:\/\//.test(text)) return send(env, chat, "Адрес должен начинаться с https://. Пришлите ещё раз."); n.u = text; } else n.x = text;
    setChildren(cfg, st.pid, [...childrenOf(cfg, st.pid), st.id]);
    await saveCfg(env, cfg); await setState(env, uid, null);
    return done("✅ Кнопка добавлена.", { inline_keyboard: [[BTN("К кнопке", `a:e:${st.id}`)]] });
  }

  if (st.a === "welcome") { cfg.welcome = text; await saveCfg(env, cfg); await setState(env, uid, null); return done("✅ Приветствие обновлено."); }

  if (st.a === "reqadd") {
    if (!/^@[A-Za-z0-9_]{4,}$/.test(text)) return send(env, chat, "Адрес должен быть вида @mychannel. Пришлите ещё раз.");
    if (!cfg.req.includes(text)) cfg.req.push(text);
    await saveCfg(env, cfg); await setState(env, uid, null);
    return done("✅ Канал добавлен. Не забудьте сделать бота администратором в нём.", { inline_keyboard: [[BTN("К списку", "a:req")]] });
  }

  if (st.a === "bc") {
    await setState(env, uid, { a: "bcprev", text });
    return send(env, chat, `Так будет выглядеть рассылка:\n\n${text}`, { inline_keyboard: [[BTN("✅ Отправить всем", "a:bcok")], [BTN("Отмена", "a:home")]] });
  }
}

// ---------- broadcast (resumable: a batch per run, cron continues it) ----------
async function processBroadcast(env) {
  const job = JSON.parse((await env.DB.get("bc")) || "null");
  if (!job || job.done) return;
  const r = await env.DB.list({ prefix: "u:", cursor: job.cursor || undefined, limit: BATCH });
  for (const k of r.keys) {
    const id = k.name.slice(2);
    const res = await api(env, "sendMessage", { chat_id: id, text: job.text, parse_mode: "HTML", disable_web_page_preview: true, disable_notification: true });
    if (res.ok) job.sent++; else job.fail++;
  }
  job.cursor = r.list_complete ? null : r.cursor;
  job.done = r.list_complete;
  await env.DB.put("bc", JSON.stringify(job));
  if (job.done) await send(env, job.by, `✅ Рассылка завершена: доставлено ${job.sent}, не доставлено ${job.fail}.`);
}

// ---------- updates ----------
async function onMessage(env, m) {
  await track(env, m.from);
  const chat = m.chat.id;
  if (m.successful_payment) {
    await bump(env, "stat:stars_payments");
    return send(env, chat, "❤️ Спасибо за поддержку! Это очень помогает развивать плеер.", { inline_keyboard: [[BTN("⬅️ Меню", "n:root")]] });
  }
  const text = (m.text || "").trim();
  if (isAdmin(env, m.from?.id) && !text.startsWith("/")) {
    const st = await getState(env, m.from.id);
    if (st) return onAdminText(env, m, st);
  }
  if (text.startsWith("/admin") && isAdmin(env, m.from?.id)) { const h = await adminHome(env); return send(env, chat, h.text, h.kb); }
  if (text.startsWith("/start") || text.startsWith("/menu")) { if (isAdmin(env, m.from?.id)) await setState(env, m.from.id, null); return showNode(env, null, chat, m.from?.id, "root"); }
}

async function onCallback(env, q) {
  await track(env, q.from);
  const d = q.data || "";
  const chat = q.message.chat.id;
  if (d.startsWith("a:")) { if (!isAdmin(env, q.from.id)) return ack(env, q); return onAdminCb(env, q, d); }
  if (d.startsWith("n:")) { await ack(env, q); return showNode(env, q, chat, q.from.id, d.slice(2)); }
  if (d === "check") return onCheck(env, q);
  if (d.startsWith("star:")) {
    const n = Number(d.slice(5));
    await ack(env, q);
    if (![50, 100, 250].includes(n)) return;
    return api(env, "sendInvoice", { chat_id: chat, title: "Поддержать YAiPlayer", description: `Поддержка разработки плеера: ${n} ⭐`, payload: `donate:${n}`, currency: "XTR", prices: [{ label: "Поддержка", amount: n }] });
  }
  return ack(env, q);
}

export default {
  async fetch(req, env, ctx) {
    if (req.method !== "POST") return new Response(`YAiPlayer bot v${VERSION} is running`);
    if (req.headers.get("x-telegram-bot-api-secret-token") !== env.WEBHOOK_SECRET) return new Response("forbidden", { status: 403 });
    const update = await req.json();
    ctx.waitUntil((async () => {
      try {
        if (update.message) await onMessage(env, update.message);
        else if (update.callback_query) await onCallback(env, update.callback_query);
        else if (update.pre_checkout_query) await api(env, "answerPreCheckoutQuery", { pre_checkout_query_id: update.pre_checkout_query.id, ok: true });
      } catch (e) { console.log("error", String(e)); }
    })());
    return new Response("ok");
  },
  async scheduled(event, env, ctx) { ctx.waitUntil(processBroadcast(env)); },
};
