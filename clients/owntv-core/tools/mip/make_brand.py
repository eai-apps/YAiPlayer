#!/usr/bin/env python3
"""miptv: renders the whole logo set from ONE drawing (assets/mip_mark.svg) - launcher icons (TV + phone), the TV banner,
the launch-screen icon. Owner 28.11: «я потом нарисую свой логотип» - to use his own picture, replace assets/mip_mark.svg
(transparent background, 100x100 viewBox, the mark only - NO text) or assets/mip_mark.png (1024x1024 transparent) and run:
    python3 clients/owntv-core/tools/mip/make_brand.py
Needs rsvg-convert and the font brand/fonts/Unbounded-Bold.ttf installed (fc-cache). Writes straight into the res folders.
"""
import re
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
CLIENTS = HERE.parents[2]
ASSETS = HERE / "assets"
TV_RES = CLIENTS / "android-tv/app/src/main/res"
MOB_RES = CLIENTS / "android-mobile/app/src/main/res"
DENS = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
MARK_SVG = ASSETS / "mip_mark.svg"
MARK_PNG = ASSETS / "mip_mark.png"


def mark_inner() -> str:
    """The drawing without the <svg> wrapper, in a 100x100 box (defs are kept)."""
    if MARK_SVG.exists():
        t = MARK_SVG.read_text(encoding="utf-8")
        return re.sub(r"</?svg[^>]*>", "", t)
    import base64
    b = base64.b64encode(MARK_PNG.read_bytes()).decode()
    return f'<image width="100" height="100" href="data:image/png;base64,{b}"/>'


def render(svg: str, w: int, h: int, out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False, encoding="utf-8") as f:
        f.write(svg)
        tmp = f.name
    subprocess.run(["rsvg-convert", "-w", str(w), "-h", str(h), "-o", str(out), tmp], check=True)


def wordmark(x, y, size, anchor="middle"):
    return (f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-family="Unbounded" font-weight="700" font-size="{size}" letter-spacing="-1">'
            f'<tspan fill="#FFFFFF">mip</tspan><tspan fill="url(#acc)">tv</tspan></text>')


DEFS = ('<defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#2A1A6E"/><stop offset="1" stop-color="#0B0D18"/></linearGradient>'
        '<linearGradient id="acc" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#FF3D9A"/><stop offset="1" stop-color="#FF9F43"/></linearGradient></defs>')


def beta_pill(cx, cy, w, h):
    return (f'<rect x="{cx - w / 2}" y="{cy - h / 2}" width="{w}" height="{h}" rx="{h / 2}" fill="#FFC83D"/>'
            f'<text x="{cx}" y="{cy + h * 0.2}" text-anchor="middle" font-family="Unbounded" font-weight="700" font-size="{h * 0.52}" fill="#1B1147">BETA</text>')


def icon_svg(beta: bool) -> str:
    # 512x512 rounded square, the mark big in the middle; the BETA pill sits UNDER the mark (it no longer covers it)
    if beta:
        body = f'<g transform="translate(106 36) scale(3.0)">{mark_inner()}</g>' + beta_pill(256, 438, 250, 66)
    else:
        body = f'<g transform="translate(76 66) scale(3.6)">{mark_inner()}</g>'
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">{DEFS}<rect width="512" height="512" rx="112" fill="url(#bg)"/>{body}</svg>'


def banner_svg(beta: bool) -> str:
    # 640x360 TV banner: the mark on the left, «miptv» in lowercase on the right
    body = f'<g transform="translate(70 80) scale(2.0)">{mark_inner()}</g>' + wordmark(280, 222, 86, "start")
    if beta:
        body += beta_pill(456, 290, 150, 44)
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 360">{DEFS}<rect width="640" height="360" fill="url(#bg)"/>{body}</svg>'


def splash_svg() -> str:
    # launch screen icon: transparent, the mark inside the middle third so the round system mask never cuts it
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">{DEFS}<g transform="translate(18 18) scale(0.64)">{mark_inner()}</g></svg>'


def logo_svg() -> str:
    """Wide logo (mark + name) for the login screens and the site: transparent background."""
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 220">{DEFS}<g transform="translate(20 20) scale(1.8)">{mark_inner()}</g>{wordmark(230, 140, 92, "start")}</svg>'


def main() -> int:
    for beta in (False, True):
        suffix = "_beta" if beta else ""
        svg = icon_svg(beta)
        for d, px in DENS.items():
            for res in (TV_RES, MOB_RES):
                render(svg, px, px, res / f"mipmap-{d}/mip_icon{suffix}.png")
        render(banner_svg(beta), 320, 180, TV_RES / f"drawable-nodpi/mip_banner{suffix}.png")
    render(splash_svg(), 960, 960, ASSETS / "mip_splash.png")
    render(logo_svg(), 640, 220, ASSETS / "mip_logo.png")
    render(icon_svg(False), 512, 512, ASSETS / "mip_icon_512.png")
    for res in (TV_RES, MOB_RES):  # the in-app wide logo (login screens)
        render(logo_svg(), 640, 220, res / "drawable-nodpi/mip_logo.png")
    print("miptv brand set written")
    return 0


if __name__ == "__main__":
    sys.exit(main())
