# 龙虾助手（LittleHelper）

**当前版本：2.0**（`versionCode` 2 · `versionName` 2.0）  
**应用显示名**：龙虾助手（桌面图标与系统设置中的中文名）

面向普通用户的极简 Android 助手客户端。App 定位为 **OpenClaw Gateway 哑终端壳**：语义理解、多模态编排、地图/看板等能力均由局域网 Gateway 完成；App 负责连接、聊天 UI、文字输入、MODAL 白板渲染与 TTS 播报。

> **2026-06 架构精简（当前主线）**  
> 已移除 Native 高德地图 SDK、本地 Room 记事本、Reminder 提醒、DeepSeek 本地秘书、App 内录音/火山 ASR 主链路。  
> 输入改为 **仿微信文字栏**，语音识别交给 **系统输入法自带语音**；地图与复杂页面由 Gateway 下发的 **WebView / MODAL HTML** 承载。

> **2026-06-25 近期重要更新（真机已验收）**  
> - **白板手势**：上下滚动与左右翻页分离——垂直滑动优先交给内容区；仅当横向位移明显大于纵向时才切换历史页，减少滚到顶/底时误触翻页  
> - **满 10 页历史**：左右滑手势覆盖整块白板（含底栏进度点与「左右滑动切换」提示）；索引越界自动校正；翻页回调不再因闭包过期而失效  
> - **冷启动白板**：历史内容仍从 `modal_history.json` 恢复，但面板**默认收起**；用户上滑拖柄再翻历史页  
> - **文件上传 v1**：输入栏 `+` → 拍照/相册/文件；上传成功后显示「已选：文件名（大小）」；点 **×** 可取消；发送时在消息末尾附加 `[upload:{fileId}:{fileName}]`（输入框仅保留用户自打文字）  
> - **画廊 v2**：Gateway Canvas 设 `window.__LITTLEHELPER_GALLERY__`；竖向单列；**长按单张**触发 `littlehelper://gallery/download?index=N` 保存原图（已移除 v1 多选批量栏）  
> - **原图/文件下载**：单图 `__LITTLEHELPER_IMAGE__` 右下角 **↓**、画廊长按均走 `StoredFileDownloader`；`thumbUrl` 用 Canvas 同源（`:18789`），`downloadUrl` 用 `:18889/file/download/…`；写入 **`Download/LittleHelper/`**（文件名 `{displayName}_{MMdd}.{ext}`）；聊天标题栏 **📁 我的文件** 可浏览/打开/删除  
> - **多模态下载（真机已验收）**：Playwright 生成 PDF → storage → `:18889`；画廊图片 + PDF + 未来游记共用 `__LITTLEHELPER_GALLERY__` / `__LITTLEHELPER_IMAGE__`；长按/↓ → **`Download/LittleHelper/`**；详见 [`scripts/_cursor_dev_multimodal_download_topic.txt`](scripts/_cursor_dev_multimodal_download_topic.txt)  
> - **下载健壮性**：`__HOST__` 占位符替换、文件 magic-byte 校验（杜绝假成功 Toast）、失败时 `/files/{fileId}` 回退重试

> **2026-06-23 近期重要更新（真机已验收）**  
> - **品牌**：应用中文名正式改为「龙虾助手」；桌面图标更新为龙虾吉祥物  
> - **聊天气泡**：长按选区复制；左滑露出「删除」并二次确认（流式中的 partial 气泡不可删）  
> - **聊天持久化**：会话写入 `filesDir/chat_history.json`（最多 500 条 / 约 2MB，防抖保存）；重启恢复  
> - **白板历史**：最多保留 10 页快照；左右滑切换；底栏 `左右滑动切换 · 长按删除`  
> - **白板持久化**：历史写入 `filesDir/modal_history.json`（最多 10 页 / 约 2MB）；冷启动恢复当前页内容，面板默认收起  
> - **地图 → 高德 App**：Gateway Canvas 设 `window.__LITTLEHELPER_MAP__`；标题栏右侧「用高德地图查看」（15sp，仅有效地图时出现）；Deep Link + H5 降级  
> - **契约**：高德跳转见 [`docs/OPENCLAW_GATEWAY_CONTRACT.md`](docs/OPENCLAW_GATEWAY_CONTRACT.md) §7.1；Canvas **禁止**自绘跳转大按钮  

> **2026-06-22 联调现状（真机已验收）**  
> - **Gateway 白板全链路**：`webview` / `table` / `markdown` / `chart/line`；`open` · `update` · `close`（T1–T9）  
> - **Canvas HTTP**：`/__openclaw__/canvas/*.html`；地图走受保护副本 `map.littlehelper.html`  
> - **真场景**：聊天气泡摘要 + 白板 WebView 路线地图（如华亭嘉园 → 天安门）  
> - **契约固化**：[`docs/OPENCLAW_GATEWAY_CONTRACT.md`](docs/OPENCLAW_GATEWAY_CONTRACT.md)；Gateway skill `workspace/skills/littlehelper-modal/SKILL.md`  
> - **App 侧**：已移除本地 `webview测试` 硬编码；MODAL 分轨解析、Canvas URL 改写、白板切换刷新、表格 schema 兼容  
> - **故障提示**：MODAL JSON 无效时顶部黄条「白板内容解析失败，已仅显示文字回复」

---

## 目录

- [功能概览](#功能概览)
- [近期更新（2026-06-25）](#近期更新2026-06-25)
- [近期更新（2026-06-23）](#近期更新2026-06-23)
- [联调现状（2026-06-22）](#联调现状2026-06-22)
- [架构与数据流](#架构与数据流)
- [文件上传架构](#-文件上传架构)
- [界面布局](#界面布局)
- [MODAL 多模态协议](#modal-多模态协议)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [项目结构](#项目结构)
- [技术栈](#技术栈)
- [开发与测试](#开发与测试)
- [权限说明](#权限说明)
- [已知限制与后续方向](#已知限制与后续方向)
- [历史版本说明](#历史版本说明)

---

## 功能概览

| 能力 | 说明 |
|------|------|
| **Gateway 长连接** | WebSocket 连接 OpenClaw Gateway；设备 Ed25519 鉴权；断线 10s 静默重连 |
| **文字对话** | 底部仿微信输入栏；系统键盘输入；可用输入法自带语音识别转文字后发送 |
| **流式回复** | 接收 Gateway `chat.delta`，聊天气泡逐字/逐段更新 |
| **MODAL 白板** | 解析 `===CHAT===` / `===MODAL===` / `===END===` 线框；底部面板顶出渲染 |
| **白板历史** | 最多 10 页快照；整块白板区域左右滑切换（含底栏）；轴向判定避免与上下滚动冲突；底栏进度点；长按删除当前页；本地 `modal_history.json` 持久化 |
| **地图 → 高德** | Canvas `__LITTLEHELPER_MAP__`；标题栏「用高德地图查看」；Deep Link + H5 降级 |
| **聊天气泡操作** | 长按复制；左滑删除（确认对话框）；同时仅一条展开删除态 |
| **聊天持久化** | `ChatHistoryStore` 本地 JSON；重启后恢复（不含 partial 流式条） |
| **多模态渲染** | 首批块类型：`table`、`markdown`、`webview`（HTTP URL 全屏加载） |
| **连接态 UX** | 标题栏 Gateway 指示灯；静默重连期间橙色「连接中」；超时后显示重试 Banner |
| **思考态** | 用户发送后、首条助手回复前显示「思考中…」动画气泡 |
| **TTS 播报** | 助手完整回复自动朗读（系统 TTS 引擎） |
| **文件上传** | `+` 选文件 → HTTP 上传到 Gateway `:18889`；已选提示可 × 取消；发送时附加 `[upload:fileId:fileName]` |
| **Canvas 文件下载** | 单图 **↓** / 画廊长按；GET `:18889` → **`Download/LittleHelper/`**；标题栏 **我的文件** 入口 |
| **Mock 联调** | `USE_OPENCLAW_MOCK=true` 时使用内存 Mock 客户端，无需真实 Gateway |

**已移除（不再提供）：**

- 本地 DeepSeek 秘书 + Room 记事本
- Native 高德 3D 地图 SDK
- App 内按住说话 + 火山 ASR
- 本地提醒闹钟 / 待办勾选 / Dashboard 记忆卡片

---

## 近期更新（2026-06-25）

本轮主要修复白板历史切换的手势体验，真机已验收。

### 白板手势与满页历史

| 问题 | 修复 |
|------|------|
| 内容可上下滚动时，滑到顶/底容易误触发左右翻页 | `ModalHistorySwipeGesture`：超过 touch slop 后若纵向占优则放弃；翻页需 `\|横向\| ≥ 48px` 且 `\|横向\| > \|纵向\| × 1.5` |
| 满 10 页时在底栏「10/10」区域滑动无反应 | 手势监听从内容 `Box` 提升到整块 `ModalHistoryCanvasShell` Column（含进度点与提示文案） |
| 持久化裁剪后 `currentIndex` 偶发越界，表现为滑不动 | `ModalHistoryState.normalized()`；load / save / navigate / delete 路径统一校正索引 |
| 翻页回调可能读到过期 `history` | `rememberUpdatedState` 持有最新 `onNavigateHistory`；边界判断交给 `ModalHistoryReducer.navigate` |

**手势约定（与底栏提示一致）：**

| 手势 | 行为 |
|------|------|
| 左滑（手指向左） | 较新的一页（index +1） |
| 右滑（手指向右） | 较旧的一页（index −1） |
| 在最新页（如 10/10） | 只能右滑查看更早页；在最早页（1/10）只能左滑 |

实现：`ModalHistorySwipeGesture.kt`、`ModalHistoryCanvasShell.kt`、`ModalHistoryReducer.kt`、`ModalHistoryModels.kt`。

### 冷启动与白板面板

| 行为 | 说明 |
|------|------|
| 有本地白板历史 | 恢复当前页 `blocks` 与历史队列，**面板默认收起** |
| 用户查看历史 | 上滑拖柄展开，底栏左右滑翻页 |
| 新 MODAL 到达 | 仍由 `SessionReducer` 自动顶出面板（与冷启动无关） |

### 文件上传（App 端 v1，真机已验收）

| 步骤 | 行为 |
|------|------|
| 点 `+` | `FilePickerSheet`：拍照 / 相册 / 文件（`rememberLauncherForActivityResult`） |
| 本地校验 | 图片 ≤10MB、PDF ≤30MB、其他 ≤10MB（`AttachmentSizeValidator`） |
| 上传 | `FileUploadManager` → `http://{OPENCLAW_GATEWAY_HOST}:18889/upload` |
| 上传成功 | 输入框上方显示 **已选：文件名（大小）**；**不**改写输入框文字 |
| 点 **×** | 清除 `pendingUploadResult`、`pendingAttachment` 与「已选」提示行；输入框文字不变；无附件时发送按钮恢复不可用 |
| 点发送 | 在消息文本末尾追加 ` [upload:{fileId}:{fileName}]`（仅文字也可发；仅附件也可发） |
| 发送成功 | 清空输入框与附件状态 |
| 发送失败 | 保留已选附件与 upload 结果，可重试发送或点 **×** 取消 |

示例：用户输入「帮我分析这张图」并附带已上传图片 → Gateway 收到：`帮我分析这张图 [upload:eb30ef603aa044b383ce7b7ae6b1881f:IMG_123.jpg]`

实现：`FilePickerSheet.kt`、`ChatInputBar.kt`、`FileUploadManager.kt`、`MainViewModel.onAttachmentPicked` / `sendComposerText` / `clearPendingAttachment`。

### Canvas 多模态文件下载（v2，真机已验收）

**全链路（图片 / PDF / 未来游记同一协议）：**

```
白板 webview → Gateway Playwright 生成 PDF / 图片入 storage
            → 18889 提供 downloadUrl
            → Canvas 注入 __LITTLEHELPER_GALLERY__ 或 __LITTLEHELPER_IMAGE__
            → 用户长按 / ↓
            → App 下载到 Download/LittleHelper/
            → 📁「我的文件」或系统文件管理器打开
```

Gateway 在 Canvas HTML 中注入全局对象，App 通过 `CanvasWebViewBridge` 读取并响应 Deep Link：

| 场景 | Gateway 约定 | App 行为 |
|------|--------------|----------|
| 单文件 | `window.__LITTLEHELPER_IMAGE__`（`displayName` / `downloadUrl` / `mimeType`） | 白板右下角 **↓** → 下载 |
| 多文件画廊 | `window.__LITTLEHELPER_GALLERY__.items[]`（可混排 **图片 + PDF**） | 竖向单列；**长按** ~500ms → `littlehelper://gallery/download?index=N` |
| 缩略图 | `thumbUrl`：**:18789** Canvas 同源 | WebView 内展示（PDF 可无 thumb，用占位图） |
| 原文件 | `downloadUrl`：**:18889** `http://{host}:18889/file/download/{storageFileName}` | `StoredFileDownloader` GET + 鉴权 |

**保存位置**：`Download/LittleHelper/`（系统公共下载目录）。

**文件名**：Gateway 提供 `displayName` → App 保存为 `{displayName}_{MMdd}.{ext}`（如 `大胖_0627.jpg`、`东京游记_0627.pdf`）；重名自动 `_2`、`_3`…

**我的文件**：聊天标题栏右侧 📁 → 列出 / 打开 / 删除。

**Gateway 协调文档**：[`scripts/_cursor_dev_multimodal_download_topic.txt`](scripts/_cursor_dev_multimodal_download_topic.txt)

实现：`GalleryDeepLink.kt`、`StoredFileDownloader.kt`、`LittleHelperFileSaver.kt`、`MyFilesRepository.kt`、`MyFilesSheet.kt`、`ModalHistoryCanvasShell.kt`。

---

## 近期更新（2026-06-23）

本轮更新均在真机验收通过，可作为当前稳定基线。

### 品牌与图标

| 项 | 说明 |
|----|------|
| 应用名 | `strings.xml` → `app_name` = **龙虾助手** |
| 图标 | Adaptive Icon + 龙虾吉祥物前景；`res/mipmap-*` / `mipmap-anydpi-v26` |
| 聊天标题 | 「与龙虾助手的对话」 |

> **开发提示**：Android Studio **Image Asset** 生成的 launcher XML 常把版权注释写在 `<?xml` 之前，会导致 `packageDebugResources` 失败。修复方式：把 `<?xml version="1.0" encoding="utf-8"?>` 挪到文件第一行。项目规则见 `.cursor/rules/android-launcher-xml.mdc`。

### 聊天气泡

- **复制**：`SelectionContainer`，长按进入系统文字选区
- **删除**：向左滑出红色「删除」→ 确认对话框；`isPartial` 流式气泡禁用删除
- **持久化**：`chat/ChatHistoryStore.kt` 写入 `chat_history.json`（上限 500 条）

### 本地持久化（聊天 + 白板）

两类内容均在 App 私有目录落盘，杀进程或重启后自动恢复：

| 数据 | 文件 | 模块 | 上限 | 恢复行为 |
|------|------|------|------|----------|
| 聊天记录 | `chat_history.json` | `ChatHistoryStore` | 500 条 / ~2MB | 恢复消息列表（不含 partial 流式条） |
| 白板历史 | `modal_history.json` | `ModalHistoryStore` | 10 页 / ~2MB | 恢复当前页 blocks；面板默认收起，用户上滑展开后可左右翻页 |

保存策略：状态变化后 **300ms 防抖**写入；删除聊天气泡或白板页时立即落盘。

### 白板历史（Story 式）

每次 MODAL `open` / `update` 将当前 blocks 深拷贝入队（最多 **10** 条）：

| 手势 / 操作 | 行为 |
|-------------|------|
| 白板区域左滑 | 较新的一页（含底栏进度点与提示条，2026-06-25 起） |
| 白板区域右滑 | 较旧的一页 |
| 上下滑动 | 滚动当前页内容（WebView / 多块 LazyColumn）；不与翻页冲突 |
| 底栏 `长按删除` | 从队列移除当前页；若队列为空则收起白板并清空本地文件 |
| 底栏进度点 | 当前页 / 总页数 |

实现：`ModalHistoryReducer`、`ModalHistorySwipeGesture`、`ModalHistoryCanvasShell`、`ModalHistoryStore`、`SessionReducer` 挂钩。

冷启动后：若存在已保存的白板历史，后台恢复当前页内容；**面板默认收起**，用户上滑拖柄可展开并左右翻页。

### 地图 → 高德 App（无 Native SDK）

Gateway 在 Canvas HTML 中设置 `window.__LITTLEHELPER_MAP__`（路线 / 单点 / 完整 `amapUrl`），详见契约 **§7.1**。

| App 模块 | 职责 |
|----------|------|
| `AmapCanvasInjector` | 页面加载后注入 `__LH_openAmap` / `__LH_hasAmap`；剥离 Gateway 遗留的大按钮 |
| `AmapDeepLink` | `shouldOverrideUrlLoading` 拦截 scheme；未装高德则 `uri.amap.com` H5 |
| `CanvasWebViewBridge` | WebView ↔ 原生标题栏「用高德地图查看」 |
| `ModalCanvasHost` | 标题行右侧显示入口（15sp，仅 `__LH_hasAmap()` 为真） |

**Gateway 禁止**：在 Canvas 内画「高德导航」「网页版」「用高德地图查看」等大按钮；跳转入口由 App 标题栏统一提供。

---

## 联调现状（2026-06-22）

### 已通过（真机）

| 类别 | 内容 |
|------|------|
| **WebView 能力** | Gateway 发布 `webview_spec_test.html`；高德瓦片满屏、± 缩放 |
| **白板测试 T1–T9** | 静态 WebView · 表格 · Markdown · 折线图 · 组合 · 动态 HTML · 真地图 · update · close |
| **业务场景** | 自然语言问路（华亭嘉园 → 天安门）：气泡路线摘要 + 白板路线地图 |

### App ↔ Gateway 约定（已文档化）

| 资源 | 路径 |
|------|------|
| 集成契约（权威） | [`docs/OPENCLAW_GATEWAY_CONTRACT.md`](docs/OPENCLAW_GATEWAY_CONTRACT.md) |
| Gateway skill 镜像 | `scripts/skills/littlehelper-modal/SKILL.md` |
| Gateway 部署目标 | `~/.openclaw/workspace/skills/littlehelper-modal/SKILL.md` |
| Canvas 页面目录 | `~/.openclaw/canvas/` → HTTP `/__openclaw__/canvas/` |

推送 skill 到 Gateway：`python scripts/publish_littlehelper_gateway_skill.py`  
Cursor ↔ Gateway 开发协调：`python scripts/openclaw_dev_session.py "话题"`（session `agent:main:cursor-dev`）

### App 侧关键实现（代码已落地）

- `GatewayEventMapper`：`session.message` / `chat.delta` 的 `deltaText`、`payload.modal` 与 wire 正文合并
- `ModalCanvasUrlResolver`：相对 Canvas URL；绝对 URL 改写到当前 `gatewayBaseUrl`
- `GatewayCanvasUrlNormalizer`：`map.html` → `map.littlehelper.html`
- `ModalCanvasHost` / `WebViewBlockRenderer`：白板撑满、`loadRevision` 切换时重建 WebView
- `ModalHistoryCanvasShell` / `ModalHistorySwipeGesture`：白板历史切换、轴向翻页手势与底栏交互
- `AmapDeepLink` / `AmapCanvasInjector` / `CanvasWebViewBridge`：高德 Deep Link 与标题栏入口
- `ChatBubble`：复制 + 左滑删除
- `ChatHistoryStore`：会话本地持久化
- `ModalHistoryStore`：白板历史本地持久化
- `TableBlockRenderer`：table 对象 schema + 字符串数组简写兼容
- `OpenClawStatusBanner`：MODAL 解析失败黄条

### 常见问题

| 现象 | 原因 |
|------|------|
| 白板显示「等待 OpenClaw 指令」 | Gateway 未发 MODAL，仅文字回复 |
| 黄条「白板内容解析失败…」 | 已有 `===MODAL===` 但其后 JSON **语法错误**、被截断、或包了 ` ```json ` 等；线框对齐≠ JSON 一定合法；黄条会粘住直到下一条成功 MODAL 或用户发消息 |
| 白板内容不刷新 | 新回复无 MODAL；或需等新 `action:open` |
| 文件下载成功但找不到 | 请查看 **Download/LittleHelper**（系统文件管理器 → 下载），或 App 内 **我的文件** |

---

## 架构与数据流

### 设计原则

```
用户输入文字（或输入法语音转文字）
        ↓
MainViewModel.sendComposerText()
        ↓
SessionController.sendTextMessage()
        ↓
WebSocketOpenClawSessionClient → OpenClaw Gateway
        ↓
Gateway 返回 ClawSessionEvent 流
        ↓
SessionReducer（纯函数归约）→ ShellUiState
        ↓
ShellUiProjector → MainScreen（聊天 + 白板 + 输入栏）
```

- **Gateway 负责**：意图理解、业务逻辑、生成 MODAL JSON、地图 HTML 等
- **App 负责**：WebSocket 传输、状态归约、Compose UI、WebView 渲染、TTS

### 核心模块

| 包 | 职责 |
|----|------|
| `shell/transport/` | `WebSocketOpenClawSessionClient`、Gateway 握手/鉴权、`GatewayEventMapper` |
| `shell/session/` | `SessionController` 生命周期；`SessionReducer` 事件 → 状态 |
| `shell/parser/` | `MessageBlockParser` 剥离 CHAT/MODAL 线框 |
| `shell/modules/` | `ModuleHost`、`ModalCanvasHost`、table/markdown/webview 渲染器 |
| `shell/projection/` | `ShellUiProjector` 将 `ShellUiState` 投影为 UI 读模型 |
| `viewmodel/` | `MainViewModel`：连接管理、发送消息、TTS、面板状态 |
| `ui/layout/` | `ChatFlowSection`、`MultiFunctionPanel`、`ChatInputBar` |

### Session 事件流

```
ClawSessionEvent
├── SessionOpened / ConnectionState 变化
├── ChatDelta / ChatFinal          → 更新 messages、streamingAssistantRaw
├── IntentPreload / IntentFinal    → 切换 activeModule、modulePayload
└── TurnUploading / …              → capturePhase（保留，供未来扩展）
```

`SessionReducer` 是唯一允许修改 `ShellUiState` 的纯函数入口，便于单测与审计。

---

## 📤 文件上传架构

### 当前实现（v1）

```
手机 App ─── HTTP POST ──→ Gateway 机 (:18889)
              multipart        upload_server.py（伴生服务）
                               → ~/.openclaw/uploads/{fileId}_{originalName}
                               → Agent 通过 fileId 定位并处理
```

App 端组件：

- `FilePickerSheet.kt` — 底部选择菜单（拍照 / 相册 / 文件）
- `ChatInputBar.kt` — 输入栏 `+`、已选提示行、**×** 取消附件
- `FileUploadManager.kt` — HTTP multipart 上传到 Gateway 机 **18889** 端口（host 取自 `OPENCLAW_GATEWAY_HOST`）
- `AttachmentSizeValidator.kt` — App 端选文件后、上传前体积校验
- `MainViewModel` — 暂存 `pendingUploadResult` / `pendingAttachment`；**发送时**在消息末尾追加 `[upload:{fileId}:{fileName}]`（输入框不展示该标记）；`clearPendingAttachment()` 取消已选；发送成功后才清除附件状态

Gateway 端组件：

- `scripts/upload_server.py` — Python HTTP 服务（**不依赖 cgi**，兼容 Python 3.14），监听 18889；保存为 `{fileId}_{originalName}`
- `scripts/process_upload.py` — 解析消息中的 `[upload:…]` 并按扩展名分流处理（图片 / PDF / Excel / Word / CSV / 文本）
- 随 `gateway.cmd` 自启

**App 端上传前体积上限**（与 Gateway 50MB 上限独立）：图片 10MB · PDF 30MB · 其他 10MB。

### 上架前改造

当前架构需要用户 Gateway 机有 Python 3.x 环境，非技术用户有门槛。
上架前建议统一为「Gateway Plugin」形态——将上传服务作为 Gateway 的插件/扩展组件，
用户在 App 引导下自动部署，无需手动装 Python 或配置端口。

**约束：**

- 文件数据不进 LLM 上下文（只有 fileId 标记进消息），避免 token 浪费
- 单文件 ≤ 50MB
- 临时文件 24h 清理

---

## 界面布局

`MainScreen` 采用 **Column 两段式 + 底部固定输入**：

```
┌─────────────────────────────────┐
│  OpenClawStatusBanner（断线时）   │
├─────────────────────────────────┤
│  ChatFlowSection                │
│  · 标题「与龙虾助手的对话」+ 指示灯 │
│  · 聊天气泡（复制 / 左滑删除）     │
│  · 思考中动画气泡                 │
│  （weight=1，随键盘/面板压缩）     │
├─────────────────────────────────┤
│  MultiFunctionPanel（底部抽屉）   │
│  · 收起：28dp 拖柄               │
│  · 展开：约 2/3 屏高             │
│  · MODAL：Canvas 块 + 历史底栏    │
│  · 地图页：标题栏右侧「用高德…」   │
├─────────────────────────────────┤
│  ChatInputBar（仿微信输入栏）     │
│  · 左侧 + 附件（拍照/相册/文件）   │
│  · 已选提示 + × 取消              │
│  · 圆角文本框 + 发送按钮 ↑        │
│  · 系统 IME + 输入法自带语音识别   │
└─────────────────────────────────┘
```

| 交互 | 行为 |
|------|------|
| **发送** | 有文字或已选附件且 Gateway 在线时可发；附件在发送时附加 `[upload:…]` 标记 |
| **附件 +** | 底部 Sheet 选文件 → 上传成功显示「已选：…」；右侧 **×** 取消已选（上传/发送进行中时禁用） |
| **面板拖拽** | 拖柄上滑展开 / 下滑收起；MODAL 打开时自动顶出约 2/3 高度；**冷启动默认收起** |
| **复制气泡** | 长按气泡文字进入系统选区 |
| **删除气泡** | 左滑露出删除 → 确认；流式 partial 不可删 |
| **白板历史** | 白板整块区域左右滑切换页（含底栏）；上下滑滚动内容；底栏长按删除当前页 |
| **打开高德** | 地图白板标题栏右侧（仅有效 `__LITTLEHELPER_MAP__`） |
| **TTS** | 助手完整回复到达后自动朗读 |

---

## MODAL 多模态协议

> **完整契约**（App ↔ Gateway 双向约定、故障对照、联调清单）：[`docs/OPENCLAW_GATEWAY_CONTRACT.md`](docs/OPENCLAW_GATEWAY_CONTRACT.md)  
> Gateway 持久 skill：`~/.openclaw/workspace/skills/littlehelper-modal/SKILL.md`（仓库镜像 `scripts/skills/littlehelper-modal/SKILL.md`）

Gateway 助手回复可携带线框分段：

```
===CHAT===
好的，以下是今天的持仓情况。

===MODAL===
{"action":"open","blocks":[
  {"id":"holdings-table","type":"table","data":{...}},
  {"id":"daily-chart","type":"chart/line","data":{...}}
]}
===END===
```

| 段 | App 行为 |
|----|----------|
| `===CHAT===` | 提取纯文本展示在聊天气泡（不含 MODAL 段） |
| `===MODAL===` | JSON 解析 → `ModalState.blocks` → `ModalCanvasHost` 渲染 |
| `===END===` | 标记 MODAL 段结束 |

**块渲染器（`shell/modules/renderers/`）：**

| type | 渲染方式 |
|------|----------|
| `table` | Compose 表格占位（`TableBlockRenderer`） |
| `markdown` | Compose Markdown 块（`MarkdownBlockRenderer`） |
| `webview` | `WebView` 加载 HTTP(S) URL（`WebViewBlockRenderer`）；Gateway Canvas 鉴权见 `GatewayCanvasAuth` |
| `chart/line` | 占位渲染（`ChartLinePlaceholderRenderer`） |

**action**：`open`（新白板）· `update`（按 id 合并 block）· `close`（收起）。T1–T9 真机已验收。

地图、仪表盘等复杂内容建议 Gateway 下发 **`webview` 块 + HTML 页面 URL**，由 App 全屏 WebView 承载，无需 Native SDK。

**地图跳转（§7.1）**：Gateway 只设 `window.__LITTLEHELPER_MAP__`；App 标题栏提供「用高德地图查看」，禁止 Canvas 自绘跳转按钮。

**Canvas 媒体（画廊 / 单文件 / PDF）**：`__LITTLEHELPER_GALLERY__` / `__LITTLEHELPER_IMAGE__`；`thumbUrl`（`:18789`）与 `downloadUrl`（`:18889`）分轨；Playwright PDF → 长按下载至 `Download/LittleHelper/`。详见 [Canvas 多模态文件下载](#canvas-多模态文件下载v2真机已验收) 与 [`scripts/_cursor_dev_multimodal_download_topic.txt`](scripts/_cursor_dev_multimodal_download_topic.txt)。

---

## 快速开始

### 1. 配置 `local.properties`

复制 `local.properties.example` 为 `local.properties`（已在 `.gitignore`，不会提交）：

```properties
sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk

# OpenClaw Gateway（必填，当前主线）
USE_OPENCLAW_SHELL=true
USE_OPENCLAW_MOCK=false
OPENCLAW_GATEWAY_HOST=192.168.1.55
OPENCLAW_GATEWAY_PORT=18789
OPENCLAW_GATEWAY_PASSWORD=你的Gateway密码
OPENCLAW_GATEWAY_TOKEN=你的GatewayToken
```

Mock 联调 UI（无需真实 Gateway）：

```properties
USE_OPENCLAW_SHELL=true
USE_OPENCLAW_MOCK=true
```

### 2. 编译安装

```bat
cd d:\Dev\LittleHelper
gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 3. 运行单元测试

```bat
gradlew.bat testDebugUnitTest
```

报告：`app/build/reports/tests/testDebugUnitTest/index.html`

---

## 配置说明

| 配置项 | 作用 | 是否必填 |
|--------|------|----------|
| `USE_OPENCLAW_SHELL` | `true` 启用 Gateway 壳模式 | **是**（当前主线） |
| `USE_OPENCLAW_MOCK` | `true` 使用 Mock 客户端 | 联调时可选 |
| `OPENCLAW_GATEWAY_HOST` | Gateway IP / 域名 | 实连时必填 |
| `OPENCLAW_GATEWAY_PORT` | Gateway 端口（默认 18789） | 实连时必填 |
| `OPENCLAW_GATEWAY_PASSWORD` | Gateway 连接密码 | 按 Gateway 配置 |
| `OPENCLAW_GATEWAY_TOKEN` | Gateway Token | 按 Gateway 配置 |
| `DEEPSEEK_API_KEY` | 本地 DeepSeek | **已废弃**，可留空 |
| `VOLC_*` | 火山 ASR | **已停用**，代码保留未接入主链路 |
| `AMAP_API_KEY` | 高德 SDK | **已移除**，无需配置 |

编译开关写入 `BuildConfig`，由 `OpenClawSessionClientFactory` 在启动时选择 Mock 或 WebSocket 客户端。

---

## 项目结构

```
LittleHelper/
├── docs/
│   └── OPENCLAW_GATEWAY_CONTRACT.md # App ↔ Gateway 集成契约
├── scripts/
│   ├── upload_server.py             # 文件上传 HTTP 服务 (:18889)
│   ├── process_upload.py            # 解析 [upload:…] 并拉取文件
│   ├── openclaw_dev_session.py      # Cursor dev 协调通道
│   ├── publish_littlehelper_gateway_skill.py
│   ├── fetch_canvas.py
│   ├── gateway_whiteboard_test_plan.txt
│   ├── webview_spec_test.html       # Canvas 能力测试页（同步到 Gateway）
│   └── skills/littlehelper-modal/SKILL.md
├── app/src/main/java/com/littlehelper/
│   ├── MainActivity.kt              # 入口：权限、Compose、TTS 绑定
│   ├── AppModels.kt                 # AppPhase、ChatMessage、PanelState 等
│   ├── chat/                        # ChatHistoryStore 本地会话持久化
│   ├── attachment/                  # PickedAttachment、AttachmentSizeValidator
│   ├── media/                       # StoredFileDownloader、LittleHelperFileSaver、MyFilesRepository
│   ├── upload/                      # FileUploadManager
│   ├── viewmodel/
│   │   └── MainViewModel.kt         # Gateway 连接、发送、TTS、面板/历史状态
│   ├── shell/
│   │   ├── model/                   # ShellUiState、ClawSessionEvent、ModulePayload
│   │   ├── modal/                   # ModalState、ModalHistory*、ModalHistoryStore
│   │   ├── session/                 # SessionController、SessionReducer
│   │   ├── transport/               # WebSocket 客户端、Gateway 映射、设备鉴权
│   │   ├── parser/                  # MessageBlockParser
│   │   ├── modules/                 # ModuleHost、ModalCanvasHost、Amap*、ModalHistorySwipeGesture
│   │   ├── projection/              # ShellUiProjector
│   │   └── demo/                    # Mock 演示数据（持仓 MODAL）
│   ├── ui/
│   │   ├── MainScreen.kt            # 主界面 Column 布局
│   │   ├── layout/
│   │   │   ├── ChatFlowSection.kt   # 聊天列表 + Gateway 指示灯
│   │   │   ├── MultiFunctionPanel.kt# 底部 MODAL 抽屉
│   │   │   ├── ChatInputBar.kt      # 输入栏 + 附件、已选 × 取消
│   │   │   ├── FilePickerSheet.kt   # 拍照/相册/文件 BottomSheet
│   │   │   └── OpenClawStatusBanner.kt
│   │   ├── attachment/              # AttachmentFileReader（URI → bytes）
│   │   └── components/              # ChatBubble、GatewayConnectionDot 等
│   ├── network/                       # Volc ASR 代码保留（未接入主链路）
│   ├── speech/                      # AudioRecorderManager 保留（未接入主链路）
│   └── tts/TtsManager.kt
├── app/src/test/java/com/littlehelper/
│   └── shell/ …                     # SessionReducer、Gateway、MODAL 单测
├── local.properties.example
└── README.md
```

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| 最低 SDK | 24（Android 7.0） |
| 目标 SDK | 36 |
| UI | Jetpack Compose（Material3） |
| 架构 | ViewModel + StateFlow；`SessionReducer` 纯函数归约 |
| 网络 | OkHttp WebSocket；Gateway v4 协议 |
| 多模态 | Compose + Android WebView |
| 语音播报 | Android TextToSpeech |
| 测试 | JUnit 4 |

**已移除依赖：** Room、KSP、高德 3D 地图 SDK、pinyin4j

---

## 开发与测试

### Gateway / Canvas 脚本

| 脚本 | 用途 |
|------|------|
| `scripts/openclaw_dev_session.py` | Cursor ↔ Gateway 开发协调（`agent:main:cursor-dev`） |
| `scripts/run_dev_session_lan.py` | 局域网 dev session（传 topic 文件路径） |
| `scripts/_cursor_dev_multimodal_download_topic.txt` | 多模态下载协议（图片/PDF/游记，真机基线） |
| `scripts/publish_littlehelper_gateway_skill.py` | 推送 MODAL 规范 skill 到 Gateway |
| `scripts/fetch_canvas.py` | 检查 Canvas HTTP 是否 200 |
| `scripts/upload_server.py` | 启动 Gateway 伴生上传服务（`:18889`） |
| `scripts/process_upload.py` | 调试：从消息 `[upload:…]` 拉取并打印文件内容 |
| `scripts/gateway_whiteboard_test_plan.txt` | T1–T9 白板测试计划（联调记录） |

### 单元测试覆盖重点

| 测试类 | 覆盖范围 |
|--------|----------|
| `SessionReducerTest` | Gateway 事件归约、MODAL 开/关、流式去重 |
| `MessageBlockParserTest` | CHAT/MODAL/END 线框解析 |
| `GatewayEventMapperTest` | Gateway JSON → `ClawSessionEvent` |
| `ModalStateReducerTest` | MODAL 状态归约 |
| `WebViewBlockRendererTest` | WebView 块 URL 与布局 |
| `OpenClawConnectHandshakeTest` | connect 握手 |
| `OpenClawDeviceAuthTest` | Ed25519 设备鉴权 |
| `GatewayCanvasAuthTest` | Canvas WebView 鉴权头 |
| `TableBlockRendererTest` | table headers/rows 解析（含简写格式） |
| `ModalCanvasUrlResolverTest` | Canvas URL 拼接与绝对地址改写 |
| `ModalHistoryStoreTest` | 白板历史持久化与体积裁剪 |
| `ModalHistoryReducerTest` | 白板历史队列、满 10 页切换、索引校正、删除 |
| `ModalHistorySwipeGestureTest` | 翻页手势轴向判定（横/纵位移比） |
| `AttachmentSizeValidatorTest` | 附件体积上限（图片/PDF/其他） |
| `FileUploadManagerTest` | 上传响应 JSON 解析 |
| `AmapDeepLinkTest` | 高德 scheme 识别与 H5 降级 URL |
| `AmapCanvasInjectorTest` | Canvas 注入白名单与路径判断 |
| `ChatHistoryStoreTest` | 会话持久化与上限裁剪 |
| `VolcAsrBinaryProtocolTest` | 火山 v3 协议（遗留，未接主链路） |

```bat
gradlew.bat testDebugUnitTest
```

---

## 权限说明

| 权限 | 用途 |
|------|------|
| `INTERNET` | 连接 OpenClaw Gateway；WebView 加载远程页面；HTTP 上传文件 |
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | 网络状态检测 |
| `POST_NOTIFICATIONS` | 预留（当前无本地提醒功能） |
| `CAMERA` | 附件菜单「拍照」 |
| `READ_MEDIA_IMAGES` | 附件菜单「从相册选择」（API 33+） |

Manifest `<queries>` 声明：`com.autonavi.minimap`（高德 App 是否已安装）、TTS 服务。

**已移除权限：** `RECORD_AUDIO`、定位、精确闹钟、开机广播等（随 Native 地图 / 提醒模块一并删除）。

---

## 已知限制与后续方向

### 已知限制

1. **聊天持久化**：写入本地 JSON；`isPartial` 流式条不保存；清数据 / 卸载后丢失
2. **Gateway 离线**：10s 静默重连；超时后显示 Banner，需用户点重试
3. **WebView 仅支持 URL**：不支持 inline HTML 字符串（`loadData`）；Gateway 需提供可访问 HTTP(S) 地址
4. **白板历史上限**：内存与磁盘均最多 10 页快照，超出丢弃最旧；清数据 / 卸载后丢失。在最新页只能右滑看更早页，在最早页只能左滑看更新页
5. **可滚动白板页**：WebView 内横向拖动由页面消费，可能无法触发历史翻页；请在白板边缘或底栏区域做明显横向滑动
6. **MODAL `action: update`**：T8 真机已通过；复杂场景仍可能整板刷新
7. **助手语气设置**：`AssistantToneStore` 与设置 Sheet 代码保留，入口待统一 Settings 页
8. **Gateway 规范持久化**：需 `python scripts/publish_littlehelper_gateway_skill.py` 推送 skill；见 [`docs/OPENCLAW_GATEWAY_CONTRACT.md`](docs/OPENCLAW_GATEWAY_CONTRACT.md)
9. **契约文档滞后**：`docs/OPENCLAW_GATEWAY_CONTRACT.md` 仍缺画廊 v2、原图下载分轨等（以本 README 与 `scripts/_cursor_dev_*_topic.txt` 联调记录为准）

### 后续方向

- [ ] Gateway skill 自动加载路径复查（`workspace/skills/littlehelper-modal/SKILL.md`）
- [ ] MODAL `type: map` 专用块（当前用 webview + HTML）
- [ ] App 设置页（Gateway 地址、助手语气等）
- [ ] Gateway thinking 状态细粒度 UI 反馈

---

## 历史版本说明

| 版本 | 说明 |
|------|------|
| **v1.0** | 纯语音记事本（DeepSeek + Room） |
| **v2.0（早期）** | 叠加 Native 高德地图、本地提醒、火山 ASR 按住说话、DeepSeek 秘书 |
| **v2.0（当前）** | **OpenClaw 哑终端壳**：2026-06-22 T1–T9 联调；**2026-06-23** 品牌「龙虾助手」、聊天/白板本地持久化、白板 Story 历史、高德标题栏入口；**2026-06-25** 白板翻页手势、冷启动面板默认收起、文件上传 v1、画廊 v2 长按下载至 `Download/LittleHelper`、**我的文件** 入口 |

旧版 LOCAL 模式相关文档（DB_OPS 协议、高德 VIEW_LOCATION、Reminder 调度等）已从代码库删除；若需查阅历史设计，见 Git 历史或 `docs/` 目录（如有保留）。

---

## 许可证

本项目为个人/家庭使用场景开发，API Key 与 Gateway 凭证请自行保管，勿提交到公开仓库。
