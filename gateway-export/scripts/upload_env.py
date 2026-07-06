"""Upload sidecar and workspace script paths from OPENCLAW_STATE_DIR / OPENCLAW_WORKSPACE."""
from __future__ import annotations

import os


def _norm(path: str) -> str:
    return path.replace("\\", "/").rstrip("/")


def resolve_state_dir() -> str:
    state = os.environ.get("OPENCLAW_STATE_DIR")
    if state:
        return os.path.expanduser(state)
    return os.path.expanduser("~/.openclaw")


def resolve_workspace_dir() -> str:
    ws = os.environ.get("OPENCLAW_WORKSPACE")
    if ws:
        return os.path.expanduser(ws)
    return os.path.join(resolve_state_dir(), "workspace")


def resolve_upload_dir() -> str:
    return os.path.join(resolve_state_dir(), "uploads")


def resolve_storage_dir() -> str:
    return os.path.join(resolve_workspace_dir(), "storage")


def resolve_canvas_dir() -> str:
    return os.path.join(resolve_state_dir(), "canvas")


def resolve_index_file() -> str:
    return os.path.join(resolve_storage_dir(), "index.json")


def resolve_upload_port() -> int:
    env = os.environ.get("UPLOAD_PORT")
    if env:
        return int(env)
    state = os.environ.get("OPENCLAW_STATE_DIR")
    if state and _norm(state).endswith("openclaw-dev"):
        return 18890
    return 18889
