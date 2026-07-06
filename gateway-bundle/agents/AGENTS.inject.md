<!-- loongclaw-gateway-bundle:v2 -->

## LoongClaw / LittleHelper 客户端路由

- **`metadata.client.profile` 未在 Gateway 实现**；靠 **sender label**（`client.id`）+ **skill** 识别客户端，不是 Gateway 原生 profile 路由。
- 当 **`label == openclaw-android`**（龙爪 App）或 **`loongclaw-desktop`** 时，必须启用 MODAL 白板协议（见 `littlehelper-modal` skill）。

### ⚠️ Canvas / MODAL 铁律（每次回复前自检）

面向 **`openclaw-android` / `loongclaw-desktop`** 时，下列功能 **没有例外**：

| 功能 | 必须执行的脚本 | MODAL `data.url` 来源 |
|------|----------------|----------------------|
| 相册 / 画廊 | `python scripts/generate_gallery.py <关键词>` | 脚本 stdout JSON 的 `url` |
| 文件白板查看 | `python scripts/file_viewer.py <fileId> [title]` | 脚本 stdout JSON 的 `url` |
| 天气卡片 | `python scripts/weather_card.py --city <城市> --out mobile` | 脚本 stdout JSON 的 `url` |
| 记事本 | `python scripts/notepad.py --show --out mobile` | 脚本 stdout JSON 的 `url` |
| 文件管理 | `python scripts/file_manager.py` | 脚本 stdout JSON 的 `url` |
| 保存到 storage | `python scripts/save_to_storage.py <fileId> "<名称>" --tags a,b` | 文字确认（不必 MODAL） |

**禁止（违者 App 端功能缺失，不是「样式差一点」）：**

- **禁止**手写 HTML 写入 `~/.openclaw/canvas/` 再 MODAL
- **禁止**用 `type: markdown` 或纯文字冒充上述 Canvas 功能
- **禁止**猜测、编造、硬编码 `gallery_cache_*.html` / `viewer_*.html` / `weather_*.html` 等路径
- **禁止**使用 `/openclaw/canvas/`；url **必须** `/__openclaw__/canvas/...` 且来自脚本 JSON
- **禁止**手写 `storage/index.json`；用 `save_to_storage.py` 或确保 `{"items":[...]}` 格式
- **禁止**跳过脚本直接回复「已打开」——必须先跑脚本、读到 JSON，再发 `===MODAL===`

**唯一合法顺序：** 用户请求 → **执行脚本** → 解析 stdout JSON → **整条消息以 `===CHAT===` 开头** → `===MODAL===`（`data.url` = JSON 里的 `url`）。`===CHAT===` 之前不得有任何文字（含推理、修索引说明）。

### 回复格式

| 客户端 | 格式 |
|--------|------|
| `openclaw-android` | 必须 `===CHAT===` + `===MODAL===`（JSON 紧跟标记，勿用 markdown 代码块包裹） |
| `loongclaw-desktop` | 同上 |
| Control UI 浏览器（无 label） | 纯文字即可 |

### 禁止

- **禁止** `===END===`（App 解析会失败）。
- **禁止**只回复「好的」「收到」「已打开」而不带 `===MODAL===`（Canvas 功能场景）。
- **禁止**单独依据 `channel=webchat` 或 `session_status.active.channel` 推断「Control UI 纯文字」——App WebSocket 同样走 webchat 通道。
- **禁止**在聊天气泡正文里写 `===CHAT===` / `===MODAL===` 字面量（仅整条消息作为 wire 时使用）。
- **禁止**用长 markdown 清单做「协议确认」；确认用一两句纯文字即可。

完整契约：`littlehelper-modal` skill、`docs/OPENCLAW_GATEWAY_CONTRACT.md`；各 display skill 内有 **标准触发语 + 完整 wire 示例**，照抄改 `url` 即可。
