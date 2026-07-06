"""
文件上传服务器，监听 18889 端口。
与 OpenClaw Gateway 同机运行。
"""
import os, uuid, json, re
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse

UPLOAD_DIR = os.path.expanduser("~/.openclaw/uploads")
STORAGE_DIR = os.path.expanduser("~/.openclaw/workspace/storage")
os.makedirs(UPLOAD_DIR, exist_ok=True)
os.makedirs(STORAGE_DIR, exist_ok=True)
HOST = "0.0.0.0"
PORT = 18889
MAX_SIZE = 50 * 1024 * 1024


def parse_multipart(body, boundary):
    boundary = boundary.encode("utf-8")
    parts = body.split(b"--" + boundary)
    result = {}
    for part in parts:
        if part.strip() in (b"", b"--\r\n", b"--"):
            continue
        if part.startswith(b"\r\n"):
            part = part[2:]
        header_end = part.find(b"\r\n\r\n")
        if header_end < 0:
            continue
        headers_raw = part[:header_end].decode("utf-8", errors="replace")
        data = part[header_end + 4:]
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


class UploadHandler(BaseHTTPRequestHandler):

    INDEX_FILE = os.path.expanduser("~/.openclaw/workspace/storage/index.json")

    def do_POST(self):
        # 复用 do_POST 但区分路径
        if self.path.startswith("/file/delete/"):
            self._handle_delete()
            return
        self._handle_upload()

    def _handle_upload(self):
        if self.path != "/upload":
            self._json_response(404, {"error": "not found"})
            return
        ct = self.headers.get("Content-Type", "")
        if "multipart/form-data" not in ct:
            self._json_response(400, {"error": "need multipart/form-data"})
            return
        bm = re.search(r'boundary=([^\s;]+)', ct)
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
            self._json_response(413, {"error": f"file too large ({MAX_SIZE//1048576}MB max)"})
            return
        file_id = uuid.uuid4().hex
        safe_name = f"{file_id}_{original_name}"
        save_path = os.path.join(UPLOAD_DIR, safe_name)
        with open(save_path, "wb") as f:
            f.write(raw_data)
        self._json_response(200, {
            "fileId": file_id, "fileName": original_name,
            "size": len(raw_data), "path": save_path
        })

    def _handle_delete(self):
        file_name = self.path[len("/file/delete/"):]
        from urllib.parse import unquote
        file_name = unquote(file_name)
        # 只在 storage/ 中删除
        fp = os.path.join(STORAGE_DIR, file_name)
        if os.path.exists(fp) and not os.path.isdir(fp):
            try:
                os.remove(fp)
                # 同时删除缩略图
                state = os.environ.get("OPENCLAW_STATE_DIR", os.path.expanduser("~/.openclaw"))
                thumb = os.path.join(state, "canvas", "thumb_" + file_name)
                if os.path.exists(thumb):
                    os.remove(thumb)
                # 从 index.json 移除
                if os.path.exists(INDEX_FILE):
                    with open(INDEX_FILE, "r", encoding="utf-8") as f:
                        idx = json.load(f)
                    idx["items"] = [i for i in idx.get("items", []) if i.get("fileName") != file_name]
                    with open(INDEX_FILE, "w", encoding="utf-8") as f:
                        json.dump(idx, f, ensure_ascii=False, indent=2)
                self._json_response(200, {"ok": True, "deleted": file_name})
                print(f"[upload-server] deleted: {file_name}")
                return
            except Exception as e:
                self._json_response(500, {"error": str(e)})
                return
        self._json_response(404, {"error": "file not found"})

    def do_GET(self):
        from urllib.parse import unquote
        parsed = urlparse(self.path)
        path = parsed.path

        # /file/download/{fileName} — 从 storage/ 或 uploads/ 提供下载
        if path.startswith("/file/download/"):
            file_name = unquote(path[len("/file/download/"):])
            for search_dir in (STORAGE_DIR, UPLOAD_DIR):
                if not os.path.isdir(search_dir):
                    continue
                fp = os.path.join(search_dir, file_name)
                if os.path.exists(fp) and not os.path.isdir(fp):
                    with open(fp, "rb") as f:
                        data = f.read()
                    self.send_response(200)
                    self.send_header("Content-Type", self._guess_mime(file_name))
                    self.send_header("Content-Length", str(len(data)))
                    self.send_header("Content-Disposition", f'attachment; filename="{file_name}"')
                    self.send_header("Access-Control-Allow-Origin", "*")
                    self.end_headers()
                    self.wfile.write(data)
                    return
            self.send_error(404)
            return

        # /files/{fileId} — 旧版接口，仍保留作为兼容
        if path.startswith("/files/"):
            file_id = path[len("/files/"):]
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
        else:
            self.send_error(404)

    def _guess_mime(self, name):
        ext = name.rsplit(".", 1)[-1].lower() if "." in name else ""
        return {
            "jpg": "image/jpeg", "jpeg": "image/jpeg",
            "png": "image/png", "webp": "image/webp",
            "gif": "image/gif", "bmp": "image/bmp",
            "pdf": "application/pdf",
            "xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "csv": "text/csv",
            "txt": "text/plain", "md": "text/markdown", "json": "application/json",
        }.get(ext, "application/octet-stream")

    def _json_response(self, code, data):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        print(f"[upload-server] {args[0]} {args[1]} {args[2]}")


def run():
    server = HTTPServer((HOST, PORT), UploadHandler)
    print(f"Upload server running on http://{HOST}:{PORT}")
    print(f"Save dir: {UPLOAD_DIR}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.server_close()


if __name__ == "__main__":
    run()
