// YAiPlayer bot v0.2 — Cloudflare Worker (Telegram webhook).
// Secrets: BOT_TOKEN, WEBHOOK_SECRET.  Vars: CHANNEL, CHAT, ADMIN_ID.  KV binding: DB.
const VERSION = "0.2";
const REPO = "eai-apps/YAiPlayer";
const DL = (f) => `https://github.com/${REPO}/releases/latest/download/${f}`;
const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
const tme = (u) => `https://t.me/${u.replace("@", "")}`;

const api = (env, method, body) =>
  fetch(`https://api.telegram.org/bot${env.BOT_TOKEN}/${method}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  }).then((r) => r.json());

const isAdmin = (env, id) => String(id) === String(env.ADMIN_ID);

const isMember = async (env, chat, userId) => {
  const r = await api(env, "getChatMember", { chat_id: chat, user_id: userId });
  if (!r.ok) return false;
  const s = r.result.status;
  return ["creator", "administrator", "member"].includes(s) || (s === "restricted" && r.result.is_member !== false);
};

// ---- storage (KV) ----
const getLinks = async (env) => JSON.parse((await env.DB.get("links")) || "[]");
const setLinks = (env, v) => env.DB.put("links", JSON.stringify(v));
const bump = async (env, key) => env.DB.put(key, String(Number((await env.DB.get(key)) || 0) + 1));
async function track(env, from) {
  if (!from || from.is_bot) return;
  const k = `u:${from.id}`;
  if (!(await env.DB.get(k))) await env.DB.put(k, JSON.stringify({ n: from.first_name || "", t: Date.now() }));
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

// ---- keyboards ----
const mainMenu = (env) => ({
  inline_keyboard: [
    [{ text: "📥 Скачать плеер", callback_data: "dl" }],
    [{ text: "🌐 Каналы и проекты", callback_data: "links" }, { text: "☕ Поддержать автора", callback_data: "donate" }],
    [{ text: "🛟 Сообщить о проблеме", url: tme(env.CHAT) }],
  ],
});
const backRow = [{ text: "⬅️ Меню", callback_data: "menu" }];
const gateKeyboard = (env) => ({
  inline_keyboard: [
    [{ text: "🔔 Канал", url: tme(env.CHANNEL) }, { text: "💬 Чат", url: tme(env.CHAT) }],
    [{ text: "✅ Получить ссылку", callback_data: "check" }],
    backRow,
  ],
});
const downloadKeyboard = () => ({
  inline_keyboard: [
    [{ text: "📺 Скачать для ТВ", url: DL("YAiPlayer-TV.apk") }],
    [{ text: "📱 Скачать для телефона", url: DL("YAiPlayer-Mobile.apk") }],
    backRow,
  ],
});

const send = (env, chat_id, text, reply_markup) =>
  api(env, "sendMessage", { chat_id, text, parse_mode: "HTML", disable_web_page_preview: true, reply_markup });

const WELCOME =
  "👋 <b>YAiPlayer</b> — IPTV-плеер для Android TV и телефона.\n\n" +
  "Плеер в бете: пишите о багах в чат. Выберите, что нужно:";

async function showMenu(env, chat) { await send(env, chat, WELCOME, mainMenu(env)); }

async function onMessage(env, m) {
  await track(env, m.from);
  const chat = m.chat.id;
  if (m.successful_payment) {
    await bump(env, "stat:stars_payments");
    await send(env, chat, "❤️ Спасибо за поддержку! Это очень помогает развивать плеер.", { inline_keyboard: [backRow] });
    return;
  }
  const text = (m.text || "").trim();
  if (text.startsWith("/start") || text.startsWith("/menu")) return showMenu(env, chat);
  if (isAdmin(env, m.from?.id) && text.startsWith("/")) return onAdmin(env, chat, text);
}

async function onAdmin(env, chat, text) {
  const [cmd, ...rest] = text.split(" ");
  const arg = rest.join(" ").trim();
  if (cmd === "/admin") {
    const users = await countUsers(env);
    const dl = (await env.DB.get("stat:downloads")) || 0;
    const stars = (await env.DB.get("stat:stars_payments")) || 0;
    const links = await getLinks(env);
    return send(env, chat,
      `🛠 <b>Админ-панель</b> · бот v${VERSION}\n\n👥 Пользователей: <b>${users}</b>\n📥 Выдано ссылок: <b>${dl}</b>\n⭐ Платежей Stars: <b>${stars}</b>\n🔗 Своих ссылок в меню: <b>${links.length}</b>\n\n` +
      "<b>Команды</b>\n/links — список ссылок меню\n/addlink Название | https://адрес — добавить ссылку (раздел «Каналы и проекты»)\n/dellink N — удалить ссылку номер N");
  }
  if (cmd === "/links") {
    const links = await getLinks(env);
    return send(env, chat, links.length ? links.map((l, i) => `${i + 1}. ${esc(l.t)} — ${esc(l.u)}`).join("\n") : "Своих ссылок пока нет. Добавьте: /addlink Название | https://адрес");
  }
  if (cmd === "/addlink") {
    const [t, u] = arg.split("|").map((s) => (s || "").trim());
    if (!t || !/^https?:\/\//.test(u || "")) return send(env, chat, "Формат: /addlink Название | https://адрес");
    const links = await getLinks(env);
    links.push({ t: t.slice(0, 40), u });
    await setLinks(env, links);
    return send(env, chat, `✅ Добавлено (${links.length}): ${esc(t)}`);
  }
  if (cmd === "/dellink") {
    const links = await getLinks(env);
    const i = Number(arg) - 1;
    if (!links[i]) return send(env, chat, "Нет ссылки с таким номером. Смотрите /links");
    const [gone] = links.splice(i, 1);
    await setLinks(env, links);
    return send(env, chat, `🗑 Удалено: ${esc(gone.t)}`);
  }
}

async function onCallback(env, q) {
  await track(env, q.from);
  const chat = q.message.chat.id;
  const uid = q.from.id;
  const ack = (extra = {}) => api(env, "answerCallbackQuery", { callback_query_id: q.id, ...extra });
  const d = q.data || "";

  if (d === "menu") { await ack(); return showMenu(env, chat); }

  if (d === "dl") {
    await ack();
    return send(env, chat, "Чтобы получить ссылку на скачивание, подпишитесь на канал и вступите в чат, затем нажмите «Получить ссылку».", gateKeyboard(env));
  }

  if (d === "check") {
    const [inChannel, inChat] = await Promise.all([isMember(env, env.CHANNEL, uid), isMember(env, env.CHAT, uid)]);
    if (inChannel && inChat) {
      await ack();
      await bump(env, "stat:downloads");
      return send(env, chat, "✅ Спасибо! Вот последняя версия:", downloadKeyboard());
    }
    const missing = [!inChannel && "на канал", !inChat && "в чат"].filter(Boolean).join(" и ");
    return ack({ show_alert: true, text: `Пока не вижу подписки ${missing}. Подпишитесь и нажмите кнопку ещё раз.` });
  }

  if (d === "links") {
    await ack();
    const extra = (await getLinks(env)).map((l) => [{ text: l.t, url: l.u }]);
    return send(env, chat, "🌐 <b>Каналы и проекты</b>", {
      inline_keyboard: [
        [{ text: "🔔 Канал YAiPlayer", url: tme(env.CHANNEL) }, { text: "💬 Чат", url: tme(env.CHAT) }],
        ...extra,
        backRow,
      ],
    });
  }

  if (d === "donate") {
    await ack();
    return send(env, chat, "☕ <b>Поддержать автора</b>\nПлеер бесплатный. Если он вам нравится, можно поддержать разработку звёздами Telegram:", {
      inline_keyboard: [
        [{ text: "⭐ 50", callback_data: "star:50" }, { text: "⭐ 100", callback_data: "star:100" }, { text: "⭐ 250", callback_data: "star:250" }],
        backRow,
      ],
    });
  }

  if (d.startsWith("star:")) {
    const n = Number(d.slice(5));
    if (![50, 100, 250].includes(n)) return ack();
    await ack();
    return api(env, "sendInvoice", {
      chat_id: chat,
      title: "Поддержать YAiPlayer",
      description: `Поддержка разработки плеера: ${n} ⭐`,
      payload: `donate:${n}`,
      currency: "XTR",
      prices: [{ label: "Поддержка", amount: n }],
    });
  }
  return ack();
}

export default {
  async fetch(req, env, ctx) {
    if (req.method !== "POST") return new Response(`YAiPlayer bot v${VERSION} is running`);
    if (req.headers.get("x-telegram-bot-api-secret-token") !== env.WEBHOOK_SECRET) {
      return new Response("forbidden", { status: 403 });
    }
    const update = await req.json();
    ctx.waitUntil(
      (async () => {
        try {
          if (update.message) await onMessage(env, update.message);
          else if (update.callback_query) await onCallback(env, update.callback_query);
          else if (update.pre_checkout_query) await api(env, "answerPreCheckoutQuery", { pre_checkout_query_id: update.pre_checkout_query.id, ok: true });
        } catch (e) { console.log("error", String(e)); }
      })(),
    );
    return new Response("ok");
  },
};
