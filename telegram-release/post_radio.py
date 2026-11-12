#!/usr/bin/env python3
"""Пост про отдельное приложение YAiPlay Radio (сайт + Android) и про радио внутри плеера YAiPlayer.
Rich-сообщение: постер и три скриншота сайта слайдшоу, текст, кнопки «Открыть сайт» и «Скачать приложение».
Запуск: post_radio.py [--dry-run]. Без --dry-run нужны TELEGRAM_BOT_TOKEN и TELEGRAM_CHAT_ID.
Отправляется ТОЛЬКО по слову владельца (workflow radio-post.yml запускается вручную, по умолчанию «пробный»)."""
from __future__ import annotations

import argparse
import html
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import post_release as pr  # noqa: E402
import post_showcase as ps  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..")
SITE_URL = "https://yaiplay-radio.pages.dev/"
APK_URL = "https://github.com/eai-apps/YAiPlay-Radio/releases/download/android-latest/YAiRadio.apk"
CHAT_URL = pr.CHAT_URL
IMAGES = [
    "brand/radio-poster-1280x720.png",
    "docs/radio-post/shot-1.jpg",
    "docs/radio-post/shot-2.jpg",
    "docs/radio-post/shot-3.jpg",
]

TITLE = "📻 YAiPlay Radio — новое отдельное приложение"
INTRO = "Это самостоятельное приложение (и сайт), не связанное с плеером YAiPlayer: радио со всего мира и веб-камеры городов. Всё бесплатно, без рекламы и без регистрации."
FEATURES = [
    ("🌍", "Станции всего мира", "ищите по названию, стране или жанру"),
    ("🎼", "Жанры и подборки", "от «Нулевых» и диско до рока и танцевальной музыки"),
    ("🔥", "Эксклюзив YAi", "официальные станции без рекламы в хорошем качестве"),
    ("❤️", "Избранное и плейлисты", "сердечко у станции — и она всегда под рукой"),
    ("🎧", "Обложки и названия песен", "видно, что играет прямо сейчас"),
    ("📹", "Веб-камеры мира", "города, курорты, горы и МКС в прямом эфире — можно смотреть и слушать радио одновременно"),
]
IN_PLAYER_TITLE = "📺 Отдельно: радио появилось и в IPTV-плеере YAiPlayer"
IN_PLAYER = [
    "Это другое приложение — наш IPTV-плеер для Android TV и телефона. В нём теперь есть встроенное радио: раздел <b>«Радио»</b> в главном меню.",
    "Радио также стоит на экране выбора профиля, рядом с профилями — можно включить музыку, не заходя в профиль.",
]
OUTRO = "Слушайте с телефона или в браузере — выбирайте, как удобнее 💜"
BUTTONS = [
    [{"text": "🌐 Открыть сайт радио", "url": SITE_URL}, {"text": "📥 Скачать приложение", "url": APK_URL}],
    [{"text": "💬 Обсудить в чате", "url": CHAT_URL}],
]


def build() -> tuple[str, str]:
    """(rich_html, classic_html)"""
    n = len(IMAGES)
    slides = "<tg-slideshow>" + "".join(f'<img src="tg://photo?id=s{i}"/>' for i in range(n)) + "</tg-slideshow>"
    rich = [
        slides, f"<h2>{TITLE}</h2>", f"<p>{INTRO}</p>",
        "<h3>Что внутри</h3>",
        "<ul>" + "".join(f"<li>{e} <b>{a}</b>: {b}</li>" for e, a, b in FEATURES) + "</ul>",
        f"<h3>{IN_PLAYER_TITLE}</h3>",
        "<ul>" + "".join(f"<li>{s}</li>" for s in IN_PLAYER) + "</ul>",
        f"<p><b>{OUTRO}</b></p>",
    ]
    classic = [f"<b>{TITLE}</b>", INTRO, "", "<b>Что внутри</b>"]
    classic += [f"{e} <b>{a}</b>: {b}" for e, a, b in FEATURES]
    classic += ["", f"<b>{IN_PLAYER_TITLE}</b>"] + [f"• {s}" for s in IN_PLAYER]
    classic += ["", f"<b>{OUTRO}</b>", "", f'🌐 <a href="{SITE_URL}">Сайт радио</a>', f'📥 <a href="{html.escape(APK_URL)}">Скачать приложение</a>']
    return "".join(rich), "\n".join(classic)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args(argv)
    paths = [os.path.abspath(os.path.join(ROOT, p)) for p in IMAGES]
    missing = [p for p in paths if not os.path.exists(p)]
    rich, classic = build()
    if a.dry_run:
        print(classic)
        print(f"\n[пробный запуск: {len(paths)} картинок, не найдено: {len(missing)}; ничего не отправлено]")
        return 1 if missing else 0
    if missing:
        print("Нет картинок:", ", ".join(os.path.basename(p) for p in missing), file=sys.stderr)
        return 1
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    chat = os.environ.get("TELEGRAM_CHAT_ID", "").strip()
    if not token or not chat:
        print("Telegram не настроен, пост пропущен")
        return 0
    if ps.send_rich(token, chat, paths, rich + ps.buttons_html(BUTTONS), []):
        print("Пост про радио опубликован, кнопки внутри текста")
        return 0
    if ps.send_rich(token, chat, paths, rich, BUTTONS):
        print("Пост про радио опубликован, кнопки под ним")
        return 0
    print("Rich не прошёл — запасной вариант", file=sys.stderr)
    return 0 if pr._send_classic_release(token, chat, classic, paths[0], BUTTONS) else 1


if __name__ == "__main__":
    sys.exit(main())
