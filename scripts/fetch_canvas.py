#!/usr/bin/env python3
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
props = {}
for line in (ROOT / "local.properties").read_text(encoding="utf-8").splitlines():
    if "=" in line and not line.strip().startswith("#"):
        k, v = line.split("=", 1)
        props[k.strip()] = v.strip()

port = props.get("OPENCLAW_GATEWAY_PORT", "18789")
token = props.get("OPENCLAW_GATEWAY_TOKEN", props.get("OPENCLAW_GATEWAY_PASSWORD", ""))
paths = [
    "/__openclaw__/canvas/map.html",
    "/__openclaw__/canvas/test.html",
    "/__openclaw__/canvas/blank.html",
    "/__openclaw__/canvas/webview_spec_test.html",
]

for host in ["192.168.1.55", "100.112.96.116"]:
    print(f"\n=== host {host} ===")
    for path in paths:
        url = f"http://{host}:{port}{path}"
        req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
        try:
            with urllib.request.urlopen(req, timeout=10) as r:
                body = r.read()
                print(f"{path} -> {r.status} bytes={len(body)}")
                out = ROOT / "scripts" / f"canvas_{path.split('/')[-1]}"
                out.write_bytes(body)
        except Exception as e:
            print(f"{path} -> ERR {e}")
