# 龙爪（LoongClaw for Android）

**v2.1.3** · Android 客户端 · 中文名「龙爪」 · 包名 `com.littlehelper`

> 英文说明见 [README.md](README.md)（GitHub 默认首页，内容与本文同步更新中）。

连接你家或云端的 **OpenClaw Gateway**，在手机上聊天、看白板、听 TTS、上传附件、下载 Gateway 生成的文件。语义与多模态由 Gateway 完成，App 负责连接与展示。

### 界面预览

真机截屏（v2.1.3）：连接 Gateway 后聊天、设置握手，以及 MODAL 白板与「我的文件」。

| | | |
|:---:|:---:|:---:|
| ![启动器](docs/Icon.jpg) | ![主聊天](docs/WelcomPage.jpg) | ![设置](docs/setting.jpg) |
| 启动器 · 龙爪 | 主聊天与欢迎 | 连接设置与握手 |

| | | |
|:---:|:---:|:---:|
| ![天气白板](docs/Weather.jpg) | ![基金估值](docs/Finance.jpg) | ![画廊](docs/Gallery.jpg) |
| 天气 MODAL | 基金估值白板 | 画廊分类浏览 |

| |
|:---:|
| ![我的文件](docs/FileManager.jpg) |
| 助手端文件管理 |

---

## 产品定位

**龙爪**面向普通用户的 **单一超级助手**：开箱连接 Gateway 默认主会话，不需要在 App 里选 Agent、配语气或理解 sessionKey。

| 项 | 当前产品行为 |
|----|--------------|
| 对外名称 | 中文 **龙爪** / 英文 **LoongClaw**（启动器名随界面语言变化） |
| 包名 / 技术标识 | `com.littlehelper`（与 Gateway skill `littlehelper-modal` 等保持不变） |
| 固定会话 | **`agent:main:main`**（与 Control UI 主会话一致） |
| 连接方式 | `ws://` + Tailscale / 局域网（**不支持** `wss://` 公网） |
| 多 Agent | 代码保留，**设置页不提供**；见 [§进阶：多 Agent](#进阶多-agent代码级默认未启用) |

---

## 主要功能

| | |
|---|---|
| 💬 **对话** | 文本输入栏；流式回复；本地聊天历史（可删单条 / 清空） |
| 📋 **MODAL 白板** | 表格、Markdown、WebView；底部标签切换（最多 6 个） |
| 🗺️ **地图** | App 支持地图 MODAL + 高德跳转；**安装包不含**地图 canvas，需 Gateway 自备（见 bundle README） |
| 📎 **附件** | 拍照 / 相册 / 文件上传到 Gateway（`:18889` sidecar） |
| 📁 **我的文件** | 浏览与管理 `Download/LoongClaw/` 下已下载文件 |
| 🖼️ **画廊等** | 依赖 Gateway 参考 skill（bundle `standard` 含 gallery / file-manager 等） |
| 🔊 **TTS** | Gateway 助手回复朗读（设置内可关） |
| ⚙️ **连接** | App 内填 Gateway 地址与 Token/密码；**四步握手进度** |
| 🌐 **界面语言** | 设置内 **中文 / English**（部分界面，见下节） |
| 📡 **后台保活** | 保存连接后 **前台 Service** + 通知栏；息屏后尽量保持 WebSocket |

Gateway 侧须安装 skill 等资源。推荐用仓库自带的 **Gateway Companion Bundle**（`standard` 含 MODAL 核心 skill + 画廊/文件/记事本/天气等**参考 skill** 及配套脚本，见 [§Gateway 配套安装](#3-gateway-配套安装)）。

---

## 界面语言（v2.1.3）

设置页提供 **中文 | English** 两个选项（单行切换），选择后界面会刷新。

**会随语言切换的部分**

- ⚙️ 设置（Gateway 字段、握手四步、保存/测试按钮等）
- ➕ 添加附件 Sheet、输入栏占位符
- 📁 **我的文件** 列表与删除确认

**仍为中文的部分（已知限制）**

- 主聊天标题、欢迎语、清空记录对话框
- 连接失败顶部横幅与 Toast 错误提示
- Gateway 返回的助手回复（由 Agent / skill 决定）

完整英文 UI 计划在后续版本完善。英文用户可先读 [README.md](README.md)。

---

## 快速开始

### 1. 自建 Gateway（家里 PC + Tailscale）— 推荐

适合：Gateway 跑在你家电脑或 NAS，手机通过 Tailscale 内网访问。

1. 在 Gateway 主机完成 **§3 [Gateway 配套安装](#3-gateway-配套安装)**（OpenClaw 首次配置 + Companion Bundle）。
2. 手机安装 **Tailscale**，与主机在同一 tailnet。
3. 安装 **龙爪** App（见 [§获取与编译](#获取与编译)）。
4. **首次启动**会自动打开 **⚙️ 设置**（**不会**自动连接 Gateway，也不会在 Control UI 里产生配对申请）。
5. 填写连接信息：
   - **服务器地址**：主机 Tailscale IP（如 `100.x.x.x`）或局域网 IP
   - **端口**：默认 `18789`
   - **认证**：默认 **Token**；`openclaw config get gateway.auth.token` 或 Control UI `/pair qr`（UI 显示 `REDACTED` 时用 CLI）
6. （可选）**界面语言** 选 **English** 或保持 **中文**。
7. 点 **测试握手**：下方显示四步进度（Token → 配对 → 批准 → 连接）；按提示在 Control UI 批准设备。
8. 点 **保存并连接**（主界面才发起长连接）。
9. 连接成功后固定使用 **`agent:main:main`**。通知栏出现 **「Gateway 连接」**；建议系统设置 → 应用 → 龙爪 → 省电 → **无限制**。

> **扫码连接**：设置页为灰色占位，**当前不支持**。

### 2. 云端 Gateway（VPS + 公网）

公网 `wss://` **当前不支持**。若用 VPS + `ws://` 自行验证可达性：

1. 在 Gateway 主机完成 **§3 [Gateway 配套安装](#3-gateway-配套安装)**（同自建场景）。
2. App 设置填入公网地址、端口 `18789`、Token/密码。
3. **测试握手** → **保存并连接** → Control UI 批准设备。

### 3. Gateway 配套安装

完整体验需要 Gateway 主机上的 skill、Canvas、上传 sidecar 与 workspace 脚本。仓库提供 **Gateway Companion Bundle** 一键安装（**Windows Gateway only**；OpenClaw **>=2026.6.9**）。

更细的 profile 说明、dev 多实例参数见 [gateway-bundle/README.md](gateway-bundle/README.md)。

#### 前提条件

1. **Node.js** 推荐 **24 LTS**（`npm i -g openclaw`）；装完 npm 后**重开 PowerShell**（PATH 刷新）
2. **Python 3.10+**（install / doctor / upload sidecar / workspace 脚本）
3. Win 11 默认禁脚本：`Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned`
4. OpenClaw **`>=2026.6.9`**

> **安装 bundle 前**：请确认 `openclaw --version` **不低于 2026.6.9**。当前 `install.ps1` **不会**自动拦截低版本；版本过低可能导致握手或白板协议不兼容。

#### 安装 OpenClaw（首次）

```powershell
openclaw setup
openclaw onboard                              # 配置 API key 等
openclaw config set gateway.bind "lan"       # 手机/Tailscale 连接必须；默认 loopback 仅本机
openclaw gateway                               # 前台确认启动正常（勿先用 gateway start，可能静默失败）
```

> **⚠️ 禁止手动编辑 `openclaw.json` 的 gateway 项**（Token、bind、port 等）。用 `openclaw config set` / `openclaw configure`。  
> bundle `install` **仅** merge **`skills.load.extraDirs`**；其余 Gateway 配置仍走 CLI。

#### 环境差异（常见，一般可忽略）

| 现象 | 处理 |
|------|------|
| Node 安装时 `workload-vctools` / Python 组件失败 | 一般不影响 OpenClaw，可跳过 |
| 中文用户名下 `.bat` 乱码 | upload sidecar **优先用 PowerShell**（见下方 `python ... upload_server.py`） |
| Control UI 打不开或 404 | 路径为 **`/`**，不是旧版 `/ui` |
| Token 在 UI 显示 `REDACTED` | `openclaw config get gateway.auth.token`，或 Control UI **`/pair qr`** |
| 需要带 token 的 Control UI 链接 | `openclaw dashboard --no-open` |
| `openclaw gateway start` 无反应 | 先用前台 **`openclaw gateway`** 验证；通过后再考虑后台/服务方式 |

#### 安装 Companion Bundle

**Release 产物（v2.1.3）**

- 打包：`python scripts/build_gateway_bundle.py`
- 输出：`dist/loongclaw-gateway-bundle-2.1.3.zip`

**步骤（Gateway 管理员，PowerShell）**

```powershell
# 1. 解压
Expand-Archive dist\loongclaw-gateway-bundle-2.1.3.zip -DestinationPath .
cd loongclaw-gateway-bundle-2.1.3

# 2. 安装 bundle（龙爪 App 默认即可，无需 patch client id）
powershell -ExecutionPolicy Bypass -File .\install.ps1
# 仅 MODAL 核心：.\install.ps1 -Profile minimal
# LoongClaw PC 客户端另需：.\install.ps1 -WithPcPatch

# 3. 重启 openclaw gateway 后，另开终端启动 upload sidecar（standard 必做）
python $env:USERPROFILE\.openclaw\companion\upload_server.py

# 4. 自检
.\doctor.ps1
```

`doctor` 全 `[OK]` 后，维护者发版前建议再过一遍 [bundle 回归清单](docs/BUNDLE_2.1.1_REMEDIATION.md#六下一版-bundle-回归清单15-分钟)（PDF 预览、文件删除、`save_to_storage` 进画廊等，约 15 分钟）。

全部 `[OK]` 后，手机侧：

1. 安装龙爪 App（见 [§获取与编译](#获取与编译)）
2. `ipconfig` 查 Gateway 主机局域网 / Tailscale IP
3. Token：`openclaw config get gateway.auth.token` 或 Control UI `/pair qr`
4. App **⚙️ 设置** → 填地址/端口/Token → **测试握手** → **保存并连接**

龙爪 App 使用内置 **`openclaw-android`**，**不必** `-WithPcPatch`。PC 客户端白名单见 [gateway-bundle/config/CLIENT_ID_SETUP.md](gateway-bundle/config/CLIENT_ID_SETUP.md)。

**`standard` profile（默认）包含：**

| 类型 | 内容 | 说明 |
|------|------|------|
| 核心 skill | `littlehelper-modal` | MODAL 白板协议（**必装**） |
| 参考 skill | `gallery-display`、`file-viewer-display`、`file-manager`、`notepad`、`weather-display` | Agent 指令（`SKILL.md`）；配合下方 workspace 脚本使用 |
| Sidecar | `upload_server.py`（`:18889`） | 上传 / 下载 / 画廊长按保存 |
| Workspace 脚本 | `generate_gallery.py`、`file_viewer.py`、`file_reader.py`、`file_manager.py`、`notepad.py`、`weather_card.py` | 装到 `~/.openclaw/workspace/scripts/` |
| Agent 规则 | `AGENTS.md` 注入 | install 幂等追加龙爪客户端路由（`openclaw-android` → `===CHAT===` + `===MODAL===`） |

`minimal` profile 仅含 `littlehelper-modal`（无 sidecar、无参考 skill、**不含地图 canvas**）。详见 [gateway-bundle/README.md](gateway-bundle/README.md) 与 [GATEWAY_COMPANION_BUNDLE_PLAN.md](docs/GATEWAY_COMPANION_BUNDLE_PLAN.md)。

未跑 bundle 时，至少需手动安装 [littlehelper-modal](scripts/skills/littlehelper-modal/SKILL.md) 才能正常弹出白板。

---

## 获取与编译

### 从 Release 安装

使用 GitHub Release 中的 **`app-release.apk`**（或 `app-release-unsigned.apk` 自行签名）与 **`loongclaw-gateway-bundle-2.1.3.zip`**。  
**真机开发验收**可用 Debug 包：`app/build/outputs/apk/debug/app-debug.apk`。

**维护者打 Release 包（v2.1.3）**

```powershell
python scripts/build_gateway_bundle.py
.\gradlew assembleRelease
# APK: app\build\outputs\apk\release\app-release-unsigned.apk（需 keystore 签名后对外分发）
# Zip: dist\loongclaw-gateway-bundle-2.1.3.zip
```

在 GitHub 创建 **v2.1.3** Release，将上述 APK 与 zip 作为附件上传；Release 说明可摘录 [CHANGELOG.md](CHANGELOG.md) 中 2.1.3 条目。发版前建议对照 [bundle 回归清单](docs/BUNDLE_2.1.1_REMEDIATION.md#六下一版-bundle-回归清单15-分钟) 与 [2.1.3 回归要点](docs/SYSTEM_CANVAS_PLAN.md#213-回归要点发布前) 做最后一次真机确认。

> Release 包需正式签名后才能对外分发；本地 `assembleRelease` 若未配置 keystore，输出为 **unsigned**（可与 debug 签名二选一用于内测）。详见 [docs/DEVELOPER.md](docs/DEVELOPER.md)。

### 开发者编译

```bat
git clone <本仓库>
cd LittleHelper
copy local.properties.example local.properties
:: 编辑 local.properties：至少填写 sdk.dir
gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

- **Gateway 地址、Token、密码** 仅保存在 App 内（`EncryptedSharedPreferences`），**不会**写入 APK 或 `local.properties`。
- 未保存设置前 **不会** 自动连接 Gateway。
- 单元测试：`gradlew.bat test`

---

## 连接与设置

### 设置页包含什么

| 区块 | 内容 |
|------|------|
| **Gateway 连接** | 地址、端口、Token/密码、测试握手、保存并连接 |
| **界面语言** | 中文 / English |
| **声音** | Gateway 语音播报（TTS）开关 |

**已移除（v2.1 不再提供）**

- ~~助手语气~~（人设由 Gateway Agent / skill 决定）
- ~~智能体名称~~（产品固定 `main`；进阶见下文）

### 配置存储

| 行为 | 说明 |
|------|------|
| 存储位置 | 本机加密 SharedPreferences |
| 首次安装 | 自动打开设置；不后台连 Gateway |
| 真正连上 | 点 **保存并连接** 后启动 WebSocket + 前台 Service |
| 测试握手 | 独立短连接，仅设置页验证；显示四步进度 |

### 连接保活与通知

- WebSocket 长连接（空闲仅心跳，**不消耗大模型 Token**）
- 低优先级通知：「正在连接 Gateway…」→「已连接 Gateway」
- 主界面 **绿灯** = 在线；**橙灯闪烁** = 连接中
- 国产 ROM 建议：**省电 → 无限制**

### 设置项 ↔ Gateway 配置

| 设置项 | 对应 Gateway |
|--------|--------------|
| 协议 | WebSocket（固定） |
| 认证 | `gateway.auth.mode` / token / password |
| 地址 / 端口 | 可达的 bind 地址与 `gateway.port`（默认 18789） |

### 测试握手 · 四步进度

```
═══════ 设备握手进度 ═══════
① Token 验证
② 设备配对审批
③ 批准检测
④ 建立连接
```

- **Token 错误**：停在 ①
- **待配对**：① 过，② 等待；可展开 **设备 ID** 到 Control UI → **Devices** 对照批准
- **限流**：④ 失败，稍等 1–2 分钟再试

主界面连接失败横幅与测试握手 **共用** 错误映射（**中文**文案）。

### App 内置（不在设置里改）

| 项 | 值 |
|----|-----|
| `client.id` | `openclaw-android` |
| `client.mode` | `ui` |
| `role` | `operator` |
| sessionKey | **`agent:main:main`** |
| Canvas / 上传 HTTP | `http://{host}:18789` / `:18889` |
| 下载目录 | **`Download/LoongClaw/`** |

握手设计：[openclaw_client_handshake_guide.md](docs/openclaw_client_handshake_guide.md)（扫码章节为历史草案）。

---

## Gateway 会话与协议

保存并连接后，App 自动执行：

```
WebSocket connect（hello-ok）
  → sessions.messages.subscribe
  → chat.send（用户消息）
  → 接收 chat.delta、session.message
```

> 必须 **subscribe**，否则发得出消息但收不到回复。

助手若要打开白板，Gateway 须按双线协议回复（**勿**加 `===END===`）：

```
===CHAT===
一句话摘要

===MODAL===
{"action":"open","blocks":[...]}
```

完整契约：[docs/OPENCLAW_GATEWAY_CONTRACT.md](docs/OPENCLAW_GATEWAY_CONTRACT.md)

---

## 进阶：多 Agent（代码级，默认未启用）

面向自托管开发者；**不是**当前产品主推场景。

| 模块 | 路径 |
|------|------|
| 产品策略 | `app/.../settings/AgentSessionPolicy.kt`（`PRODUCT_AGENT_NAME = "main"`） |
| sessionKey | `app/.../settings/SessionKeyResolver.kt` → `agent:{name}:main` |
| 设置 UI（已隐藏） | 可恢复 `GatewaySettingsSheet` 中智能体名称输入框 |
| Gateway 搭建 | [docs/multi_agent_gateway_setup.md](docs/multi_agent_gateway_setup.md) |

> **⚠️ `agents.list` 陷阱**：若在 `openclaw.json` 里**新增**家庭成员 agent，必须把 **`main` 也显式写在 list 里**（见搭建文档示例）。只写新 id、漏写 `main` 时，Gateway 重启后默认主 agent 可能被覆盖，龙爪与 Control UI 主会话会异常。龙爪 App 默认固定连 `agent:main:main`。

---

## 架构概览

```
┌─────────────┐   WebSocket    ┌──────────────────┐
│   龙爪 App   │ ◄──────────► │ OpenClaw Gateway │
│ 聊天+白板+文件│ chat.send +  │ Agent+Canvas+上传 │
│              │  subscribe   │                  │
└─────────────┘              └──────────────────┘
```

- **进程级连接**：`GatewayConnectionManager` + `GatewayConnectionService`（前台保活）
- **Shell**：`SessionReducer` 归约事件；`MessageBlockParser` 解析 MODAL
- **传输**：`WebSocketOpenClawSessionClient`、device-auth v3

开发者细节：[docs/DEVELOPER.md](docs/DEVELOPER.md)

---

## 项目结构

```
LittleHelper/
├── app/                         # Android（Kotlin + Compose）
│   └── src/main/res/
│       ├── values/strings.xml   # 中文（默认）
│       └── values-en/           # 部分界面英文
├── gateway-bundle/              # Gateway 配套 install/doctor 源
├── dist/                        # build_gateway_bundle.py 输出 zip
├── docs/                        # 契约、握手、Bundle 计划等
├── scripts/                     # skill 源、打包与联调脚本
├── README.md                    # 英文说明（GitHub 默认首页）
├── README.zh.md                 # 本文件（中文用户指南）
└── CHANGELOG.md
```

---

## 常见问题

**连不上 / 一直「连接中」**  
查地址、Tailscale/局域网、Gateway 是否启动、防火墙。用 **测试握手** 看卡在哪一步。

**发消息无回复**  
须已 **保存并连接** 且 subscribe 成功。重启 App 或重测握手。确认 Gateway 与 Token 正常。

**白板不显示**  
Gateway 回复须含 `===MODAL===` 合法 JSON；不要 `===END===`。确认已装 `littlehelper-modal` skill。

**切换 English 后部分仍是中文**  
见 [§界面语言](#界面语言v213)；连接横幅与主聊天壳暂未英文化。

**通知栏一直「Gateway 连接」**  
保存并连接后的正常行为（前台 Service）。不需要时可 Force Stop 或限制后台。

**息屏后掉线**  
省电设为 **无限制**；回前台会自动重连。

**提示待配对**  
Control UI → **Devices** → 批准；对照设置页或横幅中的 **设备 ID**。

**Token 错 vs 待配对**  
Token 错只失败在 ①；勿在 Token 错误时反复改配对。

**下载文件在哪**  
`Download/LoongClaw/`；App 内 **我的文件** 可浏览删除。

**聊天历史丢了**  
存在 App 私有目录；清数据或卸载会丢失。

**修改 Gateway 地址后上传仍走旧 IP**  
设置里重新 **保存并连接**。

更多排错：[docs/DEVELOPER.md](docs/DEVELOPER.md) · 版本历史：[CHANGELOG.md](CHANGELOG.md)

---

## 相关文档

| 文档 | 用途 |
|------|------|
| [OPENCLAW_GATEWAY_CONTRACT.md](docs/OPENCLAW_GATEWAY_CONTRACT.md) | App ↔ Gateway 契约 |
| [GATEWAY_COMPANION_BUNDLE_PLAN.md](docs/GATEWAY_COMPANION_BUNDLE_PLAN.md) | 配套包分发计划 |
| [SYSTEM_CANVAS_PLAN.md](docs/SYSTEM_CANVAS_PLAN.md) | 2.2 系统白板（文件库/相册 Tab）规划 |
| [gateway-bundle/README.md](gateway-bundle/README.md) | install / doctor 用户指南 |
| [BUNDLE_2.1.1_REMEDIATION.md](docs/BUNDLE_2.1.1_REMEDIATION.md) | Bundle 整改记录与 **发版回归清单** |
| [DEVELOPER.md](docs/DEVELOPER.md) | 贡献者与联调 |

---

## 联系

反馈、问题与合作：**[laomianhua@agent.qq.com](mailto:laomianhua@agent.qq.com)**

---

## 许可证

本项目采用 **[MIT License](LICENSE)** 开源。

- 你可以自由使用、修改、再分发本仓库代码（包括商用），但须保留版权声明与许可全文。
- **LoongClaw（龙爪）** 为独立社区客户端，与 [OpenClaw](https://github.com/openclaw/openclaw) 官方无隶属关系；OpenClaw 及相关名称归其各自权利人所有。

### 免责声明

- 本软件按 **「现状」（AS IS）** 提供，**不提供**任何明示或默示担保（包括适销性、特定用途适用性、与 Gateway 的持续兼容性等）。
- 龙爪连接的是你或第三方自托管的 **OpenClaw Gateway**；Gateway 地址、Token、API Key、对话与文件内容由**用户自行配置与承担**，开发者不对 Gateway 侧行为或第三方服务负责。
- 在适用法律允许的最大范围内，作者对因使用或无法使用本软件而产生的任何直接、间接或附带损失**不承担责任**。
- 贡献者与用户请勿将 API Key、Gateway Token 等敏感信息提交到公开仓库。
