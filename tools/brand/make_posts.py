#!/usr/bin/env python3
"""Постерные слайды для постов в Telegram (владелец 21.11): приставка, телефон, сайт, рекламный пост.

Цвета и шрифты — как у сайта и плеера: тёмно-синий фон, голубой акцент 6FB0FF, бирюзовая точка 22D3EE,
заголовки Unbounded, текст Onest (шрифты лежат в brand/fonts, лицензия OFL).
Исходные скриншоты — docs/posts/src (без логотипов каналов и постеров фильмов; ник владельца убран).
Готовые слайды кладутся в docs/posts/<пост>/slide-NN.jpg и коммитятся — CI их только отправляет.

Новый слайд: положите скриншот в docs/posts/src и добавьте строку в нужный список ниже, затем
    python3 tools/brand/make_posts.py
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
SRC = os.path.join(ROOT, "docs", "posts", "src")
OUT = os.path.join(ROOT, "docs", "posts")
FONTS = os.path.join(ROOT, "brand", "fonts")
W, H = 1600, 1000

PAPER_TOP = (8, 18, 38)
PAPER_BOTTOM = (4, 9, 19)
ACCENT = (111, 176, 255)
CYAN = (34, 211, 238)
INK = (232, 238, 248)
MUTED = (169, 182, 204)
BEZEL = (2, 6, 13)
LINE = (32, 49, 79)


def f(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name), size)


def background():
    im = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(im)
    for y in range(H):
        t = y / H
        d.line([(0, y), (W, y)], fill=tuple(int(PAPER_TOP[i] * (1 - t) + PAPER_BOTTOM[i] * t) for i in range(3)))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    g.ellipse([W - 760, -420, W + 360, 520], fill=ACCENT + (70,))
    g.ellipse([-420, H - 420, 520, H + 380], fill=CYAN + (36,))
    glow = glow.filter(ImageFilter.GaussianBlur(160))
    im = im.convert("RGBA")
    im.alpha_composite(glow)
    return im


def rounded(img, radius):
    img = img.convert("RGBA")
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.width - 1, img.height - 1], radius=radius, fill=255)
    img.putalpha(mask)
    return img


def device(img, width, radius, pad):
    """Screenshot inside a thin dark bezel with a soft shadow; returns (rgba, shadow_offset)."""
    h = round(width * img.height / img.width)
    shot = rounded(img.resize((width, h), Image.LANCZOS), radius - pad)
    frame = Image.new("RGBA", (width + pad * 2, h + pad * 2), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle([0, 0, frame.width - 1, frame.height - 1], radius=radius, fill=BEZEL + (255,), outline=LINE + (255,), width=2)
    frame.alpha_composite(shot, (pad, pad))
    shadow = Image.new("RGBA", (frame.width + 160, frame.height + 160), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([80, 110, frame.width + 80, frame.height + 110], radius=radius, fill=(0, 0, 0, 190))
    shadow = shadow.filter(ImageFilter.GaussianBlur(40))
    shadow.alpha_composite(frame, (80, 80))
    return shadow


def brand_row(im, label):
    logo = Image.open(os.path.join(SRC, "logo.png")).convert("RGBA").resize((64, 64), Image.LANCZOS)
    im.alpha_composite(logo, (80, 64))
    d = ImageDraw.Draw(im)
    d.text((164, 66), "YAiPlayer", font=f("Unbounded-Bold.ttf", 34), fill=INK)
    d.text((166, 108), label, font=f("Onest-Regular.ttf", 22), fill=MUTED)


def wrap(text, font, width, draw):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if draw.textlength(t, font=font) <= width:
            cur = t
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def text_block(im, x, y, width, title, sub, title_size=56):
    d = ImageDraw.Draw(im)
    ft, fs = f("Unbounded-Bold.ttf", title_size), f("Onest-Regular.ttf", 30)
    for line in wrap(title, ft, width, d):
        d.text((x, y), line, font=ft, fill=INK)
        y += int(title_size * 1.18)
    y += 14
    for line in wrap(sub, fs, width, d):
        d.text((x, y), line, font=fs, fill=MUTED)
        y += 42
    return y


def counter(im, idx, total):
    d = ImageDraw.Draw(im)
    t = f"{idx} / {total}"
    fo = f("Onest-SemiBold.ttf", 24)
    d.text((W - 80 - d.textlength(t, font=fo), 84), t, font=fo, fill=CYAN)


def tv_slide(src, title, sub, label, idx, total):
    im = background()
    brand_row(im, label)
    counter(im, idx, total)
    text_block(im, 80, 170, 1300, title, sub, 50)
    dev = device(Image.open(os.path.join(SRC, src)), 1280, 26, 10)
    im.alpha_composite(dev, ((W - dev.width) // 2, 300 - 80))
    return im


def phone_slide(src, title, sub, label, idx, total):
    im = background()
    brand_row(im, label)
    counter(im, idx, total)
    text_block(im, 80, 330, 760, title, sub, 56)
    dev = device(Image.open(os.path.join(SRC, src)), 430, 46, 8)
    im.alpha_composite(dev, (W - dev.width - 120, 70))
    return im


def site_slide(src, title, sub, label, idx, total, mobile=False):
    if mobile:
        return phone_slide(src, title, sub, label, idx, total)
    im = background()
    brand_row(im, label)
    counter(im, idx, total)
    text_block(im, 80, 170, 1300, title, sub, 50)
    dev = device(Image.open(os.path.join(SRC, src)).crop((0, 0, 2160, 1215)), 1280, 22, 8)
    im.alpha_composite(dev, ((W - dev.width) // 2, 300 - 80))
    return im


def cover_slide(title, sub, label):
    """Ad cover: the TV with the phone-remote and the phone app laid on it, like the site's first screen."""
    im = background()
    brand_row(im, label)
    text_block(im, 80, 190, 640, title, sub, 64)
    tv = device(Image.open(os.path.join(SRC, "tv-live.png")), 820, 22, 8)
    im.alpha_composite(tv, (W - tv.width + 40, 110))
    rem = device(Image.open(os.path.join(SRC, "ph-remote.png")), 250, 32, 6).rotate(5, resample=Image.BICUBIC, expand=True)
    im.alpha_composite(rem, (700, H - rem.height + 120))
    app = device(Image.open(os.path.join(SRC, "ph-profiles.png")), 230, 30, 6).rotate(-4, resample=Image.BICUBIC, expand=True)
    im.alpha_composite(app, (W - app.width + 10, H - app.height + 170))
    return im


TV = [
    ("tv-live.png", "Прямой эфир с живым превью", "Каналы включаются за секунду, а справа уже идёт выбранный канал"),
    ("tv-guide.png", "Телегид на весь день", "Программа появится, даже если её нет в плейлисте"),
    ("tv-sport.png", "Спорт на ваших каналах", "Матчи вашей команды: расписание, счёт и напоминание о начале"),
    ("tv-profiles.png", "Кто смотрит?", "Профили для всей семьи, гость, радио и «Продолжить» одной кнопкой"),
    ("tv-fast.png", "Быстрый запуск", "Нажали значок — и сразу смотрите последний канал"),
    ("tv-lite.png", "Лёгкий режим", "Плеер летает даже на старой и слабой приставке"),
    ("tv-sources.png", "Любые источники", "M3U, Xtream Codes и Stalker-порталы с видеоклубом"),
    ("tv-settings.png", "Всё под себя", "Плеер, изображение, звук, запись и архив"),
]
PHONE = [
    ("ph-profiles.png", "Плеер в кармане", "Тот же YAiPlayer — на Android-телефоне"),
    ("ph-sync.png", "Один аккаунт", "Войдите — плейлисты, порталы и избранное уже на месте"),
    ("ph-epg.png", "Телепрограмма и архив", "Напоминания о передачах и просмотр из архива"),
    ("ph-sources.png", "Stalker, M3U, Xtream", "Добавили адрес — и всё готово"),
    ("ph-remote.png", "Телефон — пульт", "Стрелки, громкость, каналы, клавиатура и голос"),
    ("ph-remote-set.png", "Пульт под вас", "Крестовина или тачпад, вибрация, несколько приставок"),
    ("ph-settings.png", "Гибкие настройки", "Профили, детский режим, вид, звук и субтитры"),
]
SITE = [
    ("site-hero.jpg", "Новый сайт YAiPlayer", "Всё о плеере в одном месте: возможности, скачивание, новости"),
    ("site-features.jpg", "Возможности плеера", "Архив, запись, быстрый запуск и синхронизация аккаунта"),
    ("site-know.jpg", "«А вы знали?»", "Фишки плеера листаются прямо на сайте"),
    ("site-news.jpg", "Новости релиза и беты", "Что нового в обычной версии и что уже можно попробовать в бете"),
    ("site-m-hero.jpg", "Удобно и с телефона", "Сайт подстраивается под любой экран", True),
    ("site-register.jpg", "Регистрация за минуту", "Без почты и телефона — только логин и пароль"),
]


def save(im, kind, i):
    os.makedirs(os.path.join(OUT, kind), exist_ok=True)
    im.convert("RGB").save(os.path.join(OUT, kind, f"slide-{i:02d}.jpg"), quality=88, optimize=True, progressive=True)


def main():
    for i, (s, t, sub) in enumerate(TV, 1):
        save(tv_slide(s, t, sub, "на приставке Android TV", i, len(TV)), "tv", i)
    for i, (s, t, sub) in enumerate(PHONE, 1):
        save(phone_slide(s, t, sub, "на Android-телефоне", i, len(PHONE)), "phone", i)
    for i, row in enumerate(SITE, 1):
        s, t, sub = row[:3]
        save(site_slide(s, t, sub, "yaiplayer.pages.dev", i, len(SITE), mobile=len(row) > 3), "site", i)
    ad = [cover_slide("Всё ваше ТВ в одном плеере", "Приставка, телефон, пульт и сайт — одна экосистема", "IPTV-плеер для Android TV и смартфонов")]
    ad.append(tv_slide(*TV[0], "на приставке Android TV", 2, 6))
    ad.append(phone_slide(*PHONE[4], "на Android-телефоне", 3, 6))
    ad.append(tv_slide(*TV[2], "на приставке Android TV", 4, 6))
    ad.append(phone_slide(*PHONE[1], "на Android-телефоне", 5, 6))
    ad.append(site_slide(*SITE[0], "yaiplayer.pages.dev", 6, 6))
    for i, im in enumerate(ad, 1):
        save(im, "ad", i)
    print("ok")


if __name__ == "__main__":
    sys.exit(main())
