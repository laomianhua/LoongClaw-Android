#!/usr/bin/env python3
"""Probe OpenClaw Gateway: v3 sign + ui mode (matches LittleHelper App).

Note: SESSION_KEY below is for CLI probe convenience. The Android App uses
AppSessionStore: agent:main:<UUID> (never agent:main:main).
"""

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
SESSION_KEY = "agent:main:main"
TIMEOUT_SEC = 20
# Keep in sync with app/build.gradle.kts defaultConfig.versionName
CLIENT_VERSION = "2.0"

_PRIVATE = Ed25519PrivateKey.from_private_bytes(hashlib.sha256(b"littlehelper-probe-v1").digest())
_PUBLIC_RAW = _PRIVATE.public_key().public_bytes_raw()
DEVICE_ID = hashlib.sha256(_PUBLIC_RAW).hexdigest()
PUBLIC_KEY_B64URL = base64.urlsafe_b64encode(_PUBLIC_RAW).decode().rstrip("=")
DEVICE_TOKEN_FILE = __file__.replace("openclaw_gateway_probe.py", ".probe_device_token")


def load_device_token() -> str | None:
    try:
        with open(DEVICE_TOKEN_FILE, encoding="utf-8") as f:
            t = f.read().strip()
            return t or None
    except OSError:
        return None


def save_device_token(token: str) -> None:
    with open(DEVICE_TOKEN_FILE, "w", encoding="utf-8") as f:
        f.write(token)


def sign(payload: str) -> str:
    return base64.urlsafe_b64encode(_PRIVATE.sign(payload.encode())).decode().rstrip("=")


def build_connect(nonce: str, auth_token: str) -> dict:
    signed_at = int(time.time() * 1000)
    client_mode = "ui"
    platform = "android"
    device_family = "android"
    payload = "|".join([
        "v3", DEVICE_ID, "openclaw-android", client_mode, "operator",
        "operator.read,operator.write", str(signed_at), auth_token, nonce,
        platform, device_family,
    ])
    return {
        "type": "req",
        "id": "conn_1",
        "method": "connect",
        "params": {
            "minProtocol": 4,
            "maxProtocol": 4,
            "client": {
                "id": "openclaw-android",
                "version": CLIENT_VERSION,
                "platform": platform,
                "mode": client_mode,
                "deviceFamily": device_family,
            },
            "role": "operator",
            "scopes": ["operator.read", "operator.write"],
            "caps": [],
            "commands": [],
            "permissions": {},
            "auth": {"token": auth_token, "password": TOKEN},
            "locale": "zh-CN",
            "userAgent": f"littlehelper-probe/{CLIENT_VERSION}",
            "device": {
                "id": DEVICE_ID,
                "publicKey": PUBLIC_KEY_B64URL,
                "signature": sign(payload),
                "signedAt": signed_at,
                "nonce": nonce,
            },
        },
    }


def main() -> int:
    stored = load_device_token()
    auth_token = stored or TOKEN
    print(f"device.id = {DEVICE_ID}")
    print(f"auth.token = {'deviceToken(saved)' if stored else 'gateway-shared'}")

    ws = websocket.create_connection(f"ws://{HOST}:{PORT}", timeout=TIMEOUT_SEC)
    deadline = time.time() + TIMEOUT_SEC
    connected = False
    subscribed = False
    req_id = 0

    def send_req(method: str, params: dict):
        nonlocal req_id
        req_id += 1
        ws.send(json.dumps({"type": "req", "id": f"req_{req_id}", "method": method, "params": params}))
        print(f"-> {method}")

    try:
        while time.time() < deadline:
            msg = json.loads(ws.recv())
            text = json.dumps(msg, ensure_ascii=False)
            print(f"<- {text[:500]}")

            if msg.get("type") == "event" and msg.get("event") == "connect.challenge":
                ws.send(json.dumps(build_connect(msg["payload"]["nonce"], auth_token)))

            elif msg.get("type") == "res" and msg.get("id") == "conn_1":
                if msg.get("ok") and (msg.get("payload") or {}).get("type") == "hello-ok":
                    connected = True
                    auth = (msg.get("payload") or {}).get("auth") or {}
                    dt = auth.get("deviceToken")
                    if dt:
                        save_device_token(dt)
                        print(f"OK hello-ok scopes={auth.get('scopes')} deviceToken saved")
                    else:
                        print(f"OK hello-ok scopes={auth.get('scopes')}")
                    send_req("sessions.messages.subscribe", {"key": SESSION_KEY})
                else:
                    print(f"ERROR connect: {msg.get('error')}")
                    return 1

            elif msg.get("type") == "res" and connected and not subscribed:
                if msg.get("ok"):
                    subscribed = True
                    print("OK sessions.messages.subscribe")
                    send_req("sessions.send", {"key": SESSION_KEY, "message": "probe after pairing"})
                    deadline = time.time() + 5
                else:
                    print(f"ERROR subscribe: {msg.get('error')}")
                    return 1

            elif msg.get("type") == "event" and subscribed:
                ev = msg.get("event")
                if ev in ("session.message", "session.operation", "chat.delta"):
                    print(f"  event: {ev}")

        if not connected:
            print("ERROR: no hello-ok")
            return 1
        if not subscribed:
            print("ERROR: subscribe failed")
            return 1
        print("OK full probe passed")
        return 0
    finally:
        ws.close()


if __name__ == "__main__":
    sys.exit(main())
