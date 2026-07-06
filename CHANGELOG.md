# Changelog

本文件记录 LoongClaw for Android（龙爪）开发与真机验收历史。开源用户阅读 [README.md](README.md)（英文）或 [README.zh.md](README.zh.md)（中文）即可。

格式：按日期倒序；较早日程见文末「历史版本」。

---

---

## 2026-07-06 — Release v2.1.3（开源前验收）

- **App**：版本号 **2.1.3**；欢迎语与 OpenClaw 客户端定位对齐；新启动器图标
- **聊天**：`OpenClawUserMessageCommitter` 修复用户气泡丢失；Gateway 回显去重
- **文件管理**：画廊删除改走 App 原生 `littlehelper://gallery/delete`；`__LH_removeGalleryItem` 删除后即时刷新列表
- **Gateway bundle 2.1.3**：`file_manager.py` / sidecar 删除与 CORS；`file_viewer.py` 不再污染 `index.json`；`AGENTS.inject` 加强
- **文档**：`README` / `CHANGELOG` 与 Release 产物版本对齐；`.gitignore` 加固（密钥与构建产物）

## 2026-07-06 — Gateway bundle 2.1.1（新机验收整改）

- **Harvest**：`save_to_storage.py`、`tag_images.py`、`pdf.min.js` / `pdf.worker.min.js`（pdfjs-dist@3.11.174）
- **sidecar**：`POST /file/delete/{fileName}`；下载优先 `workspace/storage` 再 `uploads`
- **install**：部署 canvas pdf.js；`storage/index.json` 种子 `{"items":[]}`
- **doctor**：检查 pdf.js、save_to_storage、file_viewer、index 格式
- **SKILL/AGENTS**：`chart/line` 示例；禁止正文 wire 标记；`save_to_storage` 铁律
- **App**：`file_paths.xml` 对齐 `Download/LoongClaw/`
- 详见 `docs/BUNDLE_2.1.1_REMEDIATION.md`

## 2026-07-05 — Gateway bundle 新机验收跟进

- **Skill 约束**：`littlehelper-modal` markdown Phase 1 子集；gallery / file-viewer / weather / file-manager / notepad 强制跑 workspace 脚本、禁止手写 HTML
- **脚本**：`gateway-export/scripts/*` 的 `WORKSPACE` 读 `OPENCLAW_WORKSPACE`（fallback `{OPENCLAW_STATE_DIR}/workspace`）
- **install/build**：复制时过滤 `__pycache__` / `*.pyc`；`.gitignore` 排除 pyc
- **AGENTS v2 铁律**：`gateway-bundle/agents/AGENTS.inject.md` 注入 workspace `AGENTS.md`；install 自动 v1→v2 升级

## 2026-07-04 — Release v2.1

- **龙爪 / LoongClaw for Android** 产品名；下载目录 `Download/LoongClaw/`
- 设置页 **界面语言**（**中文 / English**）；Settings、上传附件、我的文件英文化
- 新增 [`README.en.md`](README.en.md)
- Gateway companion bundle `2.1.0`（`loongclaw-gateway-bundle-2.1.0.zip`）
- **Bundle config merge 收窄**：`openclaw.merge.json` 仅 `skills.load.extraDirs`；Android 不 patch；PC 用 `-WithPcPatch`
- **Gateway 对齐（2026-07-04）**：OpenClaw `>=2026.6.9`；Windows Gateway only；P1 harvest 清单入 `EXPORT_NOTES.md`
- **Canvas 路径**：bundle workspace 脚本 `CANVAS_DIR` 读 `OPENCLAW_STATE_DIR`（官方），fallback `~/.openclaw`；dev 启动需 `set OPENCLAW_STATE_DIR=...`
- **安装包不含地图 canvas**：App 仍支持地图 MODAL；`map.littlehelper.html` 由 Gateway 自备（见 `scripts/canvas_map_littlehelper.html`）
- **install/doctor 支持 `OPENCLAW_STATE_DIR` / `OPENCLAW_WORKSPACE`**：dev 多实例安装不覆盖默认 `~/.openclaw`

## 2026-07-04 — Rebrand + 部分界面英文化（Release 2.1 前）

- 产品名 **龙爪** / **LoongClaw for Android**（`applicationId` 仍为 `com.littlehelper`）
- 下载目录改为 `Download/LoongClaw/`
- 设置、上传附件、我的文件支持中/英切换；连接错误与主聊天壳暂保持中文
- 新增 `README.en.md`

## 2026-07-04 — 移除助手语气设置

- 设置页删除 **助手语气** 卡片；移除 `AssistantTone*` 相关 Store、同步逻辑与测试
- 连接成功后不再向 Gateway 推送语气/人设 `systemText`

## 2026-07-04 — 产品收口：固定 main 助手

- 设置页 **隐藏** 智能体名称输入框；Release 固定连接 `agent:main:main`
- 新增 `AgentSessionPolicy.kt`；多 Agent 代码保留，README 说明进阶启用方式
- 多 Agent UI / 家庭多用户场景 **不再作为产品主推**

## 2026-07-04 — 多 Agent 支持 + chat.send 连接迁移

- **多 Agent**：设置页新增 **智能体名称**；`SessionKeyResolver` 组装 `agent:{name}:main`（默认 `main` → `agent:main:main`）
- **连接 API**：发消息改为 **`chat.send`**（含 `idempotencyKey`）；移除 `sessions.create` / `sessions.send`
- **收消息**：保留 **`sessions.messages.subscribe`**（connect → hello-ok → subscribe → chat.send）；无 subscribe 则 App 收不到 `session.message` / `chat.delta`
- **废弃** `AppSessionStore` per-device UUID sessionKey
- **Gateway bundle**：skill 安装至 `~/.openclaw/shared-skills/` + `skills.load.extraDirs` merge
- 文档：`multi_agent_gateway_setup.md`、契约 §1 更新；真机验收 main + 自定义 agent（Richard_01）

## 2026-07-03 — 连接稳定性（Phase A 搬家 + Phase B 前台 Service）

- **Phase A**：`LittleHelperApplication` + `GatewayConnectionManager`；连接/重连自 `MainViewModel` 迁出，行为不变
- **Phase B**：`GatewayConnectionService`（`dataSync` 前台 Service + 低优先级通知）
- Gateway 已配置时由 Manager 启停 Service；通知文案与 UI 对齐（静默重连中显示「正在连接」）
- **首连优化**：`onAppResumed` 不再在重连任务已运行时重复抢连；避免冷启动握手被中断
- **未改**重连间隔（60s grace / 2s / 3s pairing / 90s 限流）；无 v2 后台断连开关

## 2026-07-02 — 断开 local.properties Gateway 兜底

- Gateway 配置**仅**来自 App 内设置（`GatewaySettingsStore`）；移除 `BuildConfig.OPENCLAW_*` 编译注入
- 未保存设置前**不自动连接** Gateway（修复删装后未操作即出现配对申请）
- 设置页握手分步进度 v1（④ 步状态 + 可选折叠 deviceId）
- `local.properties` 仅保留 `sdk.dir`（开发者编译用）

## 2026-07-01 — Gateway 设置与文档对齐

- App 内 **⚙️ 设置**：Gateway 地址/端口/认证、TTS、助手语气；全屏顶栏 + 手势返回
- `EncryptedSharedPreferences` 持久化；首次安装须在 App 内配置 Gateway
- `AppSessionStore`：`agent:main:<UUID>`，禁止 `agent:main:main`；连接前 `sessions.create`
- 设置页 **WebSocket 测试握手**（`hello-ok`）
- MODAL wire 定为 **双线**（`===CHAT===` + `===MODAL===`），**禁止 `===END===`**
- README 开源用户向重构；详细历史迁入 `CHANGELOG.md`；`docs/DEVELOPER.md` 供贡献者
- 扫码配对 v1.1 占位（`openclaw qr` 待 Gateway 格式稳定）

## 2026-06-30 — Gateway 握手与连接健壮性

- `client.mode=ui` · `role=operator` · device-auth **v3**
- `hello-ok.policy`：`tickIntervalMs` 等；OkHttp WS ping 对齐
- **UNAVAILABLE** + `retryAfterMs` 握手重试
- **nonce race** 修复（重连时旧 challenge 不误发）
- 首次配对：Control UI → Devices 批准
- 探针：`scripts/openclaw_gateway_probe.py`

## 2026-06-28 — 白板 MRU 标签

- 单 WebView + 底部 MRU 标签（最多 6 + `…`）
- 同 `block.id` 刷新不增标签；`close` 按 id
- `table` / `markdown` / `chart/line` 原生块叠加，不占标签
- 冷启动不恢复白板标签；移除 10 页历史栈
- `connect`：`mode=ui`；`sessions.send` 不加 `client` 字段

## 2026-06-25 — 上传、画廊、下载

- 输入栏 `+` 上传；`[upload:fileId:fileName]` 附在消息末尾
- 画廊 v2：`__LITTLEHELPER_GALLERY__`；长按保存原图
- 下载至 `Download/LittleHelper/`；**📁 我的文件**
- 多模态 PDF / `__HOST__` 占位符 / magic-byte 校验

## 2026-06-23 — 品牌与聊天体验

- 应用名「龙虾助手」；新图标
- 气泡长按复制；左滑删除
- `ChatHistoryStore` 本地 JSON 持久化
- 地图 → 高德 App（`__LITTLEHELPER_MAP__`）

## 2026-06-22 — Gateway 白板全链路（T1–T9）

- MODAL：`webview` / `table` / `markdown` / `chart/line`
- `open` · `update` · `close`
- 契约：`docs/OPENCLAW_GATEWAY_CONTRACT.md` + Gateway skill
- 真场景：聊天气泡 + 路线地图 WebView

## 2026-06 — 架构精简（v2.0 哑终端壳）

- 移除 Native 高德 SDK、Room 记事本、Reminder、DeepSeek 本地秘书、App 内 ASR
- 仿微信文字栏；语音交给系统输入法
- 复杂页面由 Gateway WebView / MODAL HTML 承载

---

## 历史版本

| 版本 | 说明 |
|------|------|
| **v1.0** | 纯语音记事本（DeepSeek + Room） |
| **v2.0（早期）** | Native 高德、本地提醒、火山 ASR、DeepSeek 秘书 |
| **v2.0（当前）** | OpenClaw Gateway 哑终端壳（见上各日期条目） |

旧版 LOCAL 模式设计文档见 Git 历史。
