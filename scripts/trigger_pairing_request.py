#!/usr/bin/env python3
"""Trigger one OpenClaw connect for device pairing approval (v2 Ed25519 sign)."""

import base64
import hashlib
import json
import sys
import time

import websocket
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

HOST = "192.168.1.55"
PORT = 18789
TOKEN = "clawbot-test-2024"

# Stable identity — matches scripts/sign_probe_variants.py / prior probe runs
_PRIVATE = Ed25519PrivateKey.from_private_bytes(hashlib.sha256(b"littlehelper-probe-v1").digest())
_PUBLIC_RAW = _PRIVATE.public_key().public_bytes_raw()
DEVICE_ID = hashlib.sha256(_PUBLIC_RAW).hexdigest()
PUBLIC_KEY_B64URL = base64.urlsafe_b64encode(_PUBLIC_RAW).decode().rstrip("=")


def sign(payload: str) -> str:
    return base64.urlsafe_b64encode(_PRIVATE.sign(payload.encode())).decode().rstrip("=")


def main() -> int:
    print(f"device.id = {DEVICE_ID}")
    print(f"Connecting ws://{HOST}:{PORT} …")

    ws = websocket.create_connection(f"ws://{HOST}:{PORT}", timeout=15)
    try:
        challenge = json.loads(ws.recv())
        nonce = challenge["payload"]["nonce"]
        signed_at = int(time.time() * 1000)
        payload = "|".join([
            "v2", DEVICE_ID, "openclaw-android", "node", "operator",
            "operator.read,operator.write", str(signed_at), TOKEN, nonce,
        ])
        frame = {
            "type": "req",
            "id": "conn_1",
            "method": "connect",
            "params": {
                "minProtocol": 4,
                "maxProtocol": 4,
                "client": {
                    "id": "openclaw-android",
                    "version": "1.0.0",
                    "platform": "android",
                    "mode": "node",
                },
                "role": "operator",
                "scopes": ["operator.read", "operator.write"],
                "caps": [],
                "commands": [],
                "permissions": {},
                "auth": {"token": TOKEN, "password": TOKEN},
                "locale": "zh-CN",
                "userAgent": "littlehelper-pairing-trigger/1.0.0",
                "device": {
                    "id": DEVICE_ID,
                    "publicKey": PUBLIC_KEY_B64URL,
                    "signature": sign(payload),
                    "signedAt": signed_at,
                    "nonce": nonce,
                },
            },
        }
        ws.send(json.dumps(frame))
        print("-> connect sent (v2 sign, mode=node, role=operator)")
        res = json.loads(ws.recv())
        print(f"<- {json.dumps(res, ensure_ascii=False)}")
        if res.get("ok"):
            print("OK hello-ok (already paired?)")
            return 0
        err = res.get("error") or {}
        msg = err.get("message", "")
        if "pairing" in msg.lower() or "not approved" in msg.lower():
            print("OK pairing request should appear in: openclaw devices list")
            return 0
        print(f"unexpected: {err}")
        return 1
    finally:
        ws.close()


if __name__ == "__main__":
    sys.exit(main())
