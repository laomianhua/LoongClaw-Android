#!/usr/bin/env python3
"""Health checks after companion bundle install."""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path


def expand(p: str | Path) -> Path:
    return Path(str(p).replace("~", str(Path.home()))).expanduser()


def resolve_state_root(cli_value: str | None) -> Path:
    if cli_value:
        return expand(cli_value)
    env_val = os.environ.get("OPENCLAW_STATE_DIR")
    if env_val:
        return expand(env_val)
    return Path.home() / ".openclaw"


def resolve_workspace_root(state_root: Path, cli_value: str | None) -> Path:
    if cli_value:
        return expand(cli_value)
    env_val = os.environ.get("OPENCLAW_WORKSPACE")
    if env_val:
        return expand(env_val)
    return state_root / "workspace"


def check(name: str, ok: bool, detail: str = "") -> bool:
    mark = "OK" if ok else "FAIL"
    line = f"[{mark}] {name}"
    if detail:
        line += f" — {detail}"
    print(line)
    return ok


def http_ok(url: str, timeout: float = 3.0) -> tuple[bool, str]:
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            return resp.status == 200, f"HTTP {resp.status}"
    except urllib.error.HTTPError as e:
        return False, f"HTTP {e.code}"
    except Exception as e:
        return False, str(e)


def gateway_reachable(host: str, port: int) -> tuple[bool, str]:
    url = f"http://{host}:{port}/"
    try:
        with urllib.request.urlopen(url, timeout=3.0) as resp:
            return True, f"HTTP {resp.status}"
    except urllib.error.HTTPError as e:
        if e.code in (200, 301, 302, 404, 401, 403):
            return True, f"HTTP {e.code} (Gateway listening)"
        return False, f"HTTP {e.code}"
    except Exception as e:
        return False, str(e)


def check_loongclaw_desktop_patch() -> tuple[bool, str]:
    """Match gateway patch_clientid.ps1: LOONGCLAW_DESKTOP in client-info JS."""
    if sys.platform != "win32":
        return True, "skipped (patch_clientid.ps1 is Windows-only)"
    appdata = os.environ.get("APPDATA")
    if not appdata:
        return False, "APPDATA not set"
    dist = Path(appdata) / "npm" / "node_modules" / "openclaw" / "dist"
    if not dist.is_dir():
        return False, f"OpenClaw dist not found: {dist}"
    for js in dist.glob("client-info-*.js"):
        text = js.read_text(encoding="utf-8", errors="replace")
        if "ANDROID_APP" not in text:
            continue
        if "LOONGCLAW_DESKTOP" in text:
            return True, js.name
        return False, f"{js.name} missing LOONGCLAW_DESKTOP — run scripts/patch_clientid.ps1"
    return False, "no client-info-*.js with GATEWAY_CLIENT_IDS"


def resolve_upload_port(state_root: Path, default: int) -> int:
    env = os.environ.get("UPLOAD_PORT")
    if env:
        return int(env)
    norm = str(state_root).replace("\\", "/").rstrip("/")
    if norm.endswith("openclaw-dev"):
        return 18890
    return default


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--bundle-dir",
        type=Path,
        default=Path(__file__).resolve().parents[1],
    )
    parser.add_argument("--gateway-host", default="127.0.0.1")
    parser.add_argument("--gateway-port", type=int, default=18789)
    parser.add_argument("--upload-port", type=int, default=18889)
    parser.add_argument("--profile", choices=["minimal", "standard"], default="standard")
    parser.add_argument(
        "--openclaw-state-dir",
        help="OpenClaw state root (default: OPENCLAW_STATE_DIR env, else ~/.openclaw)",
    )
    parser.add_argument(
        "--openclaw-workspace",
        help="Agent workspace root (default: OPENCLAW_WORKSPACE env, else {state}/workspace)",
    )
    args = parser.parse_args()

    state_root = resolve_state_root(args.openclaw_state_dir)
    workspace_root = resolve_workspace_root(state_root, args.openclaw_workspace)
    openclaw_json = state_root / "openclaw.json"
    companion_dir = state_root / "companion"

    bundle_dir = args.bundle_dir.resolve()
    manifest_path = bundle_dir / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {}
    doctor_cfg = manifest.get("doctor", {})

    print(f"State dir:      {state_root}")
    print(f"Workspace dir:  {workspace_root}")

    passed = 0
    total = 0

    def run(name: str, ok: bool, detail: str = "") -> None:
        nonlocal passed, total
        total += 1
        if check(name, ok, detail):
            passed += 1

    run("openclaw.json exists", openclaw_json.exists(), str(openclaw_json))
    if openclaw_json.exists():
        try:
            cfg = json.loads(openclaw_json.read_text(encoding="utf-8-sig"))
            extra_dirs = cfg.get("skills", {}).get("load", {}).get("extraDirs", [])
            skills_root = state_root / "shared-skills"
            has_shared = any(str(skills_root).replace("\\", "/") in str(d).replace("\\", "/") for d in extra_dirs)
            run("skills.load.extraDirs includes shared-skills", has_shared, str(extra_dirs))
        except json.JSONDecodeError as e:
            run("openclaw.json parses as JSON", False, str(e))

    gw_port = args.gateway_port
    host = args.gateway_host

    ok, detail = gateway_reachable(host, gw_port)
    run(f"Gateway reachable :{gw_port}", ok, detail)

    ok_pc, detail_pc = check_loongclaw_desktop_patch()
    if ok_pc and "skipped" not in detail_pc:
        print(f"[INFO] PC client patch (optional): {detail_pc}")
    elif not ok_pc and "skipped" not in detail_pc:
        print(f"[INFO] PC client patch not applied ({detail_pc}) — OK for Android-only install")

    canvas_files = doctor_cfg.get("canvasFiles") or []

    skill_path = state_root / "shared-skills" / "littlehelper-modal" / "SKILL.md"
    legacy_skill_path = workspace_root / "skills" / "littlehelper-modal" / "SKILL.md"
    min_bytes = doctor_cfg.get("skillMinBytes", {}).get("littlehelper-modal/SKILL.md", 4000)
    if skill_path.exists():
        size = skill_path.stat().st_size
        run("shared-skills/littlehelper-modal/SKILL.md", size >= min_bytes, f"{size} bytes")
    else:
        run("shared-skills/littlehelper-modal/SKILL.md", False, "not found")
    if legacy_skill_path.exists() and not skill_path.exists():
        print(
            "[WARN] workspace/skills/littlehelper-modal/ still present — "
            "consider moving to shared-skills/ and enabling skills.load.extraDirs"
        )

    if canvas_files:
        for canvas_file in canvas_files:
            local = state_root / "canvas" / canvas_file
            run(f"canvas file {canvas_file}", local.exists(), str(local) if local.exists() else "missing")

        canvas_name = canvas_files[0]
        canvas_url = f"http://{host}:{gw_port}/__openclaw__/canvas/{canvas_name}"
        ok, detail = http_ok(canvas_url)
        run(f"GET {canvas_url}", ok, detail)

    if args.profile == "standard":
        upload_port = resolve_upload_port(
            state_root,
            doctor_cfg.get("uploadPort", args.upload_port),
        )
        health_url = f"http://{host}:{upload_port}/health"
        ok, detail = http_ok(health_url)
        sidecar_hint = companion_dir / "start-upload-sidecar.bat"
        if not sidecar_hint.exists():
            sidecar_hint = companion_dir / "upload_server.py"
        run(
            f"GET {health_url}",
            ok,
            detail + (f" (start: {sidecar_hint})" if not ok else ""),
        )

        gallery = workspace_root / "scripts" / "generate_gallery.py"
        if gallery.exists():
            run("workspace/scripts/generate_gallery.py", True, str(gallery))
        else:
            print("[WARN] workspace/scripts/generate_gallery.py missing")

        save_script = workspace_root / "scripts" / "save_to_storage.py"
        run("workspace/scripts/save_to_storage.py", save_script.exists(), str(save_script) if save_script.exists() else "missing")

        viewer = workspace_root / "scripts" / "file_viewer.py"
        run("workspace/scripts/file_viewer.py", viewer.exists(), str(viewer) if viewer.exists() else "missing")

        storage_index = workspace_root / "storage" / "index.json"
        if storage_index.exists():
            try:
                idx = json.loads(storage_index.read_text(encoding="utf-8"))
                ok_index = isinstance(idx, dict) and isinstance(idx.get("items"), list)
                run("workspace/storage/index.json schema", ok_index, '{"items": [...]}' if ok_index else "invalid")
            except json.JSONDecodeError as e:
                run("workspace/storage/index.json schema", False, str(e))
        else:
            run("workspace/storage/index.json", False, "missing (re-run install)")

    print(f"\n{passed}/{total} checks passed")
    return 0 if passed == total else 1


if __name__ == "__main__":
    raise SystemExit(main())
