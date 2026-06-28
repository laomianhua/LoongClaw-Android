#!/usr/bin/env python3
"""
Cursor ↔ OpenClaw Gateway 开发协调通道（与 App 用户 session 隔离）。

约定：
- Session key: agent:main:cursor-dev（勿与 App 的 agent:main:main 混用）
- 每个话题由 Cursor 脚本发起 sessions.send；Gateway Agent 只回复，不主动开新话题
- 输出打印到 stdout，便于在 Cursor 终端反显

用法：
  python scripts/openclaw_dev_session.py "话题：请确认 webview Canvas URL 规则"
  python scripts/openclaw_dev_session.py --connect-timeout 8 --timeout 40 "话题"

超时（Gateway 重启时不要死等）：
- --connect-timeout  TCP 连接 + 握手 + session 准备，默认 10s
- --timeout          发出话题后等 Agent 回复，默认 45s
"""

from __future__ import annotations

import argparse
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
DEVICE_TOKEN_FILE = Path(__file__).with_name(".cursor_dev_device_token")

SESSION_KEY = "agent:main:cursor-dev"
CLIENT_ID = "openclaw-android"
USER_AGENT = "littlehelper-cursor-dev/1.0.0"
DEVICE_SEED = b"littlehelper-cursor-dev-v1"
PROBE_DEVICE_SEED = b"littlehelper-probe-v1"
PROBE_TOKEN_FILE = Path(__file__).with_name(".probe_device_token")

DEFAULT_CONNECT_TIMEOUT = 10
DEFAULT_REPLY_TIMEOUT = 45
RECV_POLL_SEC = 1.0

DEV_INSTRUCTIONS = """\
你是 OpenClaw Gateway 与 Cursor（LittleHelper App 开发 IDE）之间的技术协调助手。
当前会话 agent:main:cursor-dev 仅用于开发联调，不是手机 App 用户会话。

规则：
1. 只回复 Cursor 发起的最新一条技术问题，不要主动开启新话题。
2. 不要向 Cursor 提问等待用户回答；若信息不足，列出假设并给出推荐方案。
3. 回答简洁、可执行，涉及 MODAL/webview/Canvas URL 时请给具体 JSON 或 URL 示例。
"""


def device_material(seed: bytes) -> tuple[Ed25519PrivateKey, str, str]:
    private = Ed25519PrivateKey.from_private_bytes(hashlib.sha256(seed).digest())
    public_raw = private.public_key().public_bytes_raw()
    device_id = hashlib.sha256(public_raw).hexdigest()
    public_b64url = base64.urlsafe_b64encode(public_raw).decode().rstrip("=")
    return private, device_id, public_b64url


def sign_with(private: Ed25519PrivateKey, payload: str) -> str:
    return base64.urlsafe_b64encode(private.sign(payload.encode())).decode().rstrip("=")


def build_connect(
    nonce: str,
    host_token: str,
    password: str,
    *,
    private: Ed25519PrivateKey,
    device_id: str,
    public_b64url: str,
) -> dict:
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
            "client": {
                "id": CLIENT_ID,
                "version": "1.0.0",
                "platform": "android",
                "mode": "node",
            },
            "role": "operator",
            "scopes": ["operator.read", "operator.write"],
            "caps": [],
            "commands": [],
            "permissions": {},
            "auth": {"token": host_token, "password": password},
            "locale": "zh-CN",
            "userAgent": USER_AGENT,
            "device": {
                "id": device_id,
                "publicKey": public_b64url,
                "signature": sign_with(private, payload),
                "signedAt": signed_at,
                "nonce": nonce,
            },
        },
    }


def load_local_properties() -> dict[str, str]:
    props: dict[str, str] = {}
    if not LOCAL_PROPERTIES.exists():
        return props
    for line in LOCAL_PROPERTIES.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        props[k.strip()] = v.strip()
    return props


def load_device_token(token_file: Path = DEVICE_TOKEN_FILE) -> str | None:
    try:
        token = token_file.read_text(encoding="utf-8").strip()
        return token or None
    except OSError:
        return None


def save_device_token(token: str, token_file: Path = DEVICE_TOKEN_FILE) -> None:
    token_file.write_text(token, encoding="utf-8")


def extract_assistant_text(payload: dict) -> str:
    message = payload.get("message") or payload
    content = message.get("content")
    if isinstance(content, str) and content.strip():
        return content.strip()
    if isinstance(content, list):
        parts: list[str] = []
        for block in content:
            if not isinstance(block, dict):
                continue
            t = block.get("type", "").lower()
            if t in ("thinking", "reasoning"):
                continue
            text = block.get("text") or block.get("content")
            if isinstance(text, str) and text.strip():
                parts.append(text.strip())
        if parts:
            return "\n".join(parts)
    text = message.get("text")
    return text.strip() if isinstance(text, str) else ""


def wrap_dev_message(user_text: str) -> str:
    return (
        f"{DEV_INSTRUCTIONS.strip()}\n\n"
        "[CURSOR_DEV_TOPIC]\n"
        f"{user_text.strip()}\n"
        "[/CURSOR_DEV_TOPIC]\n"
        "请仅针对以上话题回复（协调会话，勿开启新话题）。"
    )


def open_gateway_socket(host: str, port: int, connect_timeout: int) -> websocket.WebSocket:
    url = f"ws://{host}:{port}"
    try:
        ws = websocket.create_connection(url, timeout=connect_timeout)
        ws.settimeout(RECV_POLL_SEC)
        return ws
    except ConnectionRefusedError:
        raise RuntimeError(f"连接被拒绝（Gateway 可能正在重启）: {url}") from None
    except OSError as exc:
        raise RuntimeError(f"Gateway 不可达: {url} ({exc})") from None
    except websocket.WebSocketException as exc:
        raise RuntimeError(f"WebSocket 连接失败: {url} ({exc})") from None


def recv_json(ws: websocket.WebSocket, deadline: float) -> dict | None:
    remaining = deadline - time.time()
    if remaining <= 0:
        return None
    ws.settimeout(min(RECV_POLL_SEC, max(0.2, remaining)))
    try:
        raw = ws.recv()
    except websocket.WebSocketTimeoutException:
        return None
    except websocket.WebSocketConnectionClosedException:
        raise RuntimeError("Gateway 连接已断开（可能正在重启）") from None
    return json.loads(raw)


def main() -> int:
    parser = argparse.ArgumentParser(description="Cursor dev coordination session with OpenClaw Gateway")
    parser.add_argument("message", nargs="?", default="", help="Topic message to send")
    parser.add_argument(
        "--connect-timeout",
        type=int,
        default=DEFAULT_CONNECT_TIMEOUT,
        help=f"Seconds to connect and prepare session (default {DEFAULT_CONNECT_TIMEOUT})",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=DEFAULT_REPLY_TIMEOUT,
        help=f"Seconds to wait for assistant reply after send (default {DEFAULT_REPLY_TIMEOUT})",
    )
    parser.add_argument(
        "--use-probe-device",
        action="store_true",
        help="Reuse scripts/.probe_device_token identity (often already paired on dev PC)",
    )
    args = parser.parse_args()

    props = load_local_properties()
    host = props.get("OPENCLAW_GATEWAY_HOST", "192.168.1.55")
    port = int(props.get("OPENCLAW_GATEWAY_PORT", "18789"))
    password = props.get("OPENCLAW_GATEWAY_PASSWORD", "clawbot-test-2024")
    shared_token = props.get("OPENCLAW_GATEWAY_TOKEN", password)

    if args.use_probe_device:
        seed = PROBE_DEVICE_SEED
        token_file = PROBE_TOKEN_FILE
        identity_label = "probe-device"
    else:
        seed = DEVICE_SEED
        token_file = DEVICE_TOKEN_FILE
        identity_label = "cursor-dev-device"

    private, device_id, public_b64url = device_material(seed)
    auth_token = load_device_token(token_file) or shared_token

    topic = args.message.strip() or (
        "握手测试：请确认你收到 Cursor 开发协调会话。"
        "回复：1) 是否使用独立 session agent:main:cursor-dev；"
        "2) webview Canvas 基址 URL 示例；"
        "3) 缺省 MODAL block 是否改为 type:webview。"
    )

    print("=== OpenClaw Dev Session (Cursor <-> Gateway) ===")
    print(f"gateway   = ws://{host}:{port}")
    print(f"session   = {SESSION_KEY}  (App uses agent:main:main)")
    print(f"device.id = {device_id} ({identity_label})")
    print(f"auth      = {'deviceToken(saved)' if load_device_token(token_file) else 'shared-token'}")
    print(
        f"timeouts  = connect {args.connect_timeout}s, reply {args.timeout}s"
    )
    print()

    try:
        ws = open_gateway_socket(host, port, args.connect_timeout)
    except RuntimeError as exc:
        print(f"ERROR: {exc}")
        print("提示：Gateway 重启中请稍后再试，脚本不会长时间阻塞。")
        return 3

    req_id = 0
    connected = False
    session_ready = False
    subscribed = False
    sent = False
    assistant_chunks: list[str] = []
    assistant_final = ""
    pre_send_deadline = time.time() + args.connect_timeout
    reply_deadline: float | None = None

    def send_req(method: str, params: dict) -> str:
        nonlocal req_id
        req_id += 1
        rid = f"req_{req_id}"
        ws.send(json.dumps({"type": "req", "id": rid, "method": method, "params": params}, ensure_ascii=False))
        print(f"[CURSOR -> GW] {method} {json.dumps(params, ensure_ascii=False)[:240]}")
        return rid

    try:
        while True:
            now = time.time()
            if not sent:
                if now >= pre_send_deadline:
                    if not connected:
                        print(f"ERROR: 连接/握手超时（{args.connect_timeout}s，Gateway 可能仍在重启）")
                    else:
                        print(f"ERROR: session 准备超时（{args.connect_timeout}s）")
                    return 1
                msg = recv_json(ws, pre_send_deadline)
            else:
                assert reply_deadline is not None
                if now >= reply_deadline:
                    break
                msg = recv_json(ws, reply_deadline)

            if msg is None:
                continue

            etype = msg.get("type")
            event = msg.get("event")

            if etype == "event" and event == "connect.challenge":
                ws.send(json.dumps(build_connect(
                    msg["payload"]["nonce"],
                    auth_token,
                    password,
                    private=private,
                    device_id=device_id,
                    public_b64url=public_b64url,
                )))

            elif etype == "res" and msg.get("id") == "conn_1":
                if msg.get("ok") and (msg.get("payload") or {}).get("type") == "hello-ok":
                    connected = True
                    auth = (msg.get("payload") or {}).get("auth") or {}
                    dt = auth.get("deviceToken")
                    if dt:
                        save_device_token(dt, token_file)
                    print(f"[GW -> CURSOR] hello-ok scopes={auth.get('scopes')}")
                    send_req("sessions.create", {"key": SESSION_KEY})
                else:
                    err = msg.get("error")
                    print(f"[GW -> CURSOR] CONNECT FAILED: {json.dumps(err, ensure_ascii=False)}")
                    print("提示：若 pairingRequired，请在 Control UI 批准 device.id 后重试。")
                    return 1

            elif etype == "res" and connected and not session_ready and msg.get("ok"):
                session_ready = True
                print("[GW -> CURSOR] session ready (create or exists)")
                send_req("sessions.messages.subscribe", {"key": SESSION_KEY})

            elif etype == "res" and connected and not session_ready and not msg.get("ok"):
                err = msg.get("error") or {}
                if err.get("message", "").startswith("session not found"):
                    print("[CURSOR -> GW] creating dev session …")
                    send_req("sessions.create", {"key": SESSION_KEY})
                else:
                    print(f"[GW -> CURSOR] ERROR session setup: {json.dumps(err, ensure_ascii=False)}")
                    return 1

            elif etype == "res" and connected and session_ready and not subscribed and msg.get("ok"):
                subscribed = True
                print("[GW -> CURSOR] subscribed")
                outbound = wrap_dev_message(topic)
                print(f"[CURSOR -> GW] topic:\n{topic}\n")
                send_req("sessions.send", {"key": SESSION_KEY, "message": outbound})
                sent = True
                reply_deadline = time.time() + args.timeout
                print(f"[CURSOR] 等待 Agent 回复，最多 {args.timeout}s …")

            elif etype == "res" and connected and session_ready and not subscribed and not msg.get("ok"):
                print(f"[GW -> CURSOR] ERROR subscribe: {json.dumps(msg.get('error'), ensure_ascii=False)}")
                return 1

            elif etype == "res" and not msg.get("ok") and connected:
                print(f"[GW -> CURSOR] ERROR res: {json.dumps(msg.get('error'), ensure_ascii=False)}")

            elif etype == "event" and sent and event in ("chat.delta", "session.message", "chat.inject"):
                payload = msg.get("payload") or {}
                role = (payload.get("message") or payload).get("role", payload.get("role", "assistant"))
                if str(role).lower() != "assistant":
                    continue
                text = extract_assistant_text(payload)
                if not text:
                    continue
                is_final = payload.get("final")
                if is_final is None:
                    envelope = payload.get("message") or payload
                    is_final = envelope.get("final", event == "session.message")
                if event == "session.message" and is_final is not False:
                    assistant_final = text
                    print(f"[GW -> CURSOR] (final)\n{text}\n")
                    break
                if text not in assistant_chunks and (not assistant_chunks or not text.startswith(assistant_chunks[-1])):
                    assistant_chunks.append(text)
                    preview = text if len(text) < 400 else text[:400] + "..."
                    print(f"[GW -> CURSOR] (stream) {preview}")

        if not sent:
            print("ERROR: failed to send topic")
            return 1
        if not assistant_final and assistant_chunks:
            assistant_final = assistant_chunks[-1]
            print(f"[GW -> CURSOR] (last stream chunk as final)\n{assistant_final}\n")
        if not assistant_final:
            print(f"WARN: {args.timeout}s 内无 Agent 回复（Gateway 可能仍在重启或 Agent 未就绪）")
            return 2

        print("=== Dev session round complete ===")
        return 0
    except RuntimeError as exc:
        print(f"ERROR: {exc}")
        return 3
    finally:
        ws.close()


if __name__ == "__main__":
    sys.exit(main())
