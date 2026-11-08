#!/usr/bin/env python3
"""Daily digest for the Telegram channel: one post about the previous day's releases.

Reads the GitHub releases published during the previous Berlin calendar day, merges their
(already user-facing) notes into one list and writes an HTML file for post_release.py
--announce-file. Prints the path of the file, or nothing when there was no release.
"""
import json
import os
import subprocess
import sys
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

REPO = os.environ.get("GITHUB_REPOSITORY", "eai-apps/YAiPlayer")
BERLIN = ZoneInfo("Europe/Berlin")


def releases():
    out = subprocess.run(
        ["gh", "api", f"repos/{REPO}/releases?per_page=60"],
        check=True, capture_output=True, text=True,
    ).stdout
    return json.loads(out)


def main() -> int:
    day = (datetime.now(BERLIN) - timedelta(days=1)).date()
    if len(sys.argv) > 1:
        day = datetime.strptime(sys.argv[1], "%Y-%m-%d").date()
    picked = []
    for r in releases():
        if r.get("draft") or not r.get("published_at"):
            continue
        t = datetime.fromisoformat(r["published_at"].replace("Z", "+00:00")).astimezone(BERLIN)
        if t.date() == day:
            picked.append(r)
    if not picked:
        return 0
    picked.sort(key=lambda r: r["published_at"])
    notes, seen = [], set()
    for r in picked:
        for line in (r.get("body") or "").splitlines():
            line = line.strip()
            if line.startswith(("- ", "• ", "* ")):
                text = line[2:].strip()
                if text and text not in seen:
                    seen.add(text)
                    notes.append(text)
    esc = lambda s: s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    first, last = picked[0]["tag_name"], picked[-1]["tag_name"]
    versions = first if first == last else f"{first} … {last}"
    lines = [
        f"📋 <b>Итоги дня — {day.strftime('%d.%m.%Y')}</b>",
        "",
        f"За день вышло версий: {len(picked)} ({esc(versions)}). Актуальная сейчас — <b>{esc(last)}</b>.",
    ]
    if notes:
        lines += ["", "<b>Что исправлено и добавлено:</b>"] + [f"• {esc(n)}" for n in notes]
    lines += ["", "Обновитесь до последней версии и напишите в чат, если что-то не так 🙌"]
    path = "digest.html"
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
