#!/usr/bin/env python3
"""Test openclaw-android connect variants (device block / auth)."""

import json
import sys
import time
import websocket

HOST, PORT = "192.168.1.55", 18789
PASSWORD = "clawbot-test-2024"


def connect_variant(label: str, client_id: str, mode: str, device, auth) -> None:
    ws = websocket.create_connection(f"ws://{HOST}:{PORT}", timeout=8)
    try:
        msg = json.loads(ws.recv())
        nonce = msg["payload"]["nonce"]
        ts = msg["payload"].get("ts", int(time.time() * 1000))
        params = {
            "minProtocol": 4,
            "maxProtocol": 4,
            "client": {"id": client_id, "version": "1.0.0", "platform": "android", "mode": mode},
            "role": "operator",
            "scopes": ["operator.read", "operator.write"],
            "caps": [],
            "commands": [],
            "permissions": {},
            "auth": auth,
            "locale": "zh-CN",
            "userAgent": "probe/1.0",
        }
        if device is not False:
            params["device"] = device if device is not None else {
                "id": "probe-device",
                "publicKey": "placeholder",
                "signature": "placeholder",
                "signedAt": ts,
                "nonce": nonce,
            }
        ws.send(json.dumps({"type": "req", "id": "conn_1", "method": "connect", "params": params}))
        res = json.loads(ws.recv())
        ok = res.get("ok")
        err = res.get("error")
        payload_type = (res.get("payload") or {}).get("type")
        print(f"{label:40} -> ok={ok} type={payload_type} err={err}")
    finally:
        ws.close()


def main():
    variants = [
        ("openclaw-android/node placeholder device", "openclaw-android", "node", None, {"password": PASSWORD}),
        ("openclaw-android/node NO device key", "openclaw-android", "node", False, {"password": PASSWORD}),
        ("openclaw-android/node empty device", "openclaw-android", "node", {}, {"password": PASSWORD}),
        ("openclaw-android/webchat placeholder", "openclaw-android", "webchat", None, {"password": PASSWORD}),
        ("openclaw-android/webchat NO device", "openclaw-android", "webchat", False, {"password": PASSWORD}),
        ("openclaw-android/node token auth", "openclaw-android", "node", False, {"token": PASSWORD}),
        ("webchat-ui/webchat NO device", "webchat-ui", "webchat", False, {"password": PASSWORD}),
        ("cli/node NO device", "cli", "node", False, {"password": PASSWORD}),
    ]
    for args in variants:
        try:
            connect_variant(*args)
        except Exception as e:
            print(f"{args[0]:40} -> EXC {e}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
