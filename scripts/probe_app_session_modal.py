#!/usr/bin/env python3
"""Send user trigger to agent:main:main and print assistant wire payload."""
from __future__ import annotations

import base64
import hashlib
import json
import sys
import time
from pathlib import Path

import websocket
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

ROOT = Path(__file__).resolve().parents[1]
LOCAL_PROPERTIES = ROOT / "local.properties"
PROBE_TOKEN_FILE = Path(__file__).with_name(".probe_device_token")

SESSION_KEY = "agent:main:main"
CLIENT_ID = "openclaw-android"
PROBE_DEVICE_SEED = b"littlehelper-probe-v1"
TRIGGER = "打开 WebView 能力测试"
TIMEOUT = 90


def load_props() -> dict[str, str]:
    props: dict[str, str] = {}
    for line in LOCAL_PROPERTIES.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        props[k.strip()] = v.strip()
    return props


def device_material(seed: bytes):
    private = Ed25519PrivateKey.from_private_bytes(hashlib.sha256(seed).digest())
    public_raw = private.public_key().public_bytes_raw()
    device_id = hashlib.sha256(public_raw).hexdigest()
    public_b64url = base64.urlsafe_b64encode(public_raw).decode().rstrip("=")
    return private, device_id, public_b64url


def sign_with(private: Ed25519PrivateKey, payload: str) -> str:
    return base64.urlsafe_b64encode(private.sign(payload.encode())).decode().rstrip("=")


def build_connect(nonce: str, host_token: str, password: str, private, device_id: str, public_b64url: str) -> dict:
    signed_at = int(time.time() * 1000)
    payload = "|".join([
        "v2", device_id, CLIENT_ID, "node", "operator",
        "operator.read,operator.write", str(signed_at), host_token, nonce,
    ])
    return {
        "type": "req",
        "id": "conn_1",
        "method": "connect",
        "params": {
            "minProtocol": 4,
            "maxProtocol": 4,
            "client": {"id": CLIENT_ID, "version": "1.0.0", "platform": "android", "mode": "node"},
            "role": "operator",
            "scopes": ["operator.read", "operator.write"],
            "caps": [],
            "commands": [],
            "permissions": {},
            "auth": {"token": host_token, "password": password},
            "locale": "zh-CN",
            "userAgent": "littlehelper-probe/1.0.0",
            "device": {
                "id": device_id,
                "publicKey": public_b64url,
                "signature": sign_with(private, payload),
                "signedAt": signed_at,
                "nonce": nonce,
            },
        },
    }


def main() -> int:
    props = load_props()
    host = props.get("OPENCLAW_GATEWAY_HOST", "192.168.1.55")
    if host == "100.112.96.116":
        host = "192.168.1.55"
    port = int(props.get("OPENCLAW_GATEWAY_PORT", "18789"))
    password = props.get("OPENCLAW_GATEWAY_PASSWORD", "clawbot-test-2024")
    token = PROBE_TOKEN_FILE.read_text(encoding="utf-8").strip() if PROBE_TOKEN_FILE.exists() else props.get("OPENCLAW_GATEWAY_TOKEN", password)

    private, device_id, public_b64url = device_material(PROBE_DEVICE_SEED)
    ws = websocket.create_connection(f"ws://{host}:{port}", timeout=15)
    ws.settimeout(2)

    subscribed = False
    sent = False
    deadline = time.time() + TIMEOUT
    assistant_events: list[dict] = []

    def send_req(method: str, params: dict) -> None:
        ws.send(json.dumps({"type": "req", "id": method, "method": method, "params": params}, ensure_ascii=False))

    print(f"gateway={host}:{port} session={SESSION_KEY}")
    try:
        while time.time() < deadline:
            try:
                raw = ws.recv()
            except websocket.WebSocketTimeoutException:
                continue
            msg = json.loads(raw)
            etype = msg.get("type")
            event = msg.get("event")

            if etype == "event" and event == "connect.challenge":
                ws.send(json.dumps(build_connect(msg["payload"]["nonce"], token, password, private, device_id, public_b64url)))

            elif etype == "res" and msg.get("id") == "conn_1" and msg.get("ok"):
                send_req("sessions.messages.subscribe", {"key": SESSION_KEY})

            elif etype == "res" and msg.get("id") == "sessions.messages.subscribe" and msg.get("ok"):
                subscribed = True
                print(f"-> send: {TRIGGER}")
                send_req("sessions.send", {"key": SESSION_KEY, "message": TRIGGER})
                sent = True

            elif etype == "event" and sent and event in ("chat.delta", "session.message", "chat.inject"):
                payload = msg.get("payload") or {}
                role = (payload.get("message") or payload).get("role", payload.get("role"))
                if str(role).lower() == "assistant":
                    assistant_events.append(msg)
                    is_final = payload.get("final")
                    if is_final is None:
                        is_final = (payload.get("message") or payload).get("final", event == "session.message")
                    print(f"\n--- {event} final={is_final} ---")
                    print(json.dumps(payload, ensure_ascii=False, indent=2)[:8000])
                    if event == "session.message" and is_final is not False:
                        break
        print(f"\nassistant_events={len(assistant_events)}")
        return 0 if assistant_events else 2
    finally:
        ws.close()


if __name__ == "__main__":
    sys.exit(main())
