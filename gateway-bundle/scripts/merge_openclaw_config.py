#!/usr/bin/env python3
"""Deep-merge skills.load.extraDirs from openclaw.merge.json into ~/.openclaw/openclaw.json."""
from __future__ import annotations

import argparse
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def deep_merge(base: dict[str, Any], patch: dict[str, Any]) -> dict[str, Any]:
    out = dict(base)
    for key, value in patch.items():
        if key.startswith("_"):
            continue
        if isinstance(value, dict) and isinstance(out.get(key), dict):
            out[key] = deep_merge(out[key], value)
        elif isinstance(value, dict):
            out[key] = deep_merge({}, value)
        elif isinstance(value, list) and isinstance(out.get(key), list):
            merged = list(out[key])
            for item in value:
                if item not in merged:
                    merged.append(item)
            out[key] = merged
        else:
            out[key] = value
    return out


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Merge skills.load.extraDirs from openclaw.merge.json into user config",
    )
    parser.add_argument(
        "--merge-file",
        type=Path,
        required=True,
        help="Path to openclaw.merge.json",
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=Path.home() / ".openclaw" / "openclaw.json",
        help="Target openclaw.json (default: ~/.openclaw/openclaw.json)",
    )
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    merge_payload = json.loads(args.merge_file.read_text(encoding="utf-8-sig"))
    config_path = args.config
    config_path.parent.mkdir(parents=True, exist_ok=True)

    if config_path.exists():
        base = json.loads(config_path.read_text(encoding="utf-8-sig"))
    else:
        base = {}

    merged = deep_merge(base, merge_payload)

    if args.dry_run:
        print(json.dumps(merged, indent=2, ensure_ascii=False))
        return 0

    if config_path.exists():
        ts = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        backup = config_path.with_suffix(f".json.bak.{ts}")
        shutil.copy2(config_path, backup)
        print(f"Backed up: {backup}")

    config_path.write_text(json.dumps(merged, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Merged skills.load.extraDirs into: {config_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
