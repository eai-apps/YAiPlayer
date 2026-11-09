#!/usr/bin/env python3
"""Daily digest for the Telegram channel: one post at 09:00 Berlin about everything released
since 09:00 the day before.

Reads the GitHub releases of that 24-hour window, merges their (already user-facing) notes into
one de-duplicated list grouped by kind, and writes an HTML file for post_release.py
--announce-file. Prints the path of the file, or nothing when there was no release (no post then).

Usage: daily_digest.py [YYYY-MM-DD]   # the window ends at 09:00 Berlin on that date (default: today)
"""
import json
import os
import re
import subprocess
import sys
from datetime import datetime, time, timedelta
from difflib import SequenceMatcher
from zoneinfo import ZoneInfo

REPO = os.environ.get("GITHUB_REPOSITORY", "eai-apps/YAiPlayer")
BERLIN = ZoneInfo("Europe/Berlin")

NEW = ("добав", "нов", "появил", "теперь можно", "поддержк")
FIX = ("исправ", "починен", "устран", "больше не", "не вылета", "не зависа", "ошибк")


def releases():
    out = subprocess.run(
        ["gh", "api", f"repos/{REPO}/releases?per_page=60"],
        check=True, capture_output=True, text=True,
    ).stdout
    return json.loads(out)


def norm(s: str) -> str:
    return re.sub(r"[^\w]+", " ", s.lower()).strip()


def is_dup(a: str, seen: list) -> bool:
    n = norm(a)
    return any(n == m or SequenceMatcher(None, n, m).ratio() > 0.85 for m in seen)


def kind(text: str) -> str:
    t = text.lower()
    if any(k in t for k in FIX):
        return "fix"
    if any(k in t for k in NEW):
        return "new"
    return "better"


MAX_PER_GROUP = 4  # a short post is read; a long one is scrolled past


def main() -> int:
    end_date = datetime.strptime(sys.argv[1], "%Y-%m-%d").date() if len(sys.argv) > 1 and sys.argv[1] else datetime.now(BERLIN).date()
    end = datetime.combine(end_date, time(9, 0), tzinfo=BERLIN)
    start = end - timedelta(days=1)
    picked = []
    for r in releases():
        if r.get("draft") or not r.get("published_at"):
            continue
        t = datetime.fromisoformat(r["published_at"].replace("Z", "+00:00")).astimezone(BERLIN)
        if start <= t < end:
            picked.append(r)
    if not picked:
        return 0
    picked.sort(key=lambda r: r["published_at"])
    groups = {"new": [], "better": [], "fix": []}
    seen = []
    for r in picked:
        for line in (r.get("body") or "").splitlines():
            line = line.strip()
            if line.startswith(("- ", "• ", "* ")):
                text = line[2:].strip()
                if text and not is_dup(text, seen):
                    seen.append(norm(text))
                    groups[kind(text)].append(text)
    if not any(groups.values()):
        return 0
    esc = lambda s: s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    last = picked[-1]["tag_name"]
    lines = [
        f"📋 <b>Итоги за сутки</b> · <i>{start.strftime('%d.%m')} → {end.strftime('%d.%m.%Y')}</i>",
        "",
        f"📦 Актуальная версия: <b>{esc(last)}</b>",
    ]
    titles = (("new", "🆕 <b>Новое</b>"), ("better", "✨ <b>Стало лучше</b>"), ("fix", "🛠 <b>Исправлено</b>"))
    for key, title in titles:
        if groups[key]:
            shown = groups[key][:MAX_PER_GROUP]
            more = len(groups[key]) - len(shown)
            lines += ["", title] + [f"• {esc(n[:160])}" for n in shown]
            if more > 0:
                lines.append(f"<i>…и ещё {more}</i>")
    lines += ["", "<i>Обновитесь до последней версии и напишите в чат, если что-то не так</i> 🙌"]
    path = "digest.html"
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
