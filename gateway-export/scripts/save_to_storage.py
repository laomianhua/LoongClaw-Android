"""
将文件从 uploads 永久保存到 storage 目录
用法: python scripts/save_to_storage.py <fileId> [显示名称] [--tags tag1,tag2]
"""
from __future__ import annotations

import json
import os
import shutil
import sys
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from upload_env import resolve_index_file, resolve_storage_dir, resolve_upload_dir

UPLOAD_DIR = resolve_upload_dir()
STORAGE_DIR = resolve_storage_dir()
INDEX_FILE = resolve_index_file()
os.makedirs(STORAGE_DIR, exist_ok=True)


def load_index() -> dict:
    if os.path.exists(INDEX_FILE):
        with open(INDEX_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
        if isinstance(data, dict):
            return data
    return {"items": []}


def save_index(index: dict) -> None:
    with open(INDEX_FILE, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=2)


def resolve_path(search_id: str) -> str | None:
    if not os.path.isdir(UPLOAD_DIR):
        return None
    for fname in os.listdir(UPLOAD_DIR):
        if fname.startswith(search_id):
            return os.path.join(UPLOAD_DIR, fname)
    return None


def main() -> None:
    if len(sys.argv) < 2:
        print(json.dumps({"error": "缺少 fileId"}, ensure_ascii=False))
        sys.exit(1)

    file_id = sys.argv[1]
    tags: list[str] = []
    display_name = file_id
    args = sys.argv[2:]
    idx = 0
    while idx < len(args):
        if args[idx] == "--tags" and idx + 1 < len(args):
            tags = [t.strip() for t in args[idx + 1].split(",") if t.strip()]
            idx += 2
        else:
            display_name = args[idx]
            idx += 1

    src = resolve_path(file_id)
    if not src:
        print(json.dumps({"error": f"未找到文件: {file_id}"}, ensure_ascii=False))
        sys.exit(1)

    basename = os.path.basename(src)
    dst = os.path.join(STORAGE_DIR, basename)
    if not os.path.exists(dst):
        shutil.copy2(src, dst)

    index = load_index()
    index["items"] = [i for i in index.get("items", []) if i.get("fileId") != file_id]
    entry = {
        "fileId": file_id,
        "fileName": basename,
        "displayName": display_name,
        "savedAt": datetime.now().isoformat(),
        "path": dst,
    }
    if tags:
        entry["tags"] = tags
    index["items"].append(entry)
    save_index(index)

    result = {
        "ok": True,
        "fileId": file_id,
        "displayName": display_name,
        "tags": tags,
        "savedPath": dst,
        "note": f"已保存：{display_name}" + (f"（标签：{','.join(tags)}）" if tags else ""),
    }
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
