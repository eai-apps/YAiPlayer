"""Постер к посту «YAiPlayer 4.0» (brand/v4-poster-1280x720.png). Запуск: python3 brand/make_v4_poster.py"""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent
F = "/usr/share/fonts/opentype/inter/"
W, H = 1280, 720


def font(name, size):
    return ImageFont.truetype(F + name, size)


def radial(size, color, radius, center):
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = center
    d.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=color)
    return layer.filter(ImageFilter.GaussianBlur(radius * 0.55))


# Фон: глубокий синий → фиолетовый, с цветными «прожекторами»
img = Image.new("RGBA", (W, H))
px = img.load()
for y in range(H):
    for x in range(W):
        t = x / W
        r = int(10 + 22 * t)
        g = int(10 + 6 * (1 - t))
        b = int(36 + 40 * t)
        px[x, y] = (r, g, b, 255)
for col, rad, c in [((92, 76, 255, 200), 340, (330, 120)), ((32, 220, 190, 150), 300, (1150, 640)),
                    ((255, 120, 60, 120), 260, (1180, 90)), ((140, 60, 255, 120), 260, (640, 760))]:
    img = Image.alpha_composite(img, radial((W, H), col, rad, c))

# Тонкие диагональные «лучи»
rays = Image.new("RGBA", (W, H), (0, 0, 0, 0))
dr = ImageDraw.Draw(rays)
for i in range(-6, 14):
    x0 = i * 110
    dr.line([(x0, H), (x0 + 520, 0)], fill=(255, 255, 255, 10), width=2)
img = Image.alpha_composite(img, rays)

d = ImageDraw.Draw(img)

# Логотип со свечением
logo = Image.open(ROOT / "logo-1024.png").convert("RGBA").resize((230, 230), Image.LANCZOS)
glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
glow.paste(logo, (70, 92), logo)
glow = glow.filter(ImageFilter.GaussianBlur(28))
img = Image.alpha_composite(img, glow)
img.alpha_composite(logo, (70, 92))
d = ImageDraw.Draw(img)

# Заголовок: «YAiPlayer» + огромная «4.0» градиентом
d.text((332, 98), "YAiPlayer", font=font("InterDisplay-Bold.otf", 64), fill=(235, 238, 255))
big = font("InterDisplay-Black.otf", 196)
mask = Image.new("L", (W, H), 0)
ImageDraw.Draw(mask).text((322, 150), "4.0", font=big, fill=255)
grad = Image.new("RGBA", (W, H))
gp = grad.load()
for y in range(H):
    for x in range(W):
        t = min(1.0, max(0.0, (x - 322) / 520))
        gp[x, y] = (int(255 - 25 * t), int(226 - 120 * t), int(89 - 30 * t), 255)  # жёлтый → оранжевый
shadow = Image.new("RGBA", (W, H), (255, 110, 30, 0))
shadow.putalpha(mask.filter(ImageFilter.GaussianBlur(22)).point(lambda v: int(v * 0.75)))
img = Image.alpha_composite(img, shadow)
img.paste(grad, (0, 0), mask)
d = ImageDraw.Draw(img)

# Бейдж «СУПЕР-ОБНОВЛЕНИЕ»
bx, by = 700, 168
label = "СУПЕР-ОБНОВЛЕНИЕ"
lf = font("InterDisplay-ExtraBold.otf", 30)
tw = d.textlength(label, font=lf)
d.rounded_rectangle((bx, by, bx + tw + 44, by + 58), radius=29, fill=(255, 76, 96))
d.text((bx + 22, by + 10), label, font=lf, fill=(255, 255, 255))
d.text((bx, by + 76), "Самое большое обновление плеера", font=font("Inter-SemiBold.otf", 27), fill=(220, 224, 255))

# Плашки-фичи
chips = ["🔐", "Один аккаунт", "📱", "Пульт с телефона", "⚽", "Спорт", "⏺", "Запись эфира",
         "⚡", "Быстрый запуск", "📻", "Радио"]
names = chips[1::2]
cf = font("Inter-Bold.otf", 30)
x, y = 70, 430
layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
dl = ImageDraw.Draw(layer)
pos = []
for n in names:
    w = d.textlength(n, font=cf) + 56
    if x + w > W - 60:
        x, y = 70, y + 78
    dl.rounded_rectangle((x, y, x + w, y + 60), radius=30, fill=(255, 255, 255, 34), outline=(120, 230, 210, 220), width=2)
    pos.append((x, y, n))
    x += w + 18
img = Image.alpha_composite(img, layer)
d = ImageDraw.Draw(img)
for x, y, n in pos:
    d.ellipse((x + 18, y + 24, x + 30, y + 36), fill=(52, 222, 190))
    d.text((x + 40, y + 12), n, font=cf, fill=(245, 247, 255))

# Низ: призыв
d.text((70, 640), "Скачать — в боте @YAiPlayer_App_bot", font=font("Inter-SemiBold.otf", 28), fill=(170, 236, 222))
d.text((W - 70 - d.textlength("Бета — уже в боте 🧪"[:-2], font=font("Inter-SemiBold.otf", 28)), 640),
       "Бета — уже в боте", font=font("Inter-SemiBold.otf", 28), fill=(255, 210, 120))

img.convert("RGB").save(ROOT / "v4-poster-1280x720.png", optimize=True)
print("ok")
