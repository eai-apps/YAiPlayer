#!/usr/bin/env python3
"""Builds assets/tvoptimizer/themes.json from a checkout of Gathin23/cleanyourtv (MIT).

Usage: python3 -I tools/tvoptimizer/build_themes.py <cleanyourtv checkout> <output json>

Keeps every theme's config.json as is and the names of its pictures. The pictures themselves are not
copied into the APK (23 MB): the phone downloads them from the same pinned commit when the user applies
the theme, from raw.githubusercontent.com/Gathin23/cleanyourtv/<commit>/themes/<name>/<file>.
"""
import json, os, re, subprocess, sys

NAME = re.compile(r"^[A-Za-z0-9_.-]+$")
PICTURE = re.compile(r"\.(jpe?g|png|webp)$", re.I)


def main(src, out):
    sha = subprocess.check_output(["git", "-C", src, "rev-parse", "HEAD"], text=True).strip()
    themes = []
    root = os.path.join(src, "themes")
    for name in sorted(os.listdir(root)):
        d = os.path.join(root, name)
        cfg = os.path.join(d, "config.json")
        if not NAME.match(name) or not os.path.isfile(cfg):
            continue
        with open(cfg, encoding="utf-8-sig") as f:
            config = json.load(f)
        if not isinstance(config, dict):
            continue
        pics = []
        wd = os.path.join(d, "wallpapers")
        if os.path.isdir(wd):
            pics = sorted(p for p in os.listdir(wd) if PICTURE.search(p) and NAME.match(p) and ".." not in p)
        themes.append({
            "name": name,
            "config": config,
            "wallpaper": os.path.isfile(os.path.join(d, "wallpaper.jpg")),
            "wallpapers": pics,
            "captions": os.path.isfile(os.path.join(wd, "captions.txt")),
        })
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"source": "Gathin23/cleanyourtv", "commit": sha, "license": "MIT", "themes": themes},
                  f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(themes)} themes from {sha}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
