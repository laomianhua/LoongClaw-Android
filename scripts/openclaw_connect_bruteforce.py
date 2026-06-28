#!/usr/bin/env python3
"""Brute-find allowed client.id / client.mode for OpenClaw connect."""

import json
import sys
import time

import websocket

HOST, PORT = "192.168.1.55", 18789
PASSWORD = "clawbot-test-2024"

IDS = [
    "android-client", "android", "openclaw-android", "littlehelper", "shell",
    "webchat-ui", "control-ui", "openclaw-control-ui", "cli", "node", "operator",
    "ios-client", "desktop", "gateway-probe",
]
MODES = ["operator", "node", "ui", "shell", "client", "webchat", "control", "android"]


def try_connect(client_id: str, mode: str) -> str | None:
    ws = websocket.create_connection(f"ws://{HOST}:{PORT}", timeout=8)
    try:
        raw = ws.recv()
        msg = json.loads(raw)
        if msg.get("event") != "connect.challenge":
            return "no challenge"
        payload = msg.get("payload") or {}
        nonce = payload["nonce"]
        ts = payload.get("ts", int(time.time() * 1000))
        frame = {
            "type": "req", "id": "conn_1", "method": "connect",
            "params": {
                "minProtocol": 4, "maxProtocol": 4,
                "client": {"id": client_id, "version": "1.0.0", "platform": "android", "mode": mode},
                "role": "operator",
                "scopes": ["operator.read", "operator.write"],
                "caps": [], "commands": [], "permissions": {},
                "auth": {"password": PASSWORD},
                "locale": "zh-CN", "userAgent": "probe/1.0",
                "device": {"id": "probe", "publicKey": "placeholder", "signature": "placeholder",
                           "signedAt": ts, "nonce": nonce},
            },
        }
        ws.send(json.dumps(frame))
        res = json.loads(ws.recv())
        if res.get("ok"):
            return "OK"
        err = res.get("error") or {}
        return err.get("message", str(err))[:120]
    finally:
        ws.close()


def main():
    for cid in IDS:
        for mode in MODES:
            try:
                result = try_connect(cid, mode)
                mark = "✓" if result == "OK" else " "
                print(f"{mark} id={cid:22} mode={mode:10} -> {result}")
                if result == "OK":
                    return 0
            except Exception as e:
                print(f"  id={cid:22} mode={mode:10} -> EXC {e}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
