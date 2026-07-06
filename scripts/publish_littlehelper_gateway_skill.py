#!/usr/bin/env python3
"""Publish littlehelper-modal skill to Gateway via dev session."""
from pathlib import Path
import sys
import openclaw_dev_session as s

ROOT = Path(__file__).resolve().parents[1]
SKILL = ROOT / "scripts" / "skills" / "littlehelper-modal" / "SKILL.md"
CONTRACT = ROOT / "docs" / "OPENCLAW_GATEWAY_CONTRACT.md"
TARGET_WORKSPACE = "~/.openclaw/workspace/skills/littlehelper-modal/SKILL.md"
TARGET_LEGACY = "~/.openclaw/skills/littlehelper-modal.md"

skill_body = (ROOT / "scripts" / "skills" / "littlehelper-modal" / "SKILL.md").read_text(encoding="utf-8")
msg = f"""【固化 LittleHelper 集成契约 · 请写入 Gateway 持久 skill】

请将下列内容写入 OpenClaw **标准 skill 路径**（必须）：

`{TARGET_WORKSPACE}`

并可选备份到 `{TARGET_LEGACY}`。

写完后确认：
1) workspace skill 路径 + 字节数
2) 是否已加入 available_skills / 主 Agent 可读列表（手机侧按 **client.id=openclaw-android** 路由，勿假设 `agent:main:main`）
3) 三条铁律：wire 三线、webview 相对 URL、地图 map.littlehelper.html

---BEGIN SKILL.md ({len(skill_body.encode('utf-8'))} bytes)---
{skill_body}
---END SKILL.md---

勿开新话题。
"""

lan_props = ROOT / "local.properties.lan.tmp"
lan_props.write_text(
    ROOT.joinpath("local.properties")
    .read_text(encoding="utf-8")
    .replace("OPENCLAW_GATEWAY_HOST=100.112.96.116", "OPENCLAW_GATEWAY_HOST=192.168.1.55"),
    encoding="utf-8",
)
s.LOCAL_PROPERTIES = lan_props
sys.argv = [
    "openclaw_dev_session.py",
    "--use-probe-device",
    "--connect-timeout",
    "15",
    "--timeout",
    "240",
    msg,
]
raise SystemExit(s.main())
