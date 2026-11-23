#!/usr/bin/env python3
"""Beta-build launcher icon, banner and fonts (owner 23.11: «на иконке написано beta»).

Run from the repo root:  python3 tools/brand/make_beta_icon.py
Needs: Pillow and the font file brand/fonts/permanent_marker.ttf (Permanent Marker, SIL OFL 1.1).
Writes into clients/owntv-core/core/src/main/res:
  mipmap-{m,h,xh,xxh,xxxh}dpi/yai_beta_icon_foreground.png   adaptive icon foreground (triangle + blue diagonal BETA ribbon)
  drawable-xhdpi/yai_banner_beta.png                         Android TV banner 320x180
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "clients/owntv-core/core/src/main/res"
FONT = ROOT / "brand/fonts/permanent_marker.ttf"
BG = (91, 63, 214, 255)      # owntv_icon_bg_eggshell, the app's purple
YELLOW = (255, 214, 64, 255)
INK = (20, 12, 60, 255)


def graffiti(text, size, angle):
    """The word as tilted graffiti: dark outline, yellow fill."""
    font = ImageFont.truetype(str(FONT), size)
    pad = size // 4
    box = font.getbbox(text)
    w, h = box[2] - box[0] + pad * 2, box[3] - box[1] + pad * 2
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.text((pad - box[0], pad - box[1]), text, font=font, fill=YELLOW, stroke_width=max(2, size // 14), stroke_fill=INK)
    return layer.rotate(angle, expand=True, resample=Image.BICUBIC)


def triangle(size, scale, cx, cy):
    """The app's white play triangle (rounded), drawn at 4x and downsampled."""
    k = 4
    s = size * k
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    half = int(s * 0.30 * scale)
    x0, y0 = int(cx * k) - half, int(cy * k)
    pts = [(x0, y0 - half), (x0 + int(half * 1.8), y0), (x0, y0 + half)]
    d.polygon(pts, fill=(255, 255, 255, 255))
    return im.resize((size, size), Image.LANCZOS)


BLUE = (24, 118, 214, 255)
BLUE_DARK = (12, 78, 156, 255)
GOLD_LIGHT = (255, 232, 140, 255)
GOLD = (222, 170, 48, 255)
GOLD_DARK = (150, 100, 20, 255)


def ribbon(size, text, y0, y1, font_px):
    """A blue band across the canvas from (0,y1) up to (size,y0), white text on it, drawn at 2x."""
    k = 2
    s = size * k
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    th = int(size * 0.20 * k)
    a = (-s * 0.1, y1 * k)
    b = (s * 1.1, y0 * k)
    import math
    ang = math.atan2(b[1] - a[1], b[0] - a[0])
    nx, ny = -math.sin(ang) * th / 2, math.cos(ang) * th / 2
    d.polygon([(a[0] + nx, a[1] + ny), (b[0] + nx, b[1] + ny), (b[0] - nx, b[1] - ny), (a[0] - nx, a[1] - ny)], fill=BLUE)
    # darker edges give the band a folded look
    for sign in (1, -1):
        d.line([(a[0] + sign * nx, a[1] + sign * ny), (b[0] + sign * nx, b[1] + sign * ny)], fill=BLUE_DARK, width=max(2, int(size * 0.012 * k)))
    font = ImageFont.truetype(str(FONT), int(font_px * k))
    box = font.getbbox(text)
    tw, thh = box[2] - box[0], box[3] - box[1]
    layer = Image.new("RGBA", (tw + 40, thh + 40), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    ld.text((20 - box[0] + 3, 20 - box[1] + 3), text, font=font, fill=(0, 30, 80, 200))
    ld.text((20 - box[0], 20 - box[1]), text, font=font, fill=(255, 255, 255, 255))
    layer = layer.rotate(-math.degrees(ang), expand=True, resample=Image.BICUBIC)
    cx, cy = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
    im.alpha_composite(layer, (int(cx - layer.width / 2), int(cy - layer.height / 2)))
    return im.resize((size, size), Image.LANCZOS)


def icon_foreground(px):
    base = 432
    im = Image.new("RGBA", (base, base), (0, 0, 0, 0))
    im.alpha_composite(triangle(base, 0.60, 176, 150))
    # The blue ribbon tied across the icon, diagonally (owner 23.11): no white box, only the band.
    im.alpha_composite(ribbon(base, "BETA", 180, 330, 78))
    return im.resize((px, px), Image.LANCZOS)


def badge(w=900, h=900):
    """«BETA VERSION» seal for the launch screens: a gold toothed medal with a blue ribbon across it (own drawing)."""
    import math
    k = 2
    W, H = w * k, h * k
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx, cy = W / 2, H * 0.47
    R = W * 0.36
    teeth = 36
    pts = []
    for i in range(teeth * 2):
        r = R if i % 2 == 0 else R * 0.93
        t = math.pi * i / teeth
        pts.append((cx + r * math.cos(t), cy + r * math.sin(t)))
    d.polygon(pts, fill=GOLD_DARK)
    d.ellipse([cx - R * 0.93, cy - R * 0.93, cx + R * 0.93, cy + R * 0.93], fill=GOLD)
    d.ellipse([cx - R * 0.80, cy - R * 0.80, cx + R * 0.80, cy + R * 0.80], fill=GOLD_LIGHT)
    d.ellipse([cx - R * 0.74, cy - R * 0.74, cx + R * 0.74, cy + R * 0.74], fill=GOLD)
    # stars above and below the ribbon
    def star(x, y, r):
        p = []
        for i in range(10):
            rr = r if i % 2 == 0 else r * 0.45
            t = -math.pi / 2 + math.pi * i / 5
            p.append((x + rr * math.cos(t), y + rr * math.sin(t)))
        d.polygon(p, fill=(60, 40, 10, 255))
    for dx in (-0.2, 0, 0.2):
        star(cx + dx * R, cy - R * 0.45, R * (0.11 if dx == 0 else 0.08))
        star(cx + dx * R, cy + R * 0.45, R * (0.11 if dx == 0 else 0.08))
    # ribbon tails and band
    bh = R * 0.50
    by0, by1 = cy - bh / 2, cy + bh / 2
    left, right = cx - R * 1.28, cx + R * 1.28
    for side in (-1, 1):
        x0 = cx + side * R * 1.28
        x1 = cx + side * R * 0.95
        d.polygon([(x0, by0 + bh * 0.28), (x1, by0 + bh * 0.28), (x1, by1 + bh * 0.28), (x0, by1 + bh * 0.28),
                   (x0 + side * (-R * 0.16), by0 + bh * 0.78)], fill=BLUE_DARK)
    d.rectangle([left + R * 0.08, by0, right - R * 0.08, by1], fill=BLUE)
    d.line([(left + R * 0.08, by0), (right - R * 0.08, by0)], fill=BLUE_DARK, width=int(R * 0.02))
    d.line([(left + R * 0.08, by1), (right - R * 0.08, by1)], fill=BLUE_DARK, width=int(R * 0.02))
    text = "BETA VERSION"
    font = ImageFont.truetype(str(FONT), int(R * 0.27))
    box = font.getbbox(text)
    tx, ty = cx - (box[2] - box[0]) / 2 - box[0], cy - (box[3] - box[1]) / 2 - box[1]
    d.text((tx + 4, ty + 5), text, font=font, fill=(0, 30, 80, 210))
    d.text((tx, ty), text, font=font, fill=(255, 255, 255, 255))
    # curved-free small captions on the medal
    f2 = ImageFont.truetype(str(FONT), int(R * 0.17))
    for txt, yy in (("YAiPlayer", cy - R * 0.70), ("test build", cy + R * 0.68)):
        b2 = f2.getbbox(txt)
        d.text((cx - (b2[2] - b2[0]) / 2 - b2[0], yy - (b2[3] - b2[1]) / 2 - b2[1]), txt, font=f2, fill=(60, 40, 10, 255))
    return im.resize((w, h), Image.LANCZOS)


def main():
    for dens, px in (("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)):
        d = RES / f"mipmap-{dens}"
        d.mkdir(parents=True, exist_ok=True)
        icon_foreground(px).save(d / "yai_beta_icon_foreground.png", optimize=True)
    # Banner: the normal banner with the blue ribbon across its lower-right corner.
    src = RES / "drawable-xhdpi/owntv_banner_eggshell.png"
    b = Image.open(src).convert("RGBA")
    rb = ribbon(max(b.size), "BETA", 70, 200, 40).crop((0, 0, b.width, b.height))
    b.alpha_composite(rb)
    b.save(RES / "drawable-xhdpi/yai_banner_beta.png", optimize=True)
    # The seal for the profile / launch screens (shared by the TV and phone apps).
    dn = RES / "drawable-nodpi"
    dn.mkdir(parents=True, exist_ok=True)
    badge().save(dn / "yai_beta_badge.png", optimize=True)
    print("ok")


if __name__ == "__main__":
    main()
