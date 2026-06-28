"""
文件上传服务器，监听 18889 端口。
与 OpenClaw Gateway 同机运行。
"""
import os, uuid, json, re
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse

UPLOAD_DIR = os.path.expanduser("~/.openclaw/uploads")
os.makedirs(UPLOAD_DIR, exist_ok=True)
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

    def do_POST(self):
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

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path.startswith("/files/"):
            file_id = parsed.path[len("/files/"):]
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
