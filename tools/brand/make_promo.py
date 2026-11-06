#!/usr/bin/env python3
"""Рекламный постер YAiPlayer для Telegram. Список возможностей — ниже (FEATURES): добавили функцию — допишите сюда
и в docs/promo/post.html, постер и текст обновятся при следующей публикации.
Запуск: python3 tools/brand/make_promo.py OUT.png"""
import os, sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from make_assets import mark, font, gradient, text_w, BOLD, REG, VIOLET, CYAN, WHITE, MUTED

W, H = 1080, 1350
FEATURES = [
    ("Любые источники", "M3U / M3U8 · Xtream Codes · Stalker-порталы"),
    ("Телегид", "Программа передач и иконки каналов — автоматически"),
    ("Эфир, кино и сериалы", "Живое ТВ и библиотека с постерами и описаниями"),
    ("Архив и запись", "Перемотка эфира назад, запись, напоминания"),
    ("Профили", "Своё избранное и история, детский профиль с PIN"),
    ("Удобно на ТВ и телефоне", "Пульт, жесты, Multiview, Chromecast, субтитры"),
]


def poster():
    im = gradient(W, H, "#1A1442", "#0B0D18").convert("RGBA")
    # soft cyan glow
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([W - 520, -260, W + 260, 520], fill=(34, 211, 238, 46))
    gd.ellipse([-300, H - 520, 420, H + 220], fill=(91, 63, 214, 70))
    im.alpha_composite(glow)
    d = ImageDraw.Draw(im)
    m = mark(150)
    im.alpha_composite(m, (72, 72))
    d.text((250, 84), "YAiPlayer", font=font(BOLD, 72), fill=WHITE)
    d.text((252, 168), "IPTV-плеер для Android TV и телефона", font=font(REG, 30), fill=MUTED)

    d.text((72, 300), "Всё ваше ТВ", font=font(BOLD, 96), fill=WHITE)
    d.text((72, 410), "в одном плеере", font=font(BOLD, 96), fill=CYAN)

    y = 560
    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    rows = []
    for title, sub in FEATURES:
        od.rounded_rectangle([72, y, W - 72, y + 92], radius=26, fill=(255, 255, 255, 22), outline=(255, 255, 255, 40), width=2)
        rows.append((y, title, sub))
        y += 104
    im.alpha_composite(ov)
    d = ImageDraw.Draw(im)
    for y, title, sub in rows:
        d.ellipse([100, y + 30, 132, y + 62], fill=VIOLET)
        d.polygon([(110, y + 37), (110, y + 55), (126, y + 46)], fill=WHITE)
        d.text((158, y + 10), title, font=font(BOLD, 32), fill=WHITE)
        d.text((158, y + 52), sub, font=font(REG, 24), fill=MUTED)

    d.rounded_rectangle([72, H - 150, W - 72, H - 64], radius=43, fill=VIOLET)
    t = "Скачать: t.me/YAiPlayer"
    f = font(BOLD, 40)
    d.text(((W - text_w(d, t, f)) / 2, H - 134), t, font=f, fill=WHITE)
    f2 = font(REG, 24)
    t2 = "Бесплатно · бета-версия · регулярные обновления"
    d.text(((W - text_w(d, t2, f2)) / 2, H - 52), t2, font=f2, fill=MUTED)
    return im.convert("RGB")


if __name__ == "__main__":
    poster().save(sys.argv[1] if len(sys.argv) > 1 else "promo-poster.png")
