#!/usr/bin/env python3
"""从用户消息中的 [upload:fileId:fileName] 标记拉取并打印文件内容。"""

from __future__ import annotations

import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

UPLOAD_MARKER_RE = re.compile(r"\[upload:([0-9a-fA-F]{32}):([^\]]+)\]")
DEFAULT_BASE = "http://127.0.0.1:18889"


def extract_upload_marker(text: str) -> tuple[str, str] | None:
    match = UPLOAD_MARKER_RE.search(text)
    if not match:
        return None
    return match.group(1), match.group(2)


def fetch_upload_bytes(base_url: str, file_id: str) -> bytes:
    url = f"{base_url.rstrip('/')}/files/{file_id}"
    with urllib.request.urlopen(url, timeout=30) as response:
        return response.read()


def main() -> int:
    if len(sys.argv) < 2:
        print("Usage: python scripts/process_upload.py '<message with [upload:fileId:fileName]>'")
        return 1

    message = sys.argv[1]
    marker = extract_upload_marker(message)
    if marker is None:
        print("No [upload:fileId:fileName] marker found.")
        return 1

    file_id, file_name = marker
    base_url = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_BASE
    try:
        data = fetch_upload_bytes(base_url, file_id)
    except urllib.error.HTTPError as error:
        print(f"Fetch failed: HTTP {error.code}")
        return 1
    except urllib.error.URLError as error:
        print(f"Fetch failed: {error.reason}")
        return 1

    text = data.decode("utf-8", errors="replace")
    print(f"fileId: {file_id}")
    print(f"fileName: {file_name}")
    print(f"content: {text!r}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
