# YAiPlayer — status and plan (2026-11-07)

## Done (released up to v2.0.43)
- Player on a clean OwnTV base, full rebrand, signed APKs with OTA update, public repo (GPL), CI (tests + build + release), Telegram release posts with buttons.
- Built-in hidden guide (3 feeds, weekly), guide icons per playlist (opt-in), Stalker: guide loaded in bulk, categories in the device language, no stream source shown in stream info, reliable MAC (`59:41:49:xx:xx:xx`).
- Profiles: new profile is clean, opt-in "playlists of other profiles"; new splash/intro.
- Telegram: channel @YAiPlayer, chat @YAiPlayer_Chat (comments under posts), promo post every 3 days 09:00 (poster + text), release posts. Posts now carry «Получить ссылку» → bot, no direct APK links.
- Bot @YAiPlayer_bot (Cloudflare Worker `yaiplayer-bot`, code in `telegram-bot/`, auto-deployed from GitHub): channel-subscription gate → download buttons; button-driven admin panel (menu tree, welcome text, required extra channels, broadcast, stats); Stars donations; support = channel direct messages.

## Next (ordered)
1. Stalker series: one card per show → seasons → episodes + episode picker in the player. BLOCKED: needs a raw `get_ordered_list` sample of a "Title [S01]" row.
2. Guest mode: 1 playlist + 1 portal, hint to register, Home footnote, guest colour, hook card.
3. Logo + name in About / loading / side rail (BrandMark still has old art).
4. Bot v0.4: "Последние", "Донаты" editor, new-user notifications, "Система".
5. New Pages site `yaiplayer` from `site-public` (replace `yai-iptv-player.pages.dev`), QR codes in TV About.
6. Stage 3 (needs Opus): accounts + sync, entry screen (main profile / Guest), SYNC_API on `yai-cloud`.
7. Provider edition (see PROVIDER_EDITION.md): token/login-only build, operator admin, subscription-expiry banner.
8. Google Play: legal pages, listing.

## Manual to-dos for the owner
- Delete the old Telegram post of 2.0.37 (it named hidden sources).
- Test on devices: new splash, portal guide, categories language, reliable MAC.
