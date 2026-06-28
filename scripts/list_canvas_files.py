#!/usr/bin/env python3
import re
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
props = {}
for line in (ROOT / "local.properties").read_text(encoding="utf-8").splitlines():
    if "=" in line and not line.strip().startswith("#"):
        k, v = line.split("=", 1)
        props[k.strip()] = v.strip()
host, port = "192.168.1.55", props.get("OPENCLAW_GATEWAY_PORT", "18789")
token = props.get("OPENCLAW_GATEWAY_TOKEN", props.get("OPENCLAW_GATEWAY_PASSWORD", ""))

req = urllib.request.Request(
    f"http://{host}:{port}/__openclaw__/canvas/",
    headers={"Authorization": f"Bearer {token}"},
)
with urllib.request.urlopen(req, timeout=10) as r:
    html = r.read().decode("utf-8", errors="replace")
links = re.findall(r'href="([^"]+\.html)"', html)
print("canvas html files:", links)
