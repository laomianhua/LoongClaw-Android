# Upload sidecar

HTTP upload/download service for LoongClaw App attachments and gallery long-press save.

## Ports and directories

| Environment | `OPENCLAW_STATE_DIR` | Port | Upload dir |
|-------------|----------------------|------|------------|
| Production | (unset) → `~/.openclaw` | **18889** | `~/.openclaw/uploads` |
| Dev | `~/.openclaw-dev` | **18890** | `~/.openclaw-dev/uploads` |

Override anytime with `UPLOAD_PORT`.

Prod and dev sidecars can run **at the same time** on one machine (different ports + dirs).

## Start

**After `install.ps1`**, starters are written to **`{OPENCLAW_STATE_DIR}/companion/`** (not the bundle `sidecar/` folder):

```powershell
# dev example — note quotes (path may contain spaces)
& "$env:USERPROFILE\.openclaw-dev\companion\start-upload-sidecar.ps1"
```

Or CMD:

```cmd
"%USERPROFILE%\.openclaw-dev\companion\start-upload-sidecar.bat"
```

Do **not** run from `LoongClaw Installation Bundle\sidecar\` — that folder is only the install source; `upload_server.py` is copied to `companion/` during install.

Manual (if starters missing — re-run install.ps1):

```powershell
$env:OPENCLAW_STATE_DIR = "$env:USERPROFILE\.openclaw-dev"
python "$env:USERPROFILE\.openclaw-dev\companion\upload_server.py"
```

`doctor` checks `GET http://127.0.0.1:{port}/health` (18889 prod, 18890 when state dir is `openclaw-dev`).

## Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/upload` | App upload |
| GET | `/files/{fileId}` | Download by id |
| GET | `/file/download/{storageFileName}` | Download by storage name |
| GET | `/health` | Health check |

Install copies `upload_server.py` + `upload_env.py` to `{OPENCLAW_STATE_DIR}/companion/`.
