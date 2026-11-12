#!/usr/bin/env python3
"""Постер поста про отдельное приложение YAiPlay Radio: brand/radio-poster-1280x720.png.
Скриншоты сайта лежат в docs/radio-post/shot-N.jpg. Запуск из корня репозитория: python3 tools/brand/make_radio_poster.py"""
from PIL import Image, ImageDraw, ImageFont

W, H = 1280, 720
B = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
R = "/usr/share/fonts/opentype/inter/Inter-Regular.otf"
M = "/usr/share/fonts/opentype/inter/Inter-Medium.otf"
f = lambda p, s: ImageFont.truetype(p, s)

bg = Image.new("RGB", (W, H))
px = bg.load()
for y in range(H):
    t = y / H
    for x in range(W):
        k = x / W
        px[x, y] = (int(16 + 14 * k - 6 * t), int(14 + 6 * k - 4 * t), int(44 + 22 * k - 14 * t))
d = ImageDraw.Draw(bg, "RGBA")
for r, a in ((460, 20), (330, 26), (200, 34)):
    d.ellipse((W - r - 120, 120 - r // 2, W + r - 120, 120 + r * 3 // 2 - r // 2), fill=(34, 211, 238, a))
for r, a in ((420, 22), (260, 30)):
    d.ellipse((-r // 2, H - r, r, H + r // 2), fill=(91, 62, 214, a))

logo = Image.open("/home/claude/yaiplay-radio/assets/brand/yaiplay-radio-logo-1024.png").convert("RGBA").resize((140, 140))
m = Image.new("L", (140, 140), 0)
ImageDraw.Draw(m).rounded_rectangle((0, 0, 139, 139), radius=34, fill=255)
bg.paste(logo, (70, 64), m)
d.text((236, 82), "YAiPlay Radio", font=f(B, 52), fill="white")
d.text((238, 148), "Радио со всего мира", font=f(R, 28), fill=(170, 174, 204))
d.text((70, 262), "Слушайте радио", font=f(B, 76), fill="white")
d.text((70, 352), "в приложении и на сайте", font=f(B, 50), fill=(34, 211, 238))
y = 452
for text, col in (("Бесплатно", (74, 222, 128)), ("Без рекламы", (250, 204, 21)), ("Без регистрации", (167, 139, 250))):
    ft = f(M, 27)
    w = int(d.textlength(text, font=ft)) + 76
    d.rounded_rectangle((70, y, 70 + w, y + 56), radius=28, fill=(255, 255, 255, 26), outline=(255, 255, 255, 60), width=2)
    d.ellipse((90, y + 17, 112, y + 39), fill=col)
    d.text((126, y + 12), text, font=ft, fill="white")
    y += 72
d.text((70, 672), "Радио есть и внутри плеера YAiPlayer", font=f(M, 22), fill=(190, 194, 220))

# three phone screens on the right, the middle one on top
shots = [("docs/radio-post/shot-3.jpg", 760, 120, -5), ("docs/radio-post/shot-2.jpg", 1000, 150, 4), ("docs/radio-post/shot-1.jpg", 880, 60, 0)]
PW, PH = 250, 540
for path, x, y, rot in shots:
    im = Image.open(path).convert("RGB").resize((PW, PH))
    mask = Image.new("L", (PW, PH), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, PW - 1, PH - 1), radius=28, fill=255)
    card = Image.new("RGBA", (PW + 12, PH + 12), (0, 0, 0, 0))
    ImageDraw.Draw(card).rounded_rectangle((0, 0, PW + 11, PH + 11), radius=34, fill=(255, 255, 255, 70))
    card.paste(im, (6, 6), mask)
    card = card.rotate(rot, expand=True, resample=Image.BICUBIC)
    shadow = Image.new("RGBA", card.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((10, 18, card.size[0] - 4, card.size[1] - 2), radius=36, fill=(0, 0, 0, 110))
    bg.paste(shadow, (x - 8, y + 8), shadow)
    bg.paste(card, (x, y), card)
bg.save("brand/radio-poster-1280x720.png", optimize=True)
print("ok")
