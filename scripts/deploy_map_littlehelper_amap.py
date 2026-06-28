#!/usr/bin/env python3
"""Deploy map.littlehelper.html with Amap nav buttons to Gateway."""
from pathlib import Path
import sys
import openclaw_dev_session as s

ROOT = Path(__file__).resolve().parents[1]
HTML = ROOT / "scripts" / "canvas_map_littlehelper.html"
body = HTML.read_text(encoding="utf-8")
msg = f"""【LittleHelper · 高德单按钮规范 + 部署 map.littlehelper.html】

App 已改为仅一个按钮「用高德地图查看」。Gateway 通过 window.__LITTLEHELPER_MAP__ 决定跳转参数。
问路/导航场景请用 action:"route" 或 amapUrl（amapuri://route/plan/...），不要只用终点 viewMap。

请写入 ~/.openclaw/canvas/map.littlehelper.html（覆盖），并确认动态路线页也按 __LITTLEHELPER_MAP__ 规范生成。

---BEGIN map.littlehelper.html ({len(body.encode('utf-8'))} bytes)---
{body}
---END---

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
