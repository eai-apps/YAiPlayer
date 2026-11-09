#!/usr/bin/env python3
# Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
"""Brand customisation tool for YAiPlayer.

Scans the repository for brand identifiers (appName, applicationId, GitHub repo,
Telegram, colors) and previews or applies substitutions.

Default mode is DRY-RUN (safe, prints planned changes without writing).
To apply modifications to files, pass --apply flag.
"""

import argparse
import json
import os
import sys

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

DEFAULT_TARGETS = [
    {
        "file": "clients/android-tv/app/src/main/res/values/yai_brand.xml",
        "replacements": [
            ('>YAiPlayer<', '>{appName}<'),
        ],
    },
    {
        "file": "clients/android-mobile/app/src/main/res/values/yai_brand.xml",
        "replacements": [
            ('>YAiPlayer<', '>{appName}<'),
        ],
    },
    {
        "file": "clients/android-tv/app/build.gradle.kts",
        "replacements": [
            ('applicationId = "com.yai.iptv.player.tv"', 'applicationId = "{appIdTv}"'),
        ],
    },
    {
        "file": "clients/android-mobile/app/build.gradle.kts",
        "replacements": [
            ('applicationId = "com.yai.iptv.player.mobile"', 'applicationId = "{appIdMobile}"'),
        ],
    },
    {
        "file": "clients/android-tv/app/src/main/java/tv/own/owntv/OwnTVApp.kt",
        "replacements": [
            ('releaseRepo = "eai-apps/YAiPlayer"', 'releaseRepo = "{githubOwner}/{githubRepo}"'),
        ],
    },
    {
        "file": "clients/android-mobile/app/src/main/java/tv/own/owntv/mobile/OwnTVMobileApp.kt",
        "replacements": [
            ('releaseRepo = "eai-apps/YAiPlayer"', 'releaseRepo = "{githubOwner}/{githubRepo}"'),
        ],
    },
    {
        "file": "tools/brand/yai_brand.py",
        "replacements": [
            ('BRAND_NAME = "YAiPlayer"', 'BRAND_NAME = "{appName}"'),
        ],
    },
]


def load_config(config_path: str) -> dict:
    with open(config_path, "r", encoding="utf-8") as f:
        return json.load(f)


def resolve_replacement(pattern: str, config: dict) -> str:
    app_id_tv = config.get("applicationId", {}).get("tv", "com.example.tv") if isinstance(config.get("applicationId"), dict) else "com.example.tv"
    app_id_mobile = config.get("applicationId", {}).get("mobile", "com.example.mobile") if isinstance(config.get("applicationId"), dict) else "com.example.mobile"
    return pattern.format(
        appName=config.get("appName", "CustomPlayer"),
        appIdTv=app_id_tv,
        appIdMobile=app_id_mobile,
        githubOwner=config.get("githubOwner", "my-org"),
        githubRepo=config.get("githubRepo", "my-repo"),
        primaryColor=config.get("primaryColor", "#5B3FD6"),
        accentColor=config.get("accentColor", "#22D3EE"),
    )


def process_targets(targets, config, apply: bool):
    mode_str = "APPLY" if apply else "DRY-RUN"
    print(f"=== Rebrand Process: {mode_str} Mode ===")
    print(f"Target App Name: {config.get('appName')}")
    print(f"Repository Root: {REPO_ROOT}\n")

    total_files = 0
    total_substitutions = 0

    for target in targets:
        rel_path = target["file"]
        full_path = os.path.join(REPO_ROOT, rel_path)

        if not os.path.exists(full_path):
            print(f"[SKIP] File not found: {rel_path}")
            continue

        with open(full_path, "r", encoding="utf-8") as f:
            content = f.read()

        modified_content = content
        file_changed = False

        print(f"--- Checking {rel_path} ---")
        for old_pattern, new_pattern_tmpl in target["replacements"]:
            new_pattern = resolve_replacement(new_pattern_tmpl, config)
            occurrences = content.count(old_pattern)
            if occurrences > 0:
                print(f"  [MATCH] Found {occurrences} occurrence(s) of:")
                print(f"     Old: {old_pattern}")
                print(f"     New: {new_pattern}")
                modified_content = modified_content.replace(old_pattern, new_pattern)
                total_substitutions += occurrences
                file_changed = True
            else:
                print(f"  [NO MATCH] Pattern not found: {old_pattern}")

        if file_changed:
            total_files += 1
            if apply:
                with open(full_path, "w", encoding="utf-8") as f:
                    f.write(modified_content)
                print(f"  => Applied changes to {rel_path}")
            else:
                print(f"  => [DRY-RUN] Would update {rel_path}")
        print()

    print("=== Summary ===")
    print(f"Files matched: {total_files}")
    print(f"Total substitutions: {total_substitutions}")
    if not apply:
        print("[DRY-RUN COMPLETE] No files were changed on disk. To execute changes, run with --apply.")
    else:
        print("[APPLY COMPLETE] Files updated successfully.")


def main():
    parser = argparse.ArgumentParser(description="Preview or apply rebranding substitutions to YAiPlayer repository.")
    parser.add_argument("--config", default=os.path.join(os.path.dirname(__file__), "brand.example.json"), help="Path to brand JSON configuration")
    parser.add_argument("--apply", action="store_true", default=False, help="Apply changes to disk (default: false, dry-run only)")
    args = parser.parse_args()

    if not os.path.exists(args.config):
        print(f"Error: configuration file '{args.config}' does not exist.", file=sys.stderr)
        sys.exit(1)

    config = load_config(args.config)
    process_targets(DEFAULT_TARGETS, config, apply=args.apply)


if __name__ == "__main__":
    main()
