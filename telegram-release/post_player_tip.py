"""«А вы знали?» про плеер — один пост в день (12:07 по Берлину), по порядку из docs/tips/player-tips.json.

    python3 telegram-release/post_player_tip.py --state state/player-tips.json [--dry-run]

Картинка одна на все посты: brand/tip-poster-1280x720.png. Номер следующей фишки хранится в --state
(ветка bot-state, её сохраняет workflow daily-posts.yml).
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from post_release import BOT_URL, CHAT_URL, send_telegram_photo  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
TIPS = ROOT / "docs" / "tips" / "player-tips.json"
POSTER = ROOT / "brand" / "tip-poster-1280x720.png"
BUTTONS = [[{"text": "📥 Скачать YAiPlayer", "url": BOT_URL}], [{"text": "💬 Чат и помощь", "url": CHAT_URL}]]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--state", required=True)
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    tips = json.loads(TIPS.read_text(encoding="utf-8"))["tips"]
    state_path = Path(a.state)
    state = json.loads(state_path.read_text(encoding="utf-8")) if state_path.exists() else {}
    i = int(state.get("index", 0)) % len(tips)
    tip = tips[i]
    print(f"Фишка {i + 1}/{len(tips)}: {tip['id']}")
    if a.dry_run:
        print(tip["text"])
        return 0
    token, chat = os.environ.get("TELEGRAM_BOT_TOKEN"), os.environ.get("TELEGRAM_CHAT_ID")
    if not token or not chat:
        print("Нет TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID")
        return 1
    if not send_telegram_photo(token, chat, str(POSTER), tip["text"], BUTTONS):
        print("Пост не ушёл")
        return 1
    state["index"] = i + 1
    state_path.parent.mkdir(parents=True, exist_ok=True)
    state_path.write_text(json.dumps(state, ensure_ascii=False, indent=1), encoding="utf-8")
    print("Опубликовано")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
