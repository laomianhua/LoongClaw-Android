"""
LittleHelper upload sidecar.

Endpoints:
  POST /upload
  POST /file/delete/{storageFileName}
  GET  /files/{fileId}
  GET  /file/download/{storageFileName}
  GET  /health

Env:
  OPENCLAW_STATE_DIR  -> uploads under {state}/uploads (default ~/.openclaw/uploads)
  OPENCLAW_WORKSPACE  -> storage under {workspace}/storage
  UPLOAD_PORT         -> listen port (default 18889; 18890 when state dir is openclaw-dev)
"""
from __future__ import annotations

import json
import mimetypes
import os
import re
import sys
import uuid
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import unquote, urlparse

sys.path.insert(0, str(Path(__file__).resolve().parent))
from upload_env import (
    resolve_canvas_dir,
    resolve_index_file,
    resolve_storage_dir,
    resolve_upload_dir,
    resolve_upload_port,
)

UPLOAD_DIR = resolve_upload_dir()
STORAGE_DIR = resolve_storage_dir()
CANVAS_DIR = resolve_canvas_dir()
INDEX_FILE = resolve_index_file()
os.makedirs(UPLOAD_DIR, exist_ok=True)
os.makedirs(STORAGE_DIR, exist_ok=True)

HOST = "0.0.0.0"
PORT = resolve_upload_port()
MAX_SIZE = 50 * 1024 * 1024


def parse_multipart(body: bytes, boundary: str) -> dict:
    boundary_bytes = boundary.encode("utf-8")
    parts = body.split(b"--" + boundary_bytes)
    result: dict = {}
    for part in parts:
        if part.strip() in (b"", b"--\r\n", b"--"):
            continue
        if part.startswith(b"\r\n"):
            part = part[2:]
        header_end = part.find(b"\r\n\r\n")
        if header_end < 0:
            continue
        headers_raw = part[:header_end].decode("utf-8", errors="replace")
        data = part[header_end + 4 :]
        if data.endswith(b"\r\n"):
            data = data[:-2]
        name_match = re.search(r'name="([^"]*)"', headers_raw)
        filename_match = re.search(r'filename="([^"]*)"', headers_raw)
        field_name = name_match.group(1) if name_match else "unknown"
        file_name = filename_match.group(1) if filename_match else None
        if file_name:
            result[field_name] = (file_name, data)
        else:
            result[field_name] = (None, data)
    return result


def resolve_upload_file(storage_name: str) -> str | None:
    """Match exact name or {uuid}_{name} pattern in uploads dir."""
    if not storage_name or ".." in storage_name or "/" in storage_name or "\\" in storage_name:
        return None
    direct = os.path.join(UPLOAD_DIR, storage_name)
    if os.path.isfile(direct):
        return direct
    if not os.path.isdir(UPLOAD_DIR):
        return None
    for fname in os.listdir(UPLOAD_DIR):
        if fname == storage_name or fname.endswith(f"_{storage_name}"):
            fp = os.path.join(UPLOAD_DIR, fname)
            if os.path.isfile(fp):
                return fp
    return None


def resolve_download_file(storage_name: str) -> str | None:
    """Prefer workspace/storage, then uploads (with uuid prefix fallback)."""
    if not storage_name or ".." in storage_name or "/" in storage_name or "\\" in storage_name:
        return None
    storage_fp = os.path.join(STORAGE_DIR, storage_name)
    if os.path.isfile(storage_fp):
        return storage_fp
    return resolve_upload_file(storage_name)


class UploadHandler(BaseHTTPRequestHandler):
    def do_OPTIONS(self) -> None:
        self.send_response(204)
        self._cors_headers()
        self.end_headers()

    def do_POST(self) -> None:
        if self.path.startswith("/file/delete/"):
            self._handle_delete()
            return
        if self.path != "/upload":
            self._json_response(404, {"error": "not found"})
            return

        ct = self.headers.get("Content-Type", "")
        if "multipart/form-data" not in ct:
            self._json_response(400, {"error": "need multipart/form-data"})
            return
        bm = re.search(r"boundary=([^\s;]+)", ct)
        if not bm:
            self._json_response(400, {"error": "no boundary"})
            return
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        if not body:
            self._json_response(400, {"error": "empty body"})
            return
        fields = parse_multipart(body, bm.group(1))
        if "file" not in fields:
            self._json_response(400, {"error": "missing file field"})
            return
        original_name, raw_data = fields["file"]
        original_name = original_name or "untitled"
        if len(raw_data) > MAX_SIZE:
            self._json_response(413, {"error": f"file too large ({MAX_SIZE // 1048576}MB max)"})
            return
        file_id = uuid.uuid4().hex
        safe_name = f"{file_id}_{original_name}"
        save_path = os.path.join(UPLOAD_DIR, safe_name)
        with open(save_path, "wb") as f:
            f.write(raw_data)
        self._json_response(
            200,
            {
                "fileId": file_id,
                "fileName": original_name,
                "storageFileName": safe_name,
                "size": len(raw_data),
                "path": save_path,
            },
        )

    def _handle_delete(self) -> None:
        file_name = unquote(self.path[len("/file/delete/") :])
        if not file_name or ".." in file_name or "/" in file_name or "\\" in file_name:
            self._json_response(400, {"error": "invalid file name"})
            return
        fp = os.path.join(STORAGE_DIR, file_name)
        if not (os.path.isfile(fp)):
            fp = resolve_upload_file(file_name) or ""
        if not fp or not os.path.isfile(fp):
            self._json_response(404, {"error": "file not found"})
            return
        try:
            os.remove(fp)
            thumb = os.path.join(CANVAS_DIR, "thumb_" + file_name)
            if os.path.exists(thumb):
                os.remove(thumb)
            canvas_copy = os.path.join(CANVAS_DIR, file_name)
            if os.path.exists(canvas_copy):
                os.remove(canvas_copy)
            if os.path.exists(INDEX_FILE):
                with open(INDEX_FILE, "r", encoding="utf-8") as f:
                    idx = json.load(f)
                if isinstance(idx, dict):
                    idx["items"] = [
                        i for i in idx.get("items", []) if i.get("fileName") != file_name
                    ]
                    with open(INDEX_FILE, "w", encoding="utf-8") as f:
                        json.dump(idx, f, ensure_ascii=False, indent=2)
            self._json_response(200, {"ok": True, "deleted": file_name})
            print(f"[upload-server] deleted: {file_name}", flush=True)
        except OSError as e:
            self._json_response(500, {"error": str(e)})

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path

        if path == "/health":
            self._json_response(
                200,
                {
                    "ok": True,
                    "service": "littlehelper-upload",
                    "port": PORT,
                    "uploadDir": UPLOAD_DIR,
                    "storageDir": STORAGE_DIR,
                },
            )
            return

        if path.startswith("/file/download/"):
            storage_name = unquote(path[len("/file/download/") :])
            fp = resolve_download_file(storage_name)
            if not fp:
                self.send_error(404)
                return
            data = open(fp, "rb").read()
            ctype = mimetypes.guess_type(fp)[0] or "application/octet-stream"
            self.send_response(200)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Content-Disposition", f'attachment; filename="{os.path.basename(fp)}"')
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(data)
            return

        if path.startswith("/files/"):
            file_id = path[len("/files/") :]
            if os.path.isdir(UPLOAD_DIR):
                for fname in os.listdir(UPLOAD_DIR):
                    if fname.startswith(file_id):
                        fp = os.path.join(UPLOAD_DIR, fname)
                        data = open(fp, "rb").read()
                        self.send_response(200)
                        self.send_header("Content-Type", "application/octet-stream")
                        self.send_header("Content-Length", str(len(data)))
                        self.end_headers()
                        self.wfile.write(data)
                        return
            self.send_error(404)
            return

        self.send_error(404)

    def _cors_headers(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def _json_response(self, code: int, data: dict) -> None:
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self._cors_headers()
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args) -> None:
        print(f"[upload-server] {args[0]} {args[1]} {args[2]}", flush=True)


def run() -> None:
    server = HTTPServer((HOST, PORT), UploadHandler)
    print(f"LittleHelper upload server on http://{HOST}:{PORT}", flush=True)
    print(f"Save dir: {UPLOAD_DIR}", flush=True)
    print(f"Storage dir: {STORAGE_DIR}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.server_close()
    except OSError as e:
        print(f"Failed to bind :{PORT} — {e}", flush=True)
        raise SystemExit(1) from e


if __name__ == "__main__":
    run()
