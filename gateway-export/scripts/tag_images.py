"""
批量给已保存图片添加/管理标签
用法:
  python scripts/tag_images.py --add cat1,cat2 --match <keyword>
  python scripts/tag_images.py --add cat1,cat2 --fileId <id>
  python scripts/tag_images.py --list
  python scripts/tag_images.py --list --tag <tag>
"""
from __future__ import annotations

import json
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from upload_env import resolve_index_file

INDEX_FILE = resolve_index_file()


def load_index() -> dict:
    if os.path.exists(INDEX_FILE):
        with open(INDEX_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
        if isinstance(data, dict):
            return data
    return {"items": []}


def save_index(index: dict) -> None:
    os.makedirs(os.path.dirname(INDEX_FILE), exist_ok=True)
    with open(INDEX_FILE, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=2)


def main() -> None:
    args = sys.argv[1:]
    tags_to_add: list[str] = []
    match_kw = None
    file_id = None
    list_mode = False
    tag_filter = None

    i = 0
    while i < len(args):
        if args[i] == "--add" and i + 1 < len(args):
            tags_to_add = [t.strip() for t in args[i + 1].split(",") if t.strip()]
            i += 2
        elif args[i] == "--match" and i + 1 < len(args):
            match_kw = args[i + 1]
            i += 2
        elif args[i] == "--fileId" and i + 1 < len(args):
            file_id = args[i + 1]
            i += 2
        elif args[i] == "--list":
            list_mode = True
            i += 1
        elif args[i] == "--tag" and i + 1 < len(args):
            tag_filter = args[i + 1]
            i += 2
        else:
            i += 1

    index = load_index()
    items = index.get("items", [])

    if list_mode:
        if tag_filter:
            matched = [item for item in items if tag_filter in item.get("tags", [])]
            print(
                json.dumps(
                    {
                        "count": len(matched),
                        "items": [
                            {
                                "fileId": item.get("fileId"),
                                "displayName": item.get("displayName"),
                                "tags": item.get("tags", []),
                            }
                            for item in matched
                        ],
                    },
                    ensure_ascii=False,
                    indent=2,
                )
            )
        else:
            all_tags: dict[str, int] = {}
            for item in items:
                for t in item.get("tags", []):
                    all_tags[t] = all_tags.get(t, 0) + 1
            untagged = sum(1 for item in items if not item.get("tags"))
            print(
                json.dumps(
                    {"tags": all_tags, "total": len(items), "untagged": untagged},
                    ensure_ascii=False,
                    indent=2,
                )
            )
        return

    if not tags_to_add:
        print(json.dumps({"error": "缺少 --add 参数"}, ensure_ascii=False))
        sys.exit(1)

    if not match_kw and not file_id:
        print(json.dumps({"error": "缺少 --match 或 --fileId 参数"}, ensure_ascii=False))
        sys.exit(1)

    matched = []
    for item in items:
        if file_id and item.get("fileId") == file_id:
            matched.append(item)
        elif match_kw and match_kw.lower() in item.get("displayName", "").lower():
            matched.append(item)
        elif match_kw and any(match_kw.lower() in t.lower() for t in item.get("tags", [])):
            matched.append(item)

    if not matched:
        print(json.dumps({"error": "未找到匹配的图片", "keyword": match_kw or file_id}, ensure_ascii=False))
        sys.exit(1)

    for item in matched:
        existing = set(item.get("tags", []))
        existing.update(tags_to_add)
        item["tags"] = sorted(existing)

    save_index(index)
    print(
        json.dumps(
            {
                "ok": True,
                "matched": len(matched),
                "tags_added": tags_to_add,
                "items": [
                    {
                        "fileId": m.get("fileId"),
                        "displayName": m.get("displayName"),
                        "tags": m.get("tags"),
                    }
                    for m in matched
                ],
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
