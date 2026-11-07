#!/usr/bin/env python3
"""Недельный пост-презентация мобильного приложения: альбом постерных скриншотов + текст с кнопками.
Использует только стандартную библиотеку. Скриншоты рисует tools/brand/make_showcase.py,
текст — docs/showcase/post.html.
Запуск: post_showcase.py --images DIR --text docs/showcase/post.html [--dry-run]"""
from __future__ import annotations

import argparse
import glob
import json
import os
import sys
import urllib.error
import urllib.request
import uuid

sys.path.insert(0, os.path.dirname(__file__))
from post_release import build_buttons, send_telegram_message  # noqa: E402


def send_album(bot_token: str, chat_id: str, paths: list[str], caption: str) -> bool:
    boundary = uuid.uuid4().hex
    media, parts = [], []
    for i, p in enumerate(paths):
        name = f"photo{i}"
        item = {"type": "photo", "media": f"attach://{name}"}
        if i == 0:
            item.update(caption=caption, parse_mode="HTML")
        media.append(item)
        with open(p, "rb") as f:
            data = f.read()
        parts.append((name, os.path.basename(p), data))
    body = b""

    def field(k: str, v: str) -> bytes:
        return f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()

    body += field("chat_id", chat_id) + field("media", json.dumps(media, ensure_ascii=False))
    for name, fn, data in parts:
        body += (f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{fn}"\r\n'
                 "Content-Type: image/jpeg\r\n\r\n").encode() + data + b"\r\n"
    body += f"--{boundary}--\r\n".encode()
    req = urllib.request.Request(
        f"https://api.telegram.org/bot{bot_token}/sendMediaGroup", data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return bool(json.loads(resp.read().decode("utf-8")).get("ok"))
    except Exception as e:  # токен в тексте ошибки не печатаем
        print(f"Ошибка отправки альбома: {type(e).__name__}", file=sys.stderr)
        return False


def to_rich_html(text: str, n_slides: int) -> str:
    """Текст поста -> HTML для rich-сообщения: слайдшоу из скриншотов сверху, заголовок, абзацы."""
    blocks = [b.strip() for b in text.split("\n\n") if b.strip()]
    out = ["<tg-slideshow>" + "".join(f'<img src="tg://photo?id=s{i}"/>' for i in range(n_slides)) + "</tg-slideshow>"]
    for i, b in enumerate(blocks):
        body = b.replace("\n", "<br/>")
        if i == 0 and body.startswith("📱") and "<b>" in body:
            out.append("<h2>" + body.replace("<b>", "").replace("</b>", "") + "</h2>")
        else:
            out.append("<p>" + body + "</p>")
    return "".join(out)


def send_rich(bot_token: str, chat_id: str, paths: list[str], html: str, buttons: list) -> bool:
    """Один пост: скриншоты + текст внутри одного сообщения (Bot API 10.x, sendRichMessage),
    кнопки под ним; под постом остаётся «Прокомментировать». False — Telegram не принял, ничего не опубликовано."""
    boundary = uuid.uuid4().hex
    media = [{"id": f"s{i}", "media": {"type": "photo", "media": f"attach://file{i}"}} for i in range(len(paths))]
    rich = {"html": html, "media": media}

    def field(k: str, v: str) -> bytes:
        return f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()

    body = field("chat_id", chat_id) + field("rich_message", json.dumps(rich, ensure_ascii=False))
    body += field("reply_markup", json.dumps({"inline_keyboard": buttons}, ensure_ascii=False))
    for i, p in enumerate(paths):
        with open(p, "rb") as f:
            data = f.read()
        body += (f'--{boundary}\r\nContent-Disposition: form-data; name="file{i}"; filename="{os.path.basename(p)}"\r\n'
                 "Content-Type: image/jpeg\r\n\r\n").encode() + data + b"\r\n"
    body += f"--{boundary}--\r\n".encode()
    req = urllib.request.Request(
        f"https://api.telegram.org/bot{bot_token}/sendRichMessage", data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return bool(json.loads(resp.read().decode("utf-8")).get("ok"))
    except urllib.error.HTTPError as e:
        try:
            print("Rich-пост не принят:", json.loads(e.read().decode("utf-8")).get("description"), file=sys.stderr)
        except Exception:
            print(f"Rich-пост не принят: HTTP {e.code}", file=sys.stderr)
        return False
    except Exception as e:
        print(f"Rich-пост: {type(e).__name__}", file=sys.stderr)
        return False


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--images", required=True)
    ap.add_argument("--text", required=True)
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args(argv)
    paths = sorted(glob.glob(os.path.join(a.images, "slide-*.jpg")))[:10]
    with open(a.text, encoding="utf-8") as f:
        text = f.read().strip()
    if a.dry_run:
        print(f"{len(paths)} слайдов, текст {len(text)} знаков")
        return 0
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    chat = os.environ.get("TELEGRAM_CHAT_ID", "").strip()
    if not token or not chat:
        print("Telegram не настроен, пост пропущен")
        return 0
    if not paths:
        print("Нет слайдов", file=sys.stderr)
        return 1
    buttons = build_buttons({}, chat)
    if send_rich(token, chat, paths, to_rich_html(text, len(paths)), buttons):
        print("Пост опубликован одним сообщением (rich)")
        return 0
    print("Rich не прошёл — публикую альбом и текст отдельно", file=sys.stderr)
    ok = send_album(token, chat, paths, "📱 <b>YAiPlayer для телефона</b> — скриншоты приложения")
    ok = ok and send_telegram_message(token, chat, text, build_buttons({}, chat))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
