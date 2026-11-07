#!/usr/bin/env python3
"""Постерные скриншоты мобильного приложения для Telegram-альбома.
Берёт сырые скриншоты из docs/showcase/raw, обрезает строку состояния и системные кнопки,
кладёт экран в скруглённую рамку на фирменный фон с подписью.
Новый экран: положите скриншот в docs/showcase/raw и добавьте строку в SLIDES.
Запуск: python3 tools/brand/make_showcase.py OUT_DIR"""
import os, sys
from PIL import Image, ImageDraw, ImageFilter
sys.path.insert(0, os.path.dirname(__file__))
from make_assets import mark, font, gradient, text_w, BOLD, REG, VIOLET, CYAN, WHITE, MUTED

RAW = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "showcase", "raw")
W, H = 1080, 1620
# файл, заголовок, подпись, обрезка (left, top, right, bottom) в пикселях исходника
SLIDES = [
    ("01-home.jpg", "Главная", "Продолжить просмотр и подборки каналов", (0, 80, 1080, 2125)),
    ("02-live.jpg", "Прямой эфир", "Каналы с текущей передачей и прогрессом", (0, 80, 1080, 2125)),
    ("03-guide.jpg", "Телегид", "Вся программа передач на одной ленте", (0, 80, 1080, 2125)),
    ("04-player.jpg", "Плеер", "Перемотка эфира, субтитры, звук, Chromecast", (0, 0, 2100, 1080)),
    ("05-library.jpg", "Библиотека", "Фильмы и сериалы с постерами", (0, 80, 1080, 2125)),
    ("06-film.jpg", "Карточка фильма", "Описание, избранное и подборка из категории", (0, 80, 1080, 2125)),
    ("07-sport.jpg", "Спорт", "Матчи и трансляции на ваших каналах", (0, 80, 1080, 2125)),
    ("08-sources.jpg", "Любые источники", "M3U / M3U8 · Xtream Codes · Stalker-порталы", (0, 80, 1080, 2125)),
    ("09-settings.jpg", "Гибкие настройки", "Плеер, изображение, звук, профили, запись", (0, 80, 1080, 2078)),
    ("10-epg.jpg", "Источники и телегид", "Свои плейлисты, ленты EPG, напоминания, архив", (0, 80, 1080, 2125)),
]


def card(img, width):
    h = round(width * img.height / img.width)
    img = img.resize((width, h), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, width - 1, h - 1], radius=46, fill=255)
    img.putalpha(mask)
    return img


def slide(src, title, sub, box, idx, total):
    im = gradient(W, H, "#1A1442", "#0B0D18").convert("RGBA")
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([W - 520, -300, W + 260, 480], fill=(34, 211, 238, 46))
    gd.ellipse([-320, H - 560, 420, H + 220], fill=(91, 63, 214, 80))
    im.alpha_composite(glow)
    d = ImageDraw.Draw(im)
    im.alpha_composite(mark(76), (64, 56))
    d.text((156, 62), "YAiPlayer", font=font(BOLD, 40), fill=WHITE)
    d.text((158, 112), "приложение для телефона", font=font(REG, 22), fill=MUTED)
    tw = f"{idx}/{total}"
    f = font(REG, 24)
    d.text((W - 64 - text_w(d, tw, f), 76), tw, font=f, fill=MUTED)
    d.text((64, 200), title, font=font(BOLD, 68), fill=WHITE)
    d.text((66, 290), sub, font=font(REG, 30), fill=CYAN)

    shot = Image.open(os.path.join(RAW, src)).crop(box)
    landscape = shot.width > shot.height
    c = card(shot, 984 if landscape else 640)
    x = (W - c.width) // 2
    y = 400 if landscape else 370
    if landscape:
        y = 520
    pad = Image.new("RGBA", (c.width + 160, c.height + 160), (0, 0, 0, 0))
    sh = Image.new("RGBA", c.size, (0, 0, 0, 200))
    sh.putalpha(c.getchannel("A").point(lambda v: 170 if v else 0))
    pad.paste(sh, (80, 96), sh)
    pad = pad.filter(ImageFilter.GaussianBlur(28))
    im.alpha_composite(pad, (x - 80, y - 80))
    ring = Image.new("RGBA", (c.width + 8, c.height + 8), (0, 0, 0, 0))
    ImageDraw.Draw(ring).rounded_rectangle([0, 0, c.width + 7, c.height + 7], radius=50, fill=(255, 255, 255, 60))
    im.alpha_composite(ring, (x - 4, y - 4))
    im.alpha_composite(c, (x, y))
    return im.convert("RGB")


def main(out):
    os.makedirs(out, exist_ok=True)
    for i, (src, title, sub, box) in enumerate(SLIDES, 1):
        slide(src, title, sub, box, i, len(SLIDES)).save(os.path.join(out, f"slide-{i:02d}.jpg"), quality=92)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "showcase-out")
