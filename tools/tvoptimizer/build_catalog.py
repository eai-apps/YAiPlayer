#!/usr/bin/env python3
"""Builds assets/tvoptimizer/catalog.json from a checkout of Gathin23/cleanyourtv (MIT).

Usage: python3 -I tools/tvoptimizer/build_catalog.py <cleanyourtv checkout> <output json>

Reads examples/<model>/tv.json and examples/<model>/debloat.txt. A package line is active (the list
disables it); a package inside a comment is kept by that list. The comment header above a block
decides the group shown in the app. Lines under a launcher header are tagged "launcher": YAiPlayer
installs no launcher, so the app never disables them.
"""
import json, os, re, subprocess, sys

PKG = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")
GROUPS = ["ads", "tracking", "preload", "brand", "launcher", "other"]
RULES = [
    ("launcher", ["launcher"]),
    ("tracking", ["telemetry", "analytic", "tracking", "tracker", "acr", "statistic", "feedback", "report",
                  "logging", "diagnos", "privacy", "personalis", "personaliz", "crash", "usage"]),
    ("ads", ["ad ", "ads", "advert", "promo", "content", "recommend", "sponsor", "rows", "patchwall",
             "discover", "channel", "shop", "demo", "retail", "store mode", "hotel"]),
    ("preload", ["preload", "apps", "games", "not needed", "nobody", "extras", "bloat", "trial"]),
    ("brand", ["background", "service", "vendor", "xiaomi", "tcl", "sony", "philips", "hisense", "samsung"]),
]
KEEP_HEADER = re.compile(r"keep|do not disable|load-bearing|kept on purpose", re.I)


def group_of(header):
    h = " " + header.lower() + " "
    for g, words in RULES:
        if any(w in h for w in words):
            return g
    return "other"


def parse(path):
    active, kept = [], []
    header, header_keep = "", False
    for raw in open(path, encoding="utf-8", errors="replace"):
        line = raw.rstrip("\n")
        s = line.strip()
        if not s:
            continue
        commented = s.startswith("#")
        body = s.lstrip("#").strip()
        pkg, _, note = body.partition("#")
        pkg = pkg.strip()
        note = note.strip()
        if PKG.match(pkg) and " " not in pkg:
            if commented:
                kept.append(pkg)
            elif not header_keep:
                active.append((pkg, group_of(header), note))
            else:
                kept.append(pkg)
        elif commented and body and not PKG.match(body.split()[0] if body.split() else ""):
            # A free-text comment line: becomes the header of the next block.
            if len(body) < 80 and not body.startswith(("Apply", "Undo", "Everything", "Known", "Also")):
                header, header_keep = body, bool(KEEP_HEADER.search(body))
        elif not commented and pkg:
            header_keep = False
    return active, kept


def main(src, out):
    ex = os.path.join(src, "examples")
    sha = subprocess.run(["git", "-C", src, "rev-parse", "--short", "HEAD"], capture_output=True, text=True).stdout.strip()
    notes, note_idx = [], {}
    models, keep = [], {}
    for d in sorted(os.listdir(ex)):
        tvj, deb = os.path.join(ex, d, "tv.json"), os.path.join(ex, d, "debloat.txt")
        if not (os.path.isfile(tvj) and os.path.isfile(deb)):
            continue
        meta = json.load(open(tvj, encoding="utf-8"))
        active, kept = parse(deb)
        for k in set(kept):
            keep[k] = keep.get(k, 0) + 1
        rows, seen = [], set()
        for pkg, g, note in active:
            if pkg in seen:
                continue
            seen.add(pkg)
            n = -1
            if note:
                if note not in note_idx:
                    note_idx[note] = len(notes)
                    notes.append(note)
                n = note_idx[note]
            rows.append([pkg, GROUPS.index(g), n])
        if rows:
            models.append({"id": d, "tv": meta.get("tv", d), "brand": meta.get("brand", ""), "os": meta.get("os", ""), "p": rows})
    data = {"source": f"Gathin23/cleanyourtv@{sha}", "license": "MIT", "groups": GROUPS, "notes": notes,
            "keep": dict(sorted(keep.items())), "models": models}
    with open(out, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(models)} models, {sum(len(m['p']) for m in models)} entries, {len(notes)} notes, {os.path.getsize(out)} bytes")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
