# LoongClaw Gateway Companion Bundle

在 **Windows Gateway 主机**上安装 **LoongClaw for Android**（龙爪）所需的 skill、workspace 脚本与上传 sidecar。

- **App 版本**：2.1.3 · **Bundle**：2.1.3  
- **OpenClaw 最低版本**：**2026.6.9**  
- **平台**：v2.1 **仅 Windows Gateway**（Linux/macOS 不在 Release 范围）

> **地图**：龙爪 App 仍支持地图 MODAL，但 **本安装包不包含** `map.littlehelper.html`。需要地图时由 Gateway 管理员自行部署 canvas 文件（参考仓库 `scripts/canvas_map_littlehelper.html`）。

## 前提

1. **Node.js** 推荐 **24 LTS**（`npm i -g openclaw`）；装完 npm 后**重开 PowerShell**（PATH 刷新）
2. **Python 3.10+**（install / doctor / upload sidecar / workspace 脚本）
3. Win 11 默认禁脚本：`Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned`
4. OpenClaw **`>=2026.6.9`**（安装 bundle **前**请自行确认；`install.ps1` 暂不自动检测版本）

> **⚠️ 版本过低**：OpenClaw &lt; 2026.6.9 时请勿安装本 bundle；请先 `npm i -g openclaw@latest` 并确认 `openclaw --version`。

## 安装 OpenClaw（首次）

```powershell
openclaw setup
openclaw onboard          # 配置 API key 等
openclaw config set gateway.bind "lan"   # 手机/Tailscale 连接必须；默认 loopback 仅本机
openclaw gateway          # 前台确认启动正常（勿先用 gateway start，可能静默失败）
```

> **⚠️ 禁止手动编辑 `openclaw.json` 的 gateway 项**（Token、bind、port 等）。用 `openclaw config set` / `openclaw configure`。  
> bundle `install` 仅 merge **`skills.load.extraDirs`**，其余配置仍走 CLI。

### 环境差异（可忽略）

- Node 安装时 `workload-vctools` / Python 组件失败 → 一般不影响 OpenClaw
- 中文用户名下 `.bat` 可能乱码 → upload sidecar 优先用下方 PowerShell 命令
- Control UI 路径为 **`/`**（不是旧版 `/ui`）
- Token 在 UI 显示 `REDACTED` 时：`openclaw config get gateway.auth.token`，或 Control UI **`/pair qr`**
- 带 token 的 Control UI 链接：`openclaw dashboard --no-open`

## 安装 Companion Bundle

### 1. 解压

```powershell
Expand-Archive loongclaw-gateway-bundle-2.1.3.zip -DestinationPath .
cd loongclaw-gateway-bundle-2.1.3
```

### 2. 运行 install

**龙爪 App only（默认，不 patch client id）：**

```powershell
.\install.ps1
# P0 only：.\install.ps1 -Profile minimal
# 或：powershell -ExecutionPolicy Bypass -File .\install.ps1
```

**若还需 LoongClaw PC 客户端（`loongclaw-desktop`）：**

```powershell
.\install.ps1 -WithPcPatch
```

install 会：

- 复制 skill → `~/.openclaw/shared-skills/`
- merge **`skills.load.extraDirs`** → `openclaw.json`（仅此 schema-safe 片段）
- 注入 **`~/.openclaw/workspace/AGENTS.md`** 龙爪客户端路由规则（幂等，见 `agents/AGENTS.inject.md`）
- （standard）复制 workspace 脚本 → `~/.openclaw/workspace/scripts/`
- （standard）部署 **`pdf.min.js` + `pdf.worker.min.js`** → `~/.openclaw/canvas/`（PDF 白板必需）
- （standard）创建 **`workspace/storage/index.json`** 种子（若不存在）
- （standard）复制 upload sidecar → `~/.openclaw/companion/`
- （`-WithPcPatch`）运行 `patch_clientid.ps1` 添加 **`loongclaw-desktop`**

> **龙爪 App** 使用 `openclaw-android`，OpenClaw 已内置，**不需要 patch**。详见 [`config/CLIENT_ID_SETUP.md`](config/CLIENT_ID_SETUP.md)。

> **⚠️ LoongClaw PC 客户端**：若你在 **PC 上**也用龙爪/OpenClaw 桌面端连同一 Gateway，安装时**必须**加 **`-WithPcPatch`**，否则 PC 端白板可能因 client id 未入白名单而无法加载。仅手机 Android 时可省略。

> **⚠️ 多 Agent 配置**：编辑 `agents.list` 新增家庭成员时，**必须保留 `main` 条目**（示例见仓库 `docs/multi_agent_gateway_setup.md`）。漏写 `main` 可能导致 Gateway 重启后主 agent 丢失。

### 3. 重启 Gateway 并启动 upload sidecar（standard）

```powershell
# 重启 openclaw gateway 后，另开终端（中文路径环境优先 PowerShell，勿依赖 .bat）：
python $env:USERPROFILE\.openclaw\companion\upload_server.py
```

建议配置 Windows 计划任务或启动文件夹保活（当前 install 不自动创建）。`doctor` 若 `:18889/health` 失败会提示手动启动。

### 4. 自检

```powershell
.\doctor.ps1
```

全部 `[OK]` 后：

**发版 / 大版本升级前**（约 15 分钟）：见 [`docs/BUNDLE_2.1.1_REMEDIATION.md`](../docs/BUNDLE_2.1.1_REMEDIATION.md) §六 — 重点 **PDF 预览**、**文件管理删除**、**save_to_storage → 画廊**。

手机侧：

1. 安装龙爪 App
2. `ipconfig` 查 Gateway 主机局域网 / Tailscale IP
3. Token：`openclaw config get gateway.auth.token` 或 Control UI `/pair qr`
4. App 设置填 Gateway → **测试握手** → **保存并连接**

## Profile 与 bundle 内容

| Profile | 用途 |
|---------|------|
| `minimal` | 能聊、能弹 MODAL 白板（`littlehelper-modal` only） |
| `standard`（默认） | 上传/下载、画廊、文件、记事本、天气等 |

### `standard` 所含 skill

| Skill | 能力（Agent 按 SKILL.md 触发） |
|-------|-------------------------------|
| **`littlehelper-modal`** | MODAL 白板协议（**必装**） |
| `gallery-display` | 多图画廊、标签搜索、长按下载 |
| `file-viewer-display` | PDF / 文本 / 图片白板查看 |
| `file-manager` | 文件列表、长按下载、删除 |
| `notepad` | 记事本 / 停车 / 月历 |
| `weather-display` | 天气卡片（wttr.in，国内可能不稳定） |

参考 skill 以 **`SKILL.md` 指令**形式安装到 `shared-skills/`；Agent 按文档调用 workspace 脚本生成 Canvas 页面。

### `standard` 所含 workspace 脚本

装到 `~/.openclaw/workspace/scripts/`：

| 脚本 | 用途 |
|------|------|
| `generate_gallery.py` | 画廊 HTML（thumb :18789，download :18889） |
| `file_viewer.py` / `file_reader.py` | 文件白板 / 文字解读 |
| `file_manager.py` | 文件管理列表页 |
| `notepad.py` | 记事本月历 |
| `weather_card.py` | 天气卡片 |

另有 sidecar `upload_server.py`（`:18889`）。Workspace 脚本读 **`OPENCLAW_STATE_DIR`**（canvas 输出）与 **`OPENCLAW_WORKSPACE`**（storage 等，可选，默认 `{state}/workspace`）。

### 非默认 OpenClaw 实例（dev / 多 profile）

与 workspace 脚本相同，install / doctor 读 **`OPENCLAW_STATE_DIR`**（官方），fallback `~/.openclaw`。  
若 agent workspace 不在 `{state}/workspace`（例如 `~/.openclaw/workspace-dev`），另设 **`OPENCLAW_WORKSPACE`**。

```powershell
# 示例：openclaw-dev（19001），不覆盖生产 ~/.openclaw
.\install.ps1 `
  -OpenClawStateDir "$env:USERPROFILE\.openclaw-dev" `
  -OpenClawWorkspace "$env:USERPROFILE\.openclaw\workspace-dev"

.\doctor.ps1 -GatewayPort 19001 `
  -OpenClawStateDir "$env:USERPROFILE\.openclaw-dev" `
  -OpenClawWorkspace "$env:USERPROFILE\.openclaw\workspace-dev"
```

干净机默认 install **不要**传上述参数。

## 常见问题

| 问题 | 处理 |
|------|------|
| connect 被拒 | `gateway.bind` 须为 **`lan`**；Token/配对；**Android 不需要 patch** |
| 白板不弹 MODAL | 确认 `littlehelper-modal` 在 shared-skills；**重启 Gateway**；检查 `AGENTS.md` 已注入 |
| 画廊下载失败 | 启动 `:18889` sidecar；`doctor` 查 `/health` |
| 地图不可用 | **正常**——bundle 不含地图；自行部署 canvas 或不用地图 |
| 升级 OpenClaw 后 PC 客户端连不上 | 重跑 `.\install.ps1 -WithPcPatch` |

## 开发者

- 构建 zip：`python scripts/build_gateway_bundle.py`（skill 权威源：`scripts/skills/littlehelper-modal` + `gateway-export/skills/` + `gateway-export/scripts/`）
- Harvest 源：`gateway-export/`（见 `EXPORT_NOTES.md`）
- 地图参考源：`scripts/canvas_map_littlehelper.html`（**不**打入 bundle）
- 计划：`docs/GATEWAY_COMPANION_BUNDLE_PLAN.md`
