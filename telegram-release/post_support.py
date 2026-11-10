#!/usr/bin/env python3
"""Пост «Как сообщить об ошибке или предложить функцию»: выходит раз в 3 дня (workflow support-post.yml).
Rich-сообщение (картинка, заголовки, список, кнопки), запасной вариант обычным текстом.
Запуск: post_support.py [--dry-run] [--day YYYY-MM-DD]. Правила владельца: HANDOFF.md, раздел про Telegram."""
from __future__ import annotations

import argparse
import datetime
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import post_release as pr  # noqa: E402

SUPPORT_URL = "https://t.me/YAiPlayer?direct"
CHAT_URL = pr.CHAT_URL
IMAGE = os.path.join(os.path.dirname(__file__), "..", "brand", "support-poster-1280x720.png")

# Заголовок меняется от поста к посту (раз в 3 дня), чтобы лента не казалась повтором.
TITLES = [
    "🛠 Нашли ошибку? Хотите новую функцию? Напишите нам!",
    "🐞 Что-то не работает? Расскажите — починим!",
    "💡 Есть идея для плеера? Мы читаем каждое сообщение",
]

INTRO = "Мы делаем YAiPlayer вместе с вами, и каждое ваше сообщение помогает плееру становиться лучше 🙌"
CHECKLIST = [
    ("1️⃣", "Устройство", "на котором смотрите (например, Xiaomi Mi Box, телефон Samsung)"),
    ("2️⃣", "Какое приложение", "для ТВ или мобильное, и версия (Настройки → О приложении)"),
    ("3️⃣", "Откуда плейлист", "оператор или Stalker-портал"),
    ("4️⃣", "Проверяли в других плеерах?", "повторяется ли ошибка и в каких именно"),
    ("5️⃣", "Фото или скриншот", "экрана с проблемой"),
    ("6️⃣", "Журнал (лог)", "из приложения: самое полезное для нас"),
]
TV_STEPS = [
    "Откройте <b>Настройки → Журнал ошибок</b>.",
    "Нажмите <b>«Экспорт журнала»</b>. На экране появится QR-код.",
    "Наведите камеру телефона на QR-код (телефон в той же Wi-Fi сети). Файл скачается в телефон.",
    "Откройте файл и отправьте в Telegram: <b>Поделиться → Telegram → поддержка YAiPlayer</b>.",
]
PHONE_STEPS = [
    "Откройте <b>Настройки → Приложение → «Экспорт журнала»</b>.",
    "В меню «Поделиться» выберите <b>Telegram</b> и отправьте нам.",
]
PRIVACY = "🔒 Пароли, логины, токены и адреса порталов в журнале скрыты автоматически. Ничего не отправляется без вашего действия."
THANKS = "Спасибо, что помогаете делать плеер лучше! 💜"
BUTTONS = [[{"text": "✉️ Написать в поддержку", "url": SUPPORT_URL}, {"text": "💬 Открыть чат", "url": CHAT_URL}]]


def title_for(day: datetime.date) -> str:
    return TITLES[(day.toordinal() // 3) % len(TITLES)]


def build(day: datetime.date) -> tuple[str, str]:
    """(rich_html, classic_html)"""
    t = title_for(day)
    rich = [
        f"<h2>{t}</h2>", f"<p>{INTRO}</p>",
        "<h3>📩 Куда писать</h3>",
        f'<ul><li>👉 Техподдержка: <a href="{SUPPORT_URL}">t.me/YAiPlayer?direct</a></li>'
        f'<li>💬 Общий чат: <a href="{CHAT_URL}">t.me/YAiPlayer_Chat</a></li></ul>',
        "<h3>📝 Что указать в сообщении</h3>", "<p>Так мы найдём причину быстрее:</p>",
        "<ul>" + "".join(f"<li>{n} <b>{a}</b>: {b}</li>" for n, a, b in CHECKLIST) + "</ul>",
        "<h3>📋 Как быстро снять журнал</h3>",
        "<p>📺 <b>На ТВ-приставке:</b></p>",
        "<ul>" + "".join(f"<li>{i}. {s}</li>" for i, s in enumerate(TV_STEPS, 1)) + "</ul>",
        "<p>📱 <b>На телефоне:</b></p>",
        "<ul>" + "".join(f"<li>{i}. {s}</li>" for i, s in enumerate(PHONE_STEPS, 1)) + "</ul>",
        f"<p>{PRIVACY}</p>", f"<p><b>{THANKS}</b></p>",
    ]
    classic = [f"<b>{t}</b>", INTRO, "",
               "📩 <b>Куда писать</b>", f'👉 Техподдержка: <a href="{SUPPORT_URL}">t.me/YAiPlayer?direct</a>',
               f'💬 Чат: <a href="{CHAT_URL}">t.me/YAiPlayer_Chat</a>', "",
               "📝 <b>Что указать в сообщении</b>"]
    classic += [f"{n} <b>{a}</b>: {b}" for n, a, b in CHECKLIST]
    classic += ["", "📋 <b>Как быстро снять журнал</b>", "📺 <b>ТВ-приставка:</b>"]
    classic += [f"{i}. {s}" for i, s in enumerate(TV_STEPS, 1)]
    classic += ["📱 <b>Телефон:</b>"] + [f"{i}. {s}" for i, s in enumerate(PHONE_STEPS, 1)]
    classic += ["", PRIVACY, "", f"<b>{THANKS}</b>"]
    return "".join(rich), "\n".join(classic)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--day", default="")
    a = ap.parse_args(argv)
    day = datetime.date.fromisoformat(a.day) if a.day else datetime.date.today()
    rich, classic = build(day)
    if a.dry_run:
        print(classic)
        return 0
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    chat = os.environ.get("TELEGRAM_CHAT_ID", "").strip()
    if not token or not chat:
        print("Telegram не настроен, пост пропущен")
        return 0
    return 0 if pr.send_digest(token, chat, rich, classic, os.path.abspath(IMAGE), BUTTONS) else 1


if __name__ == "__main__":
    sys.exit(main())
