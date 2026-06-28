#!/usr/bin/env python3
"""Dev session via LAN while local.properties keeps Tailscale host for the App build."""
from pathlib import Path
import sys
import openclaw_dev_session as s

ROOT = Path(__file__).resolve().parents[1]
msg_file = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "scripts/_cursor_dev_followup.txt"
msg = msg_file.read_text(encoding="utf-8")

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
    "180",
    msg,
]
raise SystemExit(s.main())
