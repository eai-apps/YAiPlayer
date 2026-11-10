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


SKIP = ("ускорь телевизор",)  # hidden / not working features are never announced
THEMES = (
    (("внутренние улучшения",), None),
    (("смещение программы", "сдвинут"), "Сдвиг телепрограммы"),
    (("архив", "вчера в эфире"), "Архив"),
    (("телепрограмм", "программ передач", "epg", "эпг"), "Телепрограмма"),
    (("заставк",), "Заставка"),
    (("лёгкий режим", "легкий режим", "слабых устройств"), "Лёгкий режим"),
    (("ускорь телевизор",), "Ускорь телевизор"),
    (("категори",), "Категории"),
    (("быстрый старт",), "Быстрый старт"),
    (("быстрые действия", "обновить плейлист"), "Быстрые действия"),
    (("избранн", "сердечк"), "Избранное"),
    (("сериал",), "Сериалы"),
    (("stalker", "портал", "mac-адрес"), "Порталы"),
    (("прямой эфир", "mpv", "exoplayer"), "Прямой эфир"),
    (("главный экран", "на главном"), "Главный экран"),
    (("поиск", "ищет"), "Поиск"),
    (("профил", "перенос настроек"), "Профили"),
    (("пульт",), "Пульт"),
)
MAX_PER_BLOCK = 5   # themes shown per block; the rest go under the expandable "minor" block
ESSENCE_MAX = 110
BOT_URL = "https://t.me/YAiPlayer_App_bot?start=dl"
CHAT_URL = "https://t.me/YAiPlayer_Chat"
IMAGE = "brand/telegram-banner-1280x720.png"


def theme_of(text: str):
    """(label, matched). label None = skip ('внутренние улучшения')."""
    t = text.lower()
    for words, label in THEMES:
        if any(w in t for w in words):
            return label, True
    head, sep, _ = text.partition(":")
    if sep and 0 < len(head) <= 40 and len(head.split()) <= 4:
        return head.strip(" «»\"'").capitalize(), True
    return "", False


def essence(text: str) -> str:
    """Short one-line gist: drop the 'Theme:' prefix, keep the first clause, cut at a natural break."""
    head, sep, rest = text.partition(":")
    body = rest.strip() if sep and len(head) <= 40 else text
    body = re.split(r"(?<=[.!?])\s", body, maxsplit=1)[0].strip().rstrip(".")
    m = re.search(r"[:;]\s", body[25:])  # first clause only
    if m:
        body = body[:25 + m.start()]
    if len(body) > ESSENCE_MAX:
        cut = body[:ESSENCE_MAX]
        comma = cut.rfind(", ")
        body = (cut[:comma] if comma >= 40 else cut.rsplit(" ", 1)[0]).rstrip(",;: ")
    return (body[:1].lower() + body[1:]) if body else body


def tag_num(tag: str):
    return tuple(int(x) for x in re.findall(r"\d+", tag))


def build(picked, start, end):
    """Returns (rich_html, classic_html) or None when there is nothing to say."""
    themes = {}          # label -> {"kind", "text", "tag"}  (one line per theme, the latest wording wins)
    minor = []           # bullets without a clear theme
    per_version = []     # (tag, label)
    internal = 0
    seen = []
    for r in picked:
        tag = r["tag_name"]
        first = None
        for line in (r.get("body") or "").splitlines():
            line = line.strip()
            if not line.startswith(("- ", "• ", "* ")):
                continue
            text = line[2:].strip()
            if not text:
                continue
            if any(w in text.lower() for w in SKIP):
                continue
            label, ok = theme_of(text)
            if label is None:
                internal += 1
                first = first or "Внутренние улучшения"
                continue
            first = first or (label if ok else None)
            if ok:
                k = kind(text)
                cur = themes.get(label)
                # new beats fix inside one theme; the later version replaces the wording
                kk = "new" if (cur and cur["kind"] == "new") or k != "fix" else "fix"
                themes[label] = {"kind": kk, "text": essence(text), "tag": tag}
            elif not is_dup(text, seen):
                seen.append(norm(text))
                minor.append(essence(text))
        per_version.append((tag, first or "Мелкие правки"))
    if not themes and not minor and not internal:
        return None
    esc = lambda x: x.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    first_tag, last_tag = picked[0]["tag_name"], picked[-1]["tag_name"]
    n = len(picked)
    newest = [lb for lb, v in sorted(themes.items(), key=lambda kv: tag_num(kv[1]["tag"]), reverse=True)]
    top = [lb for lb in newest if themes[lb]["kind"] == "new"][:3] or newest[:3]
    if n == 1:
        summary = f"Вышла новая версия. Сейчас актуальная: <b>{esc(last_tag)}</b>."
    else:
        summary = f"Вышли новые версии. Сейчас актуальная: <b>{esc(last_tag)}</b>."
    if top:
        summary += " Главное: <i>" + ", ".join(esc(t) for t in top) + "</i>."

    blocks = {"new": [], "fix": []}
    for label, v in sorted(themes.items(), key=lambda kv: tag_num(kv[1]["tag"]), reverse=True):  # newest first
        blocks[v["kind"]].append((label, v["text"]))
    hidden = []
    for key in ("new", "fix"):
        hidden += [(lb, tx) for lb, tx in blocks[key][MAX_PER_BLOCK:]]
        blocks[key] = blocks[key][:MAX_PER_BLOCK]
    minor_items = hidden + [("", m) for m in minor]
    if internal:
        minor_items.append(("", "внутренние улучшения и исправления"))
    titles = {"new": "🆕", "fix": "🛠"}
    names = {"new": "Новое", "fix": "Исправлено"}

    def li(label, text):
        return f"<b>{esc(label)}</b> — <i>{esc(text)}</i>" if label else f"<i>{esc(text)}</i>"

    rows = "".join(f"<tr><td><b>{esc(t)}</b></td><td>{esc(lb)}</td></tr>" for t, lb in reversed(per_version[-8:]))
    if n > 8:
        rows += f"<tr><td>…</td><td><i>и ещё {n - 8} более ранних</i></td></tr>"
    rich = ["<h2>📋 Дайджест</h2>", f"<p>{summary}</p>",
            f"<table><tr><td><b>Версия</b></td><td><b>Что вышло</b></td></tr>{rows}</table>"]
    classic = ["📋 <b>Дайджест</b>", "", summary]
    for key in ("new", "fix"):
        if blocks[key]:
            rich.append(f"<h3>{titles[key]} {names[key]}</h3>")
            rich.append("<ul>" + "".join(f"<li>{li(lb, tx)}</li>" for lb, tx in blocks[key]) + "</ul>")
            classic += ["", f"{titles[key]} <b>{names[key]}</b>"] + [f"• {li(lb, tx)}" for lb, tx in blocks[key]]
    if minor_items:
        cnt = len(minor_items)
        rich.append(f"<details><summary>Ещё {cnt} мелких правок</summary><ul>" + "".join(f"<li>{li(lb, tx)}</li>" for lb, tx in minor_items) + "</ul></details>")
        classic += ["", "<blockquote expandable>🔹 <b>Мелкие правки</b>\n" + "\n".join(f"• {li(lb, tx)}" for lb, tx in minor_items) + "</blockquote>"]
    rich.append("<p><i>Обновитесь до последней версии и напишите в чат, если что-то не так</i> 🙌</p>")
    classic += ["", "<i>Обновитесь до последней версии и напишите в чат, если что-то не так</i> 🙌"]
    return "".join(rich), "\n".join(classic)


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
    picked.sort(key=lambda r: tag_num(r["tag_name"]))
    res = build(picked, start, end)
    if not res:
        return 0
    rich, classic = res
    with open("digest.rich.html", "w", encoding="utf-8") as f:
        f.write(rich)
    with open("digest.html", "w", encoding="utf-8") as f:
        f.write(classic)
    print("digest.html")
    return 0


if __name__ == "__main__":
    sys.exit(main())
