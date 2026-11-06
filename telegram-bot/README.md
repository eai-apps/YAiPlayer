# YAiPlayer download-gate bot
Cloudflare Worker (`worker.js`). /start → asks to join the channel + chat → button «Получить ссылку» checks membership via `getChatMember` → sends download buttons (always-latest GitHub release assets `YAiPlayer-TV.apk`, `YAiPlayer-Mobile.apk`).
Variables: BOT_TOKEN, WEBHOOK_SECRET (secrets), CHANNEL, CHAT (text). The bot must be admin in both the channel and the chat.
Webhook: `https://api.telegram.org/bot<TOKEN>/setWebhook?url=<worker url>&secret_token=<WEBHOOK_SECRET>`.
