#!/usr/bin/env python3
"""Send webview_spec_test.html content to Gateway dev session for deployment."""
from pathlib import Path
import openclaw_dev_session as s

ROOT = Path(__file__).resolve().parents[1]
html = (ROOT / "scripts" / "webview_spec_test.html").read_text(encoding="utf-8")
msg = f"""续接上一话题：文件内容如下，请立即写入 canvas/webview_spec_test.html（即 ~/.openclaw/canvas/webview_spec_test.html）。

写入后请：
1) curl 验证 HTTP 200
2) 确认 App 用户会话触发话术：「打开 WebView 能力测试」
3) 回复 A/B/C/D 四项确认

---BEGIN webview_spec_test.html ({len(html.encode('utf-8'))} bytes)---
{html}
---END webview_spec_test.html---
"""

lan_props = ROOT / "local.properties.lan.tmp"
lan_props.write_text(
    ROOT.joinpath("local.properties")
    .read_text(encoding="utf-8")
    .replace("OPENCLAW_GATEWAY_HOST=100.112.96.116", "OPENCLAW_GATEWAY_HOST=192.168.1.55"),
    encoding="utf-8",
)
s.LOCAL_PROPERTIES = lan_props
import sys

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
