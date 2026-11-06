# Provider edition & backlog (ideas from the owner, 2026-11-06)

Not implemented yet — design notes so any assistant (Claude / Codex / Antigravity) can continue.

## 1. Provider edition (separate build, same core)
- Separate app flavour for a single operator (not the Play Store build); name/branding customisable, sellable to other operators.
- Start screen: two entries only — **Войти в плейлист** (user types just a token from the operator) and **Войти в портал Stalker** (user types login + password).
- Portal / playlist server addresses are NOT typed by the user: they come from the operator's server (admin panel), so a changed address reaches every logged-in user.
- Portal login flow: «Сгенерировать надёжный MAC адрес» → «Заполнить серийный номер и ID по MAC» → Войти.
- Users cannot add their own playlists; they MAY add their own EPG (or use the built-in guide).
- Core features keep flowing into the edition, but each feature is gated by a flavour flag so the operator's edition stays limited to what he bought.
- Operator admin panel (own server, same stack as our site): set playlist-server URL and portal URL, see how many users activated by which key/token.
- Needs: accounts/sync backend (stage 3, Opus) — do after the account design.

## 2. Subscription notice
- Operator wants a banner: «Уважаемый абонент! Срок вашей оплаты истекает через N дней. Просим пополнить лицевой счёт во избежание отключения услуг.» shown when ≤ 1 (configurable) day remain.
- Source of the date: portal `account_info` / profile expiry (already read as optional account info) or the operator panel; text editable by the operator.

## 3. Telegram bot
- Download-gate bot: `telegram-bot/` (Cloudflare Worker). Later: universal info bot (menus, donations, other channels), link a Telegram account to the player account, send playlist/portal from Telegram to a device.

## 4. Stalker series (needs a portal sample)
- The user's portal lists every season as a separate VOD row named `Title [S01]`; wanted: one card per series → seasons → episodes, and an episode picker inside the player. Needs a raw `get_ordered_list` sample of such a row (is_series / series[] fields) to implement correctly.
