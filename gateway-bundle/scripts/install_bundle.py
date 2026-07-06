#!/usr/bin/env python3
"""Install LoongClaw Gateway Companion Bundle into an OpenClaw state directory."""
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
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


def ensure_extra_dirs(config_path: Path, skills_root: Path) -> None:
    """Point skills.load.extraDirs at the installed shared-skills path."""
    if not config_path.exists():
        return
    cfg = json.loads(config_path.read_text(encoding="utf-8-sig"))
    skills = cfg.setdefault("skills", {})
    load = skills.setdefault("load", {})
    load["extraDirs"] = [str(skills_root).replace("\\", "/")]
    config_path.write_text(json.dumps(cfg, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"  + skills.load.extraDirs -> {skills_root}")


def copy_file(src: Path, dst: Path) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)
    print(f"  + {dst}")


def write_sidecar_starter(companion_dir: Path, state_root: Path) -> None:
    state = str(state_root)
    bat = companion_dir / "start-upload-sidecar.bat"
    bat.write_text(
        "@echo off\r\n"
        'cd /d "%~dp0"\r\n'
        f'set "OPENCLAW_STATE_DIR={state}"\r\n'
        'python "%~dp0upload_server.py"\r\n',
        encoding="utf-8",
    )
    print(f"  + {bat}")

    ps1 = companion_dir / "start-upload-sidecar.ps1"
    ps1.write_text(
        "$ErrorActionPreference = 'Stop'\r\n"
        f"$env:OPENCLAW_STATE_DIR = '{state}'\r\n"
        "Set-Location $PSScriptRoot\r\n"
        "& python (Join-Path $PSScriptRoot 'upload_server.py')\r\n",
        encoding="utf-8-sig",
    )
    print(f"  + {ps1}")


def _skip_bundle_artifact(rel: Path) -> bool:
    return "__pycache__" in rel.parts or rel.suffix == ".pyc"


def copy_dir(src: Path, dst: Path) -> None:
    if not src.exists():
        return
    for item in src.rglob("*"):
        if item.is_file():
            rel = item.relative_to(src)
            if _skip_bundle_artifact(rel):
                continue
            target = dst / rel
            copy_file(item, target)


AGENTS_INJECT_MARKER = "<!-- loongclaw-gateway-bundle:v2 -->"
LEGACY_AGENTS_MARKERS = ("<!-- loongclaw-gateway-bundle:v1 -->",)


def _strip_legacy_agents_inject(text: str) -> str:
    """Remove a prior bundle inject block (assumed appended at EOF)."""
    for legacy in LEGACY_AGENTS_MARKERS:
        idx = text.find(legacy)
        if idx >= 0:
            return text[:idx].rstrip()
    return text


def ensure_storage_index(workspace_root: Path, bundle_dir: Path) -> None:
    """Create workspace/storage/index.json seed when missing."""
    storage_dir = workspace_root / "storage"
    storage_dir.mkdir(parents=True, exist_ok=True)
    index_file = storage_dir / "index.json"
    if index_file.exists():
        print(f"  [=] {index_file} already exists")
        return
    seed = bundle_dir / "canvas" / "index.json.seed"
    if seed.exists():
        copy_file(seed, index_file)
    else:
        index_file.write_text('{"items":[]}\n', encoding="utf-8")
        print(f"  + {index_file}")


def inject_agents_md(bundle_dir: Path, workspace_root: Path) -> None:
    """Append LoongClaw client-routing rules to workspace AGENTS.md (idempotent)."""
    snippet_path = bundle_dir / "agents" / "AGENTS.inject.md"
    if not snippet_path.exists():
        print("  WARN: agents/AGENTS.inject.md missing; skip AGENTS.md injection")
        return

    snippet = snippet_path.read_text(encoding="utf-8").strip()
    if AGENTS_INJECT_MARKER not in snippet:
        snippet = f"{AGENTS_INJECT_MARKER}\n\n{snippet.lstrip()}"

    agents_md = workspace_root / "AGENTS.md"
    workspace_root.mkdir(parents=True, exist_ok=True)

    if agents_md.exists():
        existing = agents_md.read_text(encoding="utf-8")
        if AGENTS_INJECT_MARKER in existing:
            print(f"  [=] {agents_md} already has LoongClaw routing rules (v2)")
            return
        if any(m in existing for m in LEGACY_AGENTS_MARKERS):
            existing = _strip_legacy_agents_inject(existing)
            agents_md.write_text(existing.rstrip() + "\n\n" + snippet + "\n", encoding="utf-8")
            print(f"  + upgraded LoongClaw rules in {agents_md} (v1 -> v2)")
            return
        agents_md.write_text(existing.rstrip() + "\n\n" + snippet + "\n", encoding="utf-8")
        print(f"  + appended LoongClaw rules to {agents_md}")
    else:
        agents_md.write_text(snippet + "\n", encoding="utf-8")
        print(f"  + created {agents_md}")


def run_client_patch(bundle_dir: Path) -> int:
    """Run Gateway patch_clientid.ps1 (Windows npm global OpenClaw dist only)."""
    ps1 = bundle_dir / "scripts" / "patch_clientid.ps1"
    if sys.platform != "win32":
        print("  [SKIP] patch_clientid.ps1 is Windows-only (%APPDATA%\\npm\\node_modules\\openclaw)")
        return 0
    if not ps1.exists():
        print("  ERROR: patch_clientid.ps1 missing", file=sys.stderr)
        return 1
    return subprocess.call(
        [
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(ps1),
        ],
        cwd=str(bundle_dir),
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--bundle-dir",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="Root of extracted bundle (parent of manifest.json)",
    )
    parser.add_argument(
        "--profile",
        choices=["minimal", "standard"],
        default="standard",
    )
    parser.add_argument("--skip-config", action="store_true")
    parser.add_argument(
        "--with-pc-patch",
        action="store_true",
        help="Windows only: patch OpenClaw JS for loongclaw-desktop (not needed for Android App)",
    )
    parser.add_argument(
        "--openclaw-state-dir",
        help="OpenClaw state root (default: OPENCLAW_STATE_DIR env, else ~/.openclaw)",
    )
    parser.add_argument(
        "--openclaw-workspace",
        help="Agent workspace root for AGENTS.md + scripts (default: OPENCLAW_WORKSPACE env, else {state}/workspace)",
    )
    args = parser.parse_args()

    bundle_dir = args.bundle_dir.resolve()
    manifest_path = bundle_dir / "manifest.json"
    if not manifest_path.exists():
        print(f"ERROR: manifest.json not found in {bundle_dir}", file=sys.stderr)
        return 1

    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    profile = manifest.get("profiles", {}).get(args.profile, {})

    state_root = resolve_state_root(args.openclaw_state_dir)
    workspace_root = resolve_workspace_root(state_root, args.openclaw_workspace)
    skills_root = state_root / "shared-skills"
    canvas_root = state_root / "canvas"
    scripts_root = workspace_root / "scripts"
    companion_dir = state_root / "companion"
    openclaw_json = state_root / "openclaw.json"

    print(f"Installing {manifest.get('bundleId')} v{manifest.get('bundleVersion')} profile={args.profile}")
    print(f"Bundle dir:     {bundle_dir}")
    print(f"State dir:      {state_root}")
    print(f"Workspace dir:  {workspace_root}")

    print("\n[1/5] Skills")
    for skill_name in profile.get("skills", []):
        src = bundle_dir / "skills" / skill_name
        if not src.exists():
            print(f"  WARN: missing skill {skill_name}")
            continue
        copy_dir(src, skills_root / skill_name)

    print("\n[2/5] Canvas static assets")
    canvas_files = profile.get("canvas", [])
    if not canvas_files:
        print("  (none in profile)")
    for canvas_file in canvas_files:
        src = bundle_dir / "canvas" / canvas_file
        if not src.exists():
            print(f"  WARN: missing canvas {canvas_file}")
            continue
        copy_file(src, canvas_root / canvas_file)

    if profile.get("workspaceScripts"):
        print("\n[3/5] Workspace scripts")
        ws_src = bundle_dir / "workspace" / "scripts"
        if ws_src.exists():
            copy_dir(ws_src, scripts_root)
        else:
            print("  WARN: no workspace/scripts in bundle (harvest from Gateway — see gateway-export/)")
    else:
        print("\n[3/5] Workspace scripts skipped (minimal profile)")

    if profile.get("workspaceScripts"):
        print("\n[+] Storage index seed")
        ensure_storage_index(workspace_root, bundle_dir)

    if profile.get("sidecar"):
        print("\n[4/5] Upload sidecar")
        sidecar_src = bundle_dir / "sidecar" / "upload_server.py"
        if sidecar_src.exists():
            copy_file(sidecar_src, companion_dir / "upload_server.py")
            upload_env_src = bundle_dir / "sidecar" / "upload_env.py"
            if upload_env_src.exists():
                copy_file(upload_env_src, companion_dir / "upload_env.py")
            write_sidecar_starter(companion_dir, state_root)
            readme = bundle_dir / "sidecar" / "README.md"
            if readme.exists():
                copy_file(readme, companion_dir / "README.md")
        else:
            print("  WARN: sidecar/upload_server.py missing")
    else:
        print("\n[4/5] Upload sidecar skipped (minimal profile)")

    agents_src = bundle_dir / "agents"
    if agents_src.exists():
        print("\n[+] Agent routing notes")
        copy_dir(agents_src, companion_dir / "agents")

    print("\n[+] AGENTS.md client-routing rules")
    inject_agents_md(bundle_dir, workspace_root)

    if args.with_pc_patch:
        print("\n[+] Patch loongclaw-desktop client.id (PC client only)")
        rc = run_client_patch(bundle_dir)
        if rc != 0:
            print("  ERROR: patch_clientid failed — see config/CLIENT_ID_SETUP.md", file=sys.stderr)
            return rc
    else:
        print("\n[·] Skipping patch_clientid (Android App uses built-in openclaw-android; use --with-pc-patch for PC client)")

    if not args.skip_config:
        print(f"\n[+] Merge skills.load.extraDirs into {openclaw_json}")
        merge_script = bundle_dir / "scripts" / "merge_openclaw_config.py"
        merge_file = bundle_dir / "config" / "openclaw.merge.json"
        if merge_script.exists() and merge_file.exists():
            cmd = [
                sys.executable,
                str(merge_script),
                "--merge-file",
                str(merge_file),
                "--config",
                str(openclaw_json),
            ]
            rc = subprocess.call(cmd)
            if rc != 0:
                return rc
            ensure_extra_dirs(openclaw_json, skills_root)
        else:
            print("  WARN: merge script missing; merge skills.load.extraDirs manually")

    print("\nDone. Restart OpenClaw Gateway, start upload sidecar if using standard profile:")
    print(f'  cmd:  "{companion_dir / "start-upload-sidecar.bat"}"')
    print(f'  ps1:  & "{companion_dir / "start-upload-sidecar.ps1"}"')
    print("(Starters are in companion/, not in the extracted bundle sidecar/ folder.)")
    print("Then run doctor.ps1 with the same -OpenClawStateDir if non-default.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
