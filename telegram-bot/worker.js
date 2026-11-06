// YAiPlayer download-gate bot — runs as a Cloudflare Worker (Telegram webhook).
// Secrets/variables (Worker → Settings → Variables and Secrets):
//   BOT_TOKEN       (secret)  token from @BotFather
//   WEBHOOK_SECRET  (secret)  any random string; the same one is given to Telegram in setWebhook
//   CHANNEL         (text)    e.g. @YAiPlayer
//   CHAT            (text)    e.g. @YAiPlayer_Chat
const REPO = "eai-apps/YAiPlayer";
const DL = (f) => `https://github.com/${REPO}/releases/latest/download/${f}`;

const api = (env, method, body) =>
  fetch(`https://api.telegram.org/bot${env.BOT_TOKEN}/${method}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  }).then((r) => r.json());

const isMember = async (env, chat, userId) => {
  const r = await api(env, "getChatMember", { chat_id: chat, user_id: userId });
  return !!r.ok && ["creator", "administrator", "member", "restricted"].includes(r.result.status) &&
    !(r.result.status === "restricted" && r.result.is_member === false);
};

const gateKeyboard = (env) => ({
  inline_keyboard: [
    [{ text: "🔔 Канал", url: `https://t.me/${env.CHANNEL.replace("@", "")}` },
     { text: "💬 Чат", url: `https://t.me/${env.CHAT.replace("@", "")}` }],
    [{ text: "✅ Получить ссылку", callback_data: "check" }],
  ],
});

const downloadKeyboard = () => ({
  inline_keyboard: [
    [{ text: "📺 Скачать для ТВ", url: DL("YAiPlayer-TV.apk") }],
    [{ text: "📱 Скачать для телефона", url: DL("YAiPlayer-Mobile.apk") }],
  ],
});

async function onMessage(env, m) {
  const text = (m.text || "").trim();
  if (!text.startsWith("/start")) return;
  await api(env, "sendMessage", {
    chat_id: m.chat.id,
    parse_mode: "HTML",
    text: "👋 <b>YAiPlayer</b> — IPTV-плеер для Android TV и телефона.\n\n" +
      "Чтобы получить ссылку на скачивание, подпишись на канал и вступи в чат, затем нажми «Получить ссылку».",
    reply_markup: gateKeyboard(env),
  });
}

async function onCallback(env, q) {
  if (q.data !== "check") return;
  const uid = q.from.id;
  const [inChannel, inChat] = await Promise.all([isMember(env, env.CHANNEL, uid), isMember(env, env.CHAT, uid)]);
  if (inChannel && inChat) {
    await api(env, "answerCallbackQuery", { callback_query_id: q.id });
    await api(env, "sendMessage", {
      chat_id: q.message.chat.id,
      parse_mode: "HTML",
      text: "✅ Спасибо! Вот последняя версия:",
      reply_markup: downloadKeyboard(),
    });
  } else {
    const missing = [!inChannel && "на канал", !inChat && "в чат"].filter(Boolean).join(" и ");
    await api(env, "answerCallbackQuery", {
      callback_query_id: q.id,
      show_alert: true,
      text: `Пока не вижу подписки ${missing}. Подпишись и нажми кнопку ещё раз.`,
    });
  }
}

export default {
  async fetch(req, env, ctx) {
    if (req.method !== "POST") return new Response("YAiPlayer bot is running");
    if (req.headers.get("x-telegram-bot-api-secret-token") !== env.WEBHOOK_SECRET) {
      return new Response("forbidden", { status: 403 });
    }
    const update = await req.json();
    ctx.waitUntil(
      (async () => {
        try {
          if (update.message) await onMessage(env, update.message);
          else if (update.callback_query) await onCallback(env, update.callback_query);
        } catch (e) { console.log("error", String(e)); }
      })(),
    );
    return new Response("ok");
  },
};
