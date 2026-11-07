# TASK for Antigravity — Guest mode + BrandMark (items 2 and 3 of docs/STATUS.md)

Read first: HANDOFF.md, docs/STATUS.md, docs/ROADMAP.md. Rules: reply/comments in Russian where user-facing text is Russian; parity (phone + TV share core); every new user-visible string goes to `values/` and `values-ru/` (no hardcoded literals — i18n ratchet in CI: `check_hardcoded_strings.py verify`); release notes only in docs/WHATS_NEW.md, in Russian, user-relevant only, NEVER mention built-in EPG sources. Work on a branch `feature/guest-mode`, open no release by hand; CI makes it.

## Part A — BrandMark (small, do first)
Replace the old art in `BrandMark` with the current YAiPlayer logo (assets in `tools/brand`, `site-public`). Use it on: About screen, loading/splash fallback, side rail header (TV) and the bottom of the drawer (phone). Name text = "YAiPlayer" everywhere (grep for leftover "OwnTV"/"Own TV" in UI strings and fix, except license/credits text).
Done when: both apps show the new mark and name in the 3 places; screenshots in PR description.

## Part B — Guest mode
Behaviour:
- Guest = no account. Limits: 1 playlist (M3U/Xtream) + 1 Stalker portal in total. Adding more shows a bottom sheet (phone: `MobileBottomSheet`, TV: `StagePopup`) with text "Зарегистрируйтесь, чтобы добавлять больше" and OK.
- Home footnote (small muted line at the bottom): "Гостевой режим · ограничения" opening the same sheet.
- Guest colour: use a distinct accent for the guest profile chip (e.g. amber) so it is visible who is active.
- Hook card on Home (dismissible, once per week): "Зарегистрируйтесь — плейлисты синхронизируются между устройствами" (the registration itself is NOT built yet — button just opens the sheet text; do not invent an API).
Tech notes: put limits in `owntv-core` (`GuestPolicy` object + unit tests); UI in both clients. Keep existing profiles working: the current main profile is NOT a guest.
Done when: unit tests for GuestPolicy, both apps build, CI Tests+Build green, short screenshots.

## Do not touch
Stalker series grouping (blocked on sample), accounts/sync (stage 3, Opus), bots.
