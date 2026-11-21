#!/usr/bin/env python3
"""Посты-витрины в Telegram (владелец 21.11, п.84–88): rich-сообщение — слайдшоу постерных скриншотов,
крупные заголовки, раскрывающиеся блоки, кнопки внутри поста.

Виды (папка docs/posts/<вид>: post.html + slide-NN.jpg, слайды рисует tools/brand/make_posts.py):
  tv     — плеер на приставке Android TV
  phone  — приложение для Android-телефона
  site   — новый сайт, регистрация и синхронизация
  ad     — большой рекламный пост для пересылки в другие каналы (все возможности)
Расписание — .github/workflows/weekly-posts.yml.
Запуск: post_rich.py KIND [--dry-run]"""
from __future__ import annotations

import argparse
import glob
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import post_release as pr  # noqa: E402
import post_showcase as ps  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..")
SITE = "https://yaiplayer.pages.dev/"
CHANNEL = "https://t.me/YAiPlayer"
BOT = pr.BOT_URL
CHAT = pr.CHAT_URL

BUTTONS = {
    "tv": [[{"text": "📥 Скачать YAiPlayer", "url": BOT}], [{"text": "🌐 Сайт", "url": SITE}, {"text": "💬 Чат", "url": CHAT}]],
    "phone": [[{"text": "📥 Скачать YAiPlayer", "url": BOT}], [{"text": "🌐 Сайт", "url": SITE}, {"text": "💬 Чат", "url": CHAT}]],
    "site": [[{"text": "🌐 Открыть сайт", "url": SITE}], [{"text": "🔐 Зарегистрироваться", "url": SITE + "#/register"}, {"text": "📥 Скачать", "url": BOT}]],
    # The ad post is forwarded to other channels: everything a newcomer needs is in its own buttons.
    "ad": [[{"text": "📥 Скачать YAiPlayer", "url": BOT}], [{"text": "🌐 Сайт", "url": SITE}, {"text": "🔔 Канал", "url": CHANNEL}, {"text": "💬 Чат", "url": CHAT}]],
}
FALLBACK_TITLE = {
    "tv": "📺 <b>YAiPlayer на приставке</b>",
    "phone": "📱 <b>YAiPlayer на телефоне</b>",
    "site": "🌐 <b>Новый сайт YAiPlayer</b>",
    "ad": "📺 <b>YAiPlayer — всё ваше ТВ в одном плеере</b>",
}


def page(kind: str) -> tuple[list[str], str]:
    folder = os.path.join(ROOT, "docs", "posts", kind)
    slides = sorted(glob.glob(os.path.join(folder, "slide-*.jpg")))[:10]
    with open(os.path.join(folder, "post.html"), encoding="utf-8") as f:
        body = f.read().strip().replace("\n", "")
    show = "<tg-slideshow>" + "".join(f'<img src="tg://photo?id=s{i}"/>' for i in range(len(slides))) + "</tg-slideshow>"
    return slides, (show if len(slides) > 1 else '<img src="tg://photo?id=s0"/>') + body


def classic(html_text: str) -> str:
    """Rich HTML -> plain Telegram HTML, in case Telegram does not take the rich message."""
    import re
    t = html_text
    t = re.sub(r"<tg-slideshow>.*?</tg-slideshow>|<img[^>]*>", "", t)
    t = re.sub(r"<h[23]>(.*?)</h[23]>", r"\n<b>\1</b>\n", t)
    t = re.sub(r"<summary>(.*?)</summary>", r"\n<b>\1</b>\n", t)
    t = re.sub(r"<li>", "• ", t)
    t = re.sub(r"</li>|</p>", "\n", t)
    t = re.sub(r"</?(ul|details|p)>", "", t)
    return re.sub(r"\n{3,}", "\n\n", t).strip()


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("kind", choices=sorted(BUTTONS))
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args(argv)
    slides, html_text = page(a.kind)
    buttons = BUTTONS[a.kind]
    if a.dry_run:
        print(f"{a.kind}: {len(slides)} слайдов, {len(html_text)} знаков HTML")
        print(html_text[:600])
        return 0
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    chat = os.environ.get("TELEGRAM_CHAT_ID", "").strip()
    if not token or not chat:
        print("Telegram не настроен, пост пропущен")
        return 0
    if ps.send_rich(token, chat, slides, html_text + ps.buttons_html(buttons), []):
        print("Пост опубликован, кнопки внутри поста")
        return 0
    if ps.send_rich(token, chat, slides, html_text, buttons):
        print("Пост опубликован, кнопки под постом")
        return 0
    print("Rich не прошёл — альбом и текст отдельно", file=sys.stderr)
    ok = ps.send_album(token, chat, slides, FALLBACK_TITLE[a.kind])
    ok = ok and pr.send_telegram_message(token, chat, classic(html_text)[:4000], buttons)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
