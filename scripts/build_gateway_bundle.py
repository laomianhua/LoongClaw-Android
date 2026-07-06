#!/usr/bin/env python3
"""Assemble loongclaw-gateway-bundle zip from repo sources."""
from __future__ import annotations

import json
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUNDLE_SRC = ROOT / "gateway-bundle"
DIST = ROOT / "dist"


def _skip_bundle_artifact(rel: Path) -> bool:
    return "__pycache__" in rel.parts or rel.suffix == ".pyc"


def copy_tree(src: Path, dst: Path) -> None:
    if not src.exists():
        return
    if src.is_file():
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        return
    for item in src.rglob("*"):
        if item.is_dir():
            continue
        rel = item.relative_to(src)
        if _skip_bundle_artifact(rel):
            continue
        target = dst / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(item, target)


def main() -> int:
    manifest = json.loads((BUNDLE_SRC / "manifest.json").read_text(encoding="utf-8"))
    version = manifest["bundleVersion"]
    out_name = f"loongclaw-gateway-bundle-{version}"
    stage = DIST / out_name
    if stage.exists():
        shutil.rmtree(stage)
    stage.mkdir(parents=True)

    # Static bundle files (exclude repo-only scripts subfolder used at build time)
    for name in [
        "manifest.json",
        "README.md",
        "install.ps1",
        "install.sh",
        "doctor.ps1",
        "doctor.sh",
    ]:
        src = BUNDLE_SRC / name
        if src.exists():
            shutil.copy2(src, stage / name)

    copy_tree(BUNDLE_SRC / "config", stage / "config")
    copy_tree(BUNDLE_SRC / "agents", stage / "agents")
    copy_tree(BUNDLE_SRC / "sidecar", stage / "sidecar")
    copy_tree(BUNDLE_SRC / "canvas", stage / "canvas")
    upload_env = ROOT / "gateway-export" / "scripts" / "upload_env.py"
    if upload_env.exists():
        shutil.copy2(upload_env, stage / "sidecar" / "upload_env.py")
    shutil.copytree(
        BUNDLE_SRC / "scripts",
        stage / "scripts",
        ignore=shutil.ignore_patterns("__pycache__", "*.pyc"),
    )

    # Canonical sources from repo
    skill_src = ROOT / "scripts" / "skills" / "littlehelper-modal"
    skill_dst = stage / "skills" / "littlehelper-modal"
    copy_tree(skill_src, skill_dst)

    # Gateway export: scripts/ (canonical) or legacy workspace/scripts/
    export_scripts = None
    for candidate in (
        ROOT / "gateway-export" / "scripts",
        ROOT / "gateway-export" / "workspace" / "scripts",
    ):
        if candidate.exists():
            export_scripts = candidate
            break
    if export_scripts is not None:
        copy_tree(export_scripts, stage / "workspace" / "scripts")

    # Merge extra skills from gateway-export/skills/
    export_skills_root = ROOT / "gateway-export" / "skills"
    if export_skills_root.exists():
        for child in export_skills_root.iterdir():
            if child.is_dir():
                copy_tree(child, stage / "skills" / child.name)

    zip_path = DIST / f"{out_name}.zip"
    if zip_path.exists():
        zip_path.unlink()
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for file in stage.rglob("*"):
            if file.is_file():
                zf.write(file, file.relative_to(stage).as_posix())

    print(f"Staged: {stage}")
    print(f"Zip:    {zip_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
