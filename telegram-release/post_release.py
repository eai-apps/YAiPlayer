#!/usr/bin/env python3
"""
Автопост новых релизов YAiPlayer в Telegram-канал.
Использует только стандартную библиотеку Python (3.11+).
"""

from __future__ import annotations

import argparse
import html
import json
import os
import re
import sys
import urllib.error
import urllib.request

MAX_ITEMS_COUNT = 12
MAX_POST_LENGTH = 4000
MORE_TEXT = "… полный список по ссылке"
CHAT_URL = "https://t.me/YAiPlayer_Chat"
BOT_URL = "https://t.me/YAiPlayer_bot?start=dl"

# Эмодзи вместо «•»: подбираем по смыслу пункта, по умолчанию — искра.
EMOJI_RULES = [
    (("телегид", "программ", "epg", "эпг"), "📺"),
    (("иконк", "логотип", "значк"), "🖼"),
    (("исправ", "ошибк", "сбой", "вылет", "не закры", "не работ"), "🛠"),
    (("быстр", "скорост", "плавн", "оптимиз", "экономи"), "⚡️"),
    (("портал", "плейлист", "источник", "канал"), "📡"),
    (("звук", "аудио", "видео", "качеств", "картинк"), "🎬"),
    (("поиск", "ищет"), "🔎"),
    (("профил", "аккаунт", "синхрон", "регистрац"), "👤"),
    (("дизайн", "оформлен", "тема", "вид ", "экран", "главн"), "🎨"),
    (("язык", "перевод"), "🌐"),
    (("тв", "приставк", "пульт"), "📺"),
]


def pick_emoji(item: str) -> str:
    low = item.lower()
    for words, emoji in EMOJI_RULES:
        if any(w in low for w in words):
            return emoji
    return "✨"


def decorate(text: str) -> str:
    """Заменяет маркер «• » у пунктов на подходящий эмодзи."""
    out = []
    for line in text.split("\n"):
        if line.startswith("• "):
            line = f"{pick_emoji(line)} {line[2:]}"
        out.append(line)
    return "\n".join(out)


def build_buttons(release_data: dict, chat_id: str = "") -> list[list[dict]]:
    """Кнопки под постом: получить ссылку (через бота), подписаться, чат обсуждения.
    Прямых ссылок на скачивание в постах нет — ссылку выдаёт бот после подписки на канал."""
    rows: list[list[dict]] = [[{"text": "📥 Получить ссылку на приложение для Android-телефона и приставки", "url": BOT_URL}]]
    last: list[dict] = []
    if chat_id.startswith("@"):
        last.append({"text": "🔔 Подписаться", "url": f"https://t.me/{chat_id[1:]}"})
    last.append({"text": "💬 Обсуждение", "url": CHAT_URL})
    rows.append(last)
    return rows


def strip_markdown(text: str) -> str:
    """
    Убирает разметку ссылок и изображений до текста, снимает жирный шрифт и код.
    """
    # Изображения: ![alt](url) -> alt
    text = re.sub(r'!\[([^\]]*)\]\([^)]+\)', r'\1', text)
    # Ссылки с URL: [text](url) -> text
    text = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', text)
    # Ссылки с референсом: [text][ref] -> text
    text = re.sub(r'\[([^\]]+)\]\[[^\]]*\]', r'\1', text)
    # Автоссылки: <https://...> -> https://...
    text = re.sub(r'<(https?://[^>]+)>', r'\1', text)
    # Жирный шрифт и курсив в пределах строки
    text = re.sub(r'\*\*([^*\n]+)\*\*', r'\1', text)
    text = re.sub(r'__([^_\n]+)__', r'\1', text)
    # Встроенный код
    text = re.sub(r'`([^`\n]+)`', r'\1', text)
    return text


def extract_items(body: str, max_items: int = MAX_ITEMS_COUNT) -> tuple[list[str], bool]:
    """
    Извлекает пункты списка изменений из тела релиза (до max_items):
    - превращает маркеры -, * и + в «•»;
    - убирает разметку заголовков (#) и ссылок до чистого текста;
    - экранирует HTML-сущности;
    - возвращает кортеж (список_пунктов, был_ли_список_обрезан).
    """
    if not body or not body.strip():
        return [], False

    normalized = body.replace("\r\n", "\n").replace("\r", "\n")
    raw_lines = normalized.split("\n")
    processed: list[str] = []

    for line in raw_lines:
        line = line.strip()
        if not line:
            continue
        line = strip_markdown(line)
        # Убираем разметку заголовков (#) до текста
        line = re.sub(r'^#{1,6}\s*', '', line)
        # Превращаем маркеры списков (-, *, +) в «• »
        line = re.sub(r'^[-*+]\s+', '• ', line)
        line = line.strip()
        if line:
            processed.append(line)

    has_bullets = any(l.startswith("• ") for l in processed)
    selected: list[str] = []
    truncated = False

    if has_bullets:
        bullet_count = 0
        for line in processed:
            if line.startswith("• "):
                if bullet_count >= max_items:
                    truncated = True
                    break
                bullet_count += 1
                selected.append(line)
            else:
                # Вспомогательный заголовок / текст внутри списка
                if bullet_count < max_items:
                    selected.append(line)
                else:
                    truncated = True
        # Если после обрезки в конце остался заголовок без пунктов, убираем его
        while selected and not selected[-1].startswith("• "):
            selected.pop()
    else:
        if len(processed) > max_items:
            selected = processed[:max_items]
            truncated = True
        else:
            selected = processed

    # Экранируем каждый пункт для безопасного вывода в HTML
    escaped = [html.escape(item, quote=False) for item in selected]
    return escaped, truncated


def format_release_post(
    release_data: dict,
    channel_username: str = "",
    max_items: int = MAX_ITEMS_COUNT,
    max_length: int = MAX_POST_LENGTH,
) -> str:
    """
    Форматирует текст сообщения для Telegram:
    - Заголовок «YAiPlayer <версия>»
    - Список изменений (до max_items)
    - Ссылка на скачивание
    - Ссылка на личные сообщения канала (при наличии channel_username)
    - Ограничение длины поста max_length с обрезкой по границе пункта
    """
    tag_name = release_data.get("tag_name") or release_data.get("name") or ""
    tag_name = tag_name.strip()
    escaped_tag = html.escape(tag_name, quote=False)
    header = f"🚀 <b>YAiPlayer {escaped_tag}</b>" if escaped_tag else "🚀 <b>YAiPlayer</b>"

    footer = f'💬 Обсуждение и баги — <a href="{CHAT_URL}">наш чат</a>'

    body = release_data.get("body") or ""
    items, was_truncated = extract_items(body, max_items=max_items)

    def assemble(current_items: list[str], show_more: bool) -> str:
        parts = [header]
        body_lines = list(current_items)
        if show_more:
            body_lines.append(MORE_TEXT)
        if body_lines:
            parts.append("\n".join(body_lines))
        if footer:
            parts.append(footer)
        return "\n\n".join(parts)

    current_items = list(items)
    show_more = was_truncated

    # Если длина превышает max_length, аккуратно обрезаем список по границе пункта
    if len(assemble(current_items, show_more)) > max_length:
        show_more = True
        while current_items and len(assemble(current_items, show_more=True)) > max_length:
            current_items.pop()
            # Убираем заголовок секции, если под ним не осталось пунктов
            while (
                current_items
                and any(i.startswith("• ") for i in current_items)
                and not current_items[-1].startswith("• ")
            ):
                current_items.pop()

    post = decorate(assemble(current_items, show_more))
    if len(post) > max_length:
        post = post[:max_length]

    return post


def send_telegram_message(bot_token: str, chat_id: str, text: str, buttons: list | None = None) -> bool:
    """
    Отправляет сообщение в Telegram через HTTP API.
    Гарантированно не выводит токен в логи или сообщения об ошибках.
    """
    api_url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
    payload = {
        "chat_id": chat_id,
        "text": text,
        "parse_mode": "HTML",
        "disable_web_page_preview": True,
    }
    if buttons:
        payload["reply_markup"] = {"inline_keyboard": buttons}
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        api_url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            resp_body = json.loads(resp.read().decode("utf-8"))
            if not resp_body.get("ok"):
                desc = resp_body.get("description", "Неизвестная ошибка")
                print(f"Ошибка Telegram API: {desc}", file=sys.stderr)
                return False
            print("Пост успешно опубликован в Telegram")
            return True
    except urllib.error.HTTPError as e:
        # ВАЖНО: не выводить str(e) или e.url, так как URL содержит токен бота!
        error_desc = "Неизвестная ошибка HTTP"
        try:
            err_data = json.loads(e.read().decode("utf-8"))
            error_desc = err_data.get("description", str(e.reason))
        except Exception:
            error_desc = str(e.reason)
        print(f"Ошибка HTTP {e.code}: {error_desc}", file=sys.stderr)
        return False
    except urllib.error.URLError as e:
        print(f"Сетевая ошибка при отправке в Telegram: {e.reason}", file=sys.stderr)
        return False
    except Exception as e:
        print(f"Ошибка при отправке в Telegram: {type(e).__name__}", file=sys.stderr)
        return False


CAPTION_LIMIT = 1024  # лимит Telegram на подпись к фото


def _error_text(e: Exception) -> str:
    """Описание ошибки без токена бота (он есть в URL запроса)."""
    if isinstance(e, urllib.error.HTTPError):
        try:
            return json.loads(e.read().decode("utf-8")).get("description", str(e.reason))
        except Exception:
            return str(e.reason)
    if isinstance(e, urllib.error.URLError):
        return str(e.reason)
    return type(e).__name__


def send_telegram_photo(bot_token: str, chat_id: str, image_path: str, caption: str, buttons: list | None = None) -> bool:
    """Отправляет картинку с подписью (multipart). Токен в логи не попадает."""
    try:
        with open(image_path, "rb") as f:
            image = f.read()
    except OSError:
        print("Картинка для поста не найдена", file=sys.stderr)
        return False
    boundary = "----yaiplayer" + os.urandom(8).hex()
    parts = []
    fields = [("chat_id", chat_id), ("caption", caption), ("parse_mode", "HTML")]
    if buttons:
        fields.append(("reply_markup", json.dumps({"inline_keyboard": buttons})))
    for name, value in fields:
        parts.append(
            f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode("utf-8")
        )
    parts.append(
        (f'--{boundary}\r\nContent-Disposition: form-data; name="photo"; filename="release.png"\r\n'
         "Content-Type: image/png\r\n\r\n").encode("utf-8")
        + image + b"\r\n"
    )
    parts.append(f"--{boundary}--\r\n".encode("utf-8"))
    req = urllib.request.Request(
        f"https://api.telegram.org/bot{bot_token}/sendPhoto",
        data=b"".join(parts),
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            if json.loads(resp.read().decode("utf-8")).get("ok"):
                print("Картинка с подписью опубликована в Telegram")
                return True
            print("Telegram не принял картинку", file=sys.stderr)
            return False
    except Exception as e:
        print(f"Ошибка отправки картинки: {_error_text(e)}", file=sys.stderr)
        return False


def send_release_post(bot_token: str, chat_id: str, text: str, image_path: str | None, buttons: list | None = None) -> bool:
    """Картинка + текст. Влезает в подпись — одним сообщением; иначе подпись = заголовок, текст следом."""
    if image_path and os.path.exists(image_path):
        if len(text) <= CAPTION_LIMIT:
            if send_telegram_photo(bot_token, chat_id, image_path, text, buttons):
                return True
        else:
            header, _, rest = text.partition("\n\n")
            if send_telegram_photo(bot_token, chat_id, image_path, header):
                return send_telegram_message(bot_token, chat_id, rest or text, buttons)
        print("Картинка не ушла, отправляю только текст", file=sys.stderr)
    return send_telegram_message(bot_token, chat_id, text, buttons)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Автопост релизов YAiPlayer в Telegram")
    parser.add_argument(
        "--release-json",
        default="",
        help="Путь к JSON-файлу с данными релиза GitHub API",
    )
    parser.add_argument(
        "--image",
        default="",
        help="Путь к PNG-картинке, которая идёт в начале поста (необязательно)",
    )
    parser.add_argument(
        "--announce-file",
        default="",
        help="Разовое объявление: текст (HTML) из файла уходит в канал с кнопками «Подписаться» и «Обсуждение»",
    )
    parser.add_argument(
        "--with-downloads",
        action="store_true",
        help="К объявлению добавить кнопки скачивания приложения",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Только напечатать итоговый текст сообщения без отправки в Telegram",
    )

    args = parser.parse_args(argv)

    if args.announce_file:
        with open(args.announce_file, "r", encoding="utf-8") as f:
            text = f.read().strip()
        if args.dry_run:
            print(text)
            return 0
        bot_token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
        chat_id = os.environ.get("TELEGRAM_CHAT_ID", "").strip()
        if not bot_token or not chat_id:
            print("Telegram не настроен, пост пропущен")
            return 0
        row = ([{"text": "🔔 Подписаться", "url": f"https://t.me/{chat_id[1:]}"}] if chat_id.startswith("@") else []) + [
            {"text": "💬 Обсуждение", "url": CHAT_URL}
        ]
        rows = [row]
        if args.with_downloads:
            rows = [[{"text": "📥 Получить ссылку на приложение для Android-телефона и приставки", "url": BOT_URL}]] + rows
        return 0 if send_release_post(bot_token, chat_id, text, args.image or None, rows) else 1

    if not args.release_json:
        parser.error("нужен --release-json или --announce-file")

    try:
        with open(args.release_json, "r", encoding="utf-8") as f:
            release_data = json.load(f)
    except FileNotFoundError:
        print(f"Файл {args.release_json} не найден", file=sys.stderr)
        return 1
    except json.JSONDecodeError as e:
        print(f"Ошибка чтения JSON из {args.release_json}: {e}", file=sys.stderr)
        return 1

    # Пропуск черновиков и пре-релизов
    if release_data.get("draft") is True or release_data.get("prerelease") is True:
        print("Черновик или пре-релиз, публикация пропущена")
        return 0

    channel_username = os.environ.get("TELEGRAM_CHANNEL_USERNAME", "")
    post_text = format_release_post(release_data, channel_username=channel_username)

    # Режим dry-run: только печать текста
    if args.dry_run:
        print(post_text)
        return 0

    # Проверка наличия токена и идентификатора чата
    bot_token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    chat_id = os.environ.get("TELEGRAM_CHAT_ID", "").strip()

    if not bot_token or not chat_id:
        print("Telegram не настроен, пост пропущен")
        return 0

    success = send_release_post(bot_token, chat_id, post_text, args.image or None, build_buttons(release_data, chat_id))
    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main())
