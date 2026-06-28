import base64, hashlib, json, time, websocket
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

HOST, PORT, TOKEN = "192.168.1.55", 18789, "clawbot-test-2024"
priv = Ed25519PrivateKey.from_private_bytes(hashlib.sha256(b"littlehelper-probe-v1").digest())
pub = priv.public_key().public_bytes_raw()
did = hashlib.sha256(pub).hexdigest()
pk = base64.urlsafe_b64encode(pub).decode().rstrip("=")

def sign(payload: str) -> str:
    return base64.urlsafe_b64encode(priv.sign(payload.encode())).decode().rstrip("=")

def try_connect(label, scopes, version, tok_in_payload, family):
    ws = websocket.create_connection(f"ws://{HOST}:{PORT}", timeout=8)
    ch = json.loads(ws.recv())
    n = ch["payload"]["nonce"]
    t = int(time.time() * 1000)
    sc = ",".join(scopes)
    if version == "v2":
        pl = "|".join(["v2", did, "openclaw-android", "node", "operator", sc, str(t), tok_in_payload, n])
    else:
        pl = "|".join(["v3", did, "openclaw-android", "node", "operator", sc, str(t), tok_in_payload, n, "android", family])
    ws.send(json.dumps({
        "type": "req", "id": "c1", "method": "connect",
        "params": {
            "minProtocol": 4, "maxProtocol": 4,
            "client": {"id": "openclaw-android", "version": "1.0.0", "platform": "android", "mode": "node"},
            "role": "operator", "scopes": scopes, "caps": [], "commands": [], "permissions": {},
            "auth": {"token": TOKEN, "password": TOKEN},
            "locale": "zh-CN", "userAgent": "p/1",
            "device": {"id": did, "publicKey": pk, "signature": sign(pl), "signedAt": t, "nonce": n},
        },
    }))
    res = json.loads(ws.recv())
    msg = (res.get("error") or {}).get("message", "")[:60]
    print(f"{label:30} ok={res.get('ok')} {msg}")
    ws.close()

for scopes in [["operator.read", "operator.write"], []]:
    for version in ["v2", "v3"]:
        for tok in [TOKEN, ""]:
            for fam in ["android", ""]:
                try_connect(f"{version} sc={len(scopes)} tok={bool(tok)} fam={fam!r}", scopes, version, tok, fam)
