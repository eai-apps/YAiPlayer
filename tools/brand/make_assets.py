#!/usr/bin/env python3
"""Logo, Telegram avatar/banner, site images and a release-post card. Run: python3 tools/brand/make_assets.py [version] [bullet ...]
Writes into brand/. Colours match tools/brand/yai_brand.py (violet tile, white play triangle, cyan dot)."""
import os, sys
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "brand")
VIOLET, DARK, CYAN, WHITE, MUTED = "#5B3FD6", "#0F1220", "#22D3EE", "#FFFFFF", "#A9B0C8"
BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
REG = "/usr/share/fonts/opentype/inter/Inter-Regular.otf"
for p in (BOLD, REG):
    if not os.path.exists(p):
        BOLD = REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def font(path, size):
    return ImageFont.truetype(path, size)


def mark(size, radius=0.24, bg=VIOLET):
    """The app icon: rounded violet tile, white play triangle, cyan signal dot (supersampled)."""
    s = 4
    n = size * s
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, n - 1, n - 1], radius=int(n * radius), fill=bg)
    u = n / 56.0
    tri = [(22.7 * u, 20 * u), (40.2 * u, 30 * u), (22.7 * u, 40 * u)]
    d.polygon(tri, fill=WHITE)
    cx, cy, r = 41 * u, 17 * u, 3.6 * u
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=CYAN)
    return im.resize((size, size), Image.LANCZOS)


def text_w(d, t, f):
    return d.textlength(t, font=f)


def gradient(w, h, a="#161233", b="#0B0D18"):
    base = Image.new("RGB", (w, h), a)
    top = Image.new("RGB", (w, h), b)
    mask = Image.linear_gradient("L").resize((w, h))
    return Image.composite(top, base, mask)


def avatar_named(size=640):
    """Square avatar with the player name under the mark (Telegram crops it to a circle: all content stays inside)."""
    im = gradient(size, size).convert("RGBA")
    d = ImageDraw.Draw(im)
    m = mark(int(size * 0.46))
    im.alpha_composite(m, ((size - m.size[0]) // 2, int(size * 0.17)))
    f = font(BOLD, int(size * 0.115))
    t = "YAiPlayer"
    d.text(((size - text_w(d, t, f)) / 2, int(size * 0.69)), t, font=f, fill=WHITE)
    return im.convert("RGB")


def banner(w, h, tagline):
    im = gradient(w, h).convert("RGBA")
    m = mark(int(h * 0.42))
    d = ImageDraw.Draw(im)
    f1 = font(BOLD, int(h * 0.16))
    x = int(w * 0.08)
    size2 = int(h * 0.062)
    f2 = font(REG, size2)
    avail = w - x - m.size[0] - int(h * 0.06) - int(w * 0.06)
    while text_w(d, tagline, f2) > avail and size2 > 12:
        size2 -= 1
        f2 = font(REG, size2)
    y = int(h * 0.5 - m.size[1] / 2)
    im.alpha_composite(m, (x, y))
    tx = x + m.size[0] + int(h * 0.06)
    d.text((tx, y - int(h * 0.01)), "YAiPlayer", font=f1, fill=WHITE)
    d.text((tx, y + int(h * 0.19)), tagline, font=f2, fill=MUTED)
    return im.convert("RGB")


def release_card(version, bullets, w=1280, h=720):
    im = gradient(w, h).convert("RGBA")
    d = ImageDraw.Draw(im)
    m = mark(88)
    im.alpha_composite(m, (64, 56))
    d.text((176, 62), "YAiPlayer", font=font(BOLD, 44), fill=WHITE)
    d.text((176, 112), "Новая версия", font=font(REG, 26), fill=MUTED)
    vf = font(BOLD, 40)
    tag = "v" + version
    tw = text_w(d, tag, vf)
    d.rounded_rectangle([w - 64 - tw - 48, 62, w - 64, 62 + 70], radius=35, fill=VIOLET)
    d.text((w - 64 - tw - 24, 72), tag, font=vf, fill=WHITE)
    d.text((64, 190), "Что нового", font=font(BOLD, 34), fill=CYAN)
    f = font(REG, 31)
    y = 250
    for b in bullets[:6]:
        d.ellipse([68, y + 14, 84, y + 30], fill=CYAN)
        line, lines = "", []
        for word in b.split():
            if text_w(d, (line + " " + word).strip(), f) > w - 64 - 120:
                lines.append(line)
                line = word
            else:
                line = (line + " " + word).strip()
        lines.append(line)
        for ln in lines[:2]:
            d.text((104, y), ln, font=f, fill=WHITE)
            y += 44
        y += 14
    d.text((64, h - 70), "t.me/YAiPlayer", font=font(REG, 26), fill=MUTED)
    return im.convert("RGB")


def card_from_release(release_json, out_path):
    """The release post picture: version + the "what's new" bullets of a GitHub release JSON."""
    import json, re
    rel = json.load(open(release_json, encoding="utf-8"))
    tag = (rel.get("tag_name") or rel.get("name") or "").lstrip("vV") or "2.0"
    bullets = []
    for line in (rel.get("body") or "").splitlines():
        m = re.match(r"^\s*[-*•]\s+(.*)$", line)
        if m:
            t = re.sub(r"[*_`]|\[([^\]]*)\]\([^)]*\)", lambda x: x.group(1) or "", m.group(1)).strip()
            if t:
                bullets.append(t)
    release_card(tag, bullets or ["Улучшения и исправления"]).save(out_path)


def main():
    if len(sys.argv) >= 4 and sys.argv[1] == "--card":
        card_from_release(sys.argv[3], sys.argv[2])  # --card OUT.png release.json
        return
    os.makedirs(OUT, exist_ok=True)
    mark(1024).save(os.path.join(OUT, "logo-1024.png"))
    mark(512, radius=0.0).save(os.path.join(OUT, "logo-512-square.png"))
    mark(640, radius=0.0).save(os.path.join(OUT, "telegram-avatar-640.png"))  # Telegram rounds it itself
    mark(256).save(os.path.join(OUT, "favicon-256.png"))
    avatar_named(640).save(os.path.join(OUT, "telegram-avatar-named-640.png"))
    banner(1280, 720, "IPTV-плеер для Android TV и телефона").save(os.path.join(OUT, "telegram-banner-1280x720.png"))
    banner(1200, 630, "IPTV-плеер для Android TV и телефона").save(os.path.join(OUT, "site-og-1200x630.png"))
    version = sys.argv[1] if len(sys.argv) > 1 else "2.0.0"
    bullets = sys.argv[2:] or ["Вшитый телегид и значки каналов", "Кнопка «Добавить источник» на Главной", "Исправлено обновление на Xiaomi"]
    release_card(version, bullets).save(os.path.join(OUT, "post-release-example.png"))
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 56 56" width="512" height="512"><rect width="56" height="56" rx="13.5" fill="{VIOLET}"/><path d="M22.7 20 L40.2 30 L22.7 40 Z" fill="#fff" stroke="#fff" stroke-width="1.6" stroke-linejoin="round"/><circle cx="41" cy="17" r="3.6" fill="{CYAN}"/></svg>'''
    open(os.path.join(OUT, "logo.svg"), "w", encoding="utf-8").write(svg)


if __name__ == "__main__":
    main()
