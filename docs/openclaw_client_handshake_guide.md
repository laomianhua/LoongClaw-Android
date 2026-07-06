# OpenClaw 客户端认证握手与安装交付逻辑说明

> 适用客户端：LoongClaw (Windows PC) / LittleHelper (Android)
> 目标读者：客户端开发者 + Gateway 管理员
> 版本：2026-07-01

---

## 一、交付安装包构成

```
loongclaw-v1.0.0.zip
├── LoongClaw.apk                     # 安卓客户端安装包
├── LoongClaw-Setup.exe               # Windows 桌面客户端（WPF）
├── skills/                           # Gateway 端 Skill 包
│   ├── littlehelper-modal/           # MODAL/Chat 协议规范（Agent 侧）
│   │   └── SKILL.md
│   ├── gallery-display/              # 多图画廊展示
│   │   ├── SKILL.md
│   │   └── scripts/generate_gallery.py
│   ├── weather-display/              # 天气卡片
│   │   ├── SKILL.md
│   │   └── scripts/weather_card.py
│   ├── file-viewer-display/          # 单文件/PDF 查看器
│   │   └── SKILL.md
│   └── fund-realtime-display/        # 基金估值（可选）
│       ├── SKILL.md
│       └── scripts/fund_realtime.py
├── install-skills.ps1                # Windows 一键安装脚本
├── install-skills.sh                 # macOS/Linux 安装脚本
└── README.md                         # 安装说明（含两种部署场景）
```

### install-skills.ps1 逻辑

```powershell
# 1. 复制 skill 目录到 Gateway workspace
Copy-Item -Recurse .\skills\* "$env:USERPROFILE\.openclaw\workspace\skills\" -Force

# 2. 复制脚本到 workspace scripts
Get-ChildItem -Recurse .\skills\*\scripts\*.py | ForEach-Object {
    Copy-Item $_.FullName "$env:USERPROFILE\.openclaw\workspace\scripts\" -Force
}

Write-Host "Skills installed. Restart Gateway to apply."
```

---

## 二、认证握手方式

### 方式一：二维码扫描（推荐）

```
Gateway 主机                       手机 App
──────────                        ────────
openclaw qr
  → 生成 setupCode                 扫描二维码
    (含 gatewayUrl                   → 解析得到 gatewayUrl + bootstrapToken
     + bootstrapToken)               → WebSocket connect
                                     → auth: { bootstrapToken }
                                     → Gateway 验证 → 下发 challenge nonce
                                     → App Ed25519 签名 → 返回 signature
                                     → Gateway 签发设备专属 token
                                     → 后续重连用设备 token
```

**关键点：**
- `bootstrapToken` 是一次性临时凭证，用过即废
- 二维码里不包含 `gateway.auth.token`（永久共享密钥），扫码用户拿不到它
- 设备配对后获得专属 token，可在 Gateway 侧单独吊销

### 方式二：手动输入（降级方案）

```
用户操作                          手机 App
───────                          ────────
设置页手动填入：                   WebSocket connect
  地址: 192.168.1.55               → device-auth v3（challenge + Ed25519 签名）
  端口: 18789                      → auth: { token } 或 { password }（二选一）
  认证: Token                      → 首次可能需在 Control UI 批准设备
  Token: openclaw-xxx              → 配对后持久化 deviceToken，重连优先用设备 token
```

**说明（与真机一致）**：手动 Token/密码模式**并非**「无设备配对」；与 LoongClaw PC 相同，仍走 Ed25519 + challenge，共享凭据仅作首次 `connect.auth`。

**区别：**

| | 扫码 | 手动输入 |
|---|---|---|
| 用户操作 | 扫一下 | 填 4 个字段 |
| 安全隔离 | 设备专属 token，可单独吊销 | 共享 Gateway 凭据 + device-auth v3 配对 |
| 设备管理 | `openclaw devices list` 可见 | 无设备粒度 |
| 推荐 | 正式使用 | 开发调试 |

---

## 三、客户端设置界面设计

```
┌──────────────────────────────────┐
│  连接设置                         │
│                                  │
│  ┌────────────────────────────┐  │
│  │     📷 扫描二维码           │  │  ← 主入口
│  │     或手动输入              │  │
│  └────────────────────────────┘  │
│                                  │
│  ── 手动输入 ──                 │
│                                  │
│  协议                            │
│  ┌────────────────────────────┐  │
│  │ WebSocket (OpenClaw)       │  │  ← 灰色不可改，仅提示
│  └────────────────────────────┘  │
│                                  │
│  认证方式        [Token   ▼]    │  ← 下拉：Token / 密码 / 无
│                                  │
│  Gateway 地址   [            ]  │
│  端口           [18789       ]  │
│  Token          [●●●●●●●●●●●]  │  ← 根据认证方式切换标签
│  智能体名称      [main        ]  │  ← 与 agents.list id 一致
│                                  │
│  [测试握手]          [保存]     │
│                                  │
│  🟢 已连接  会话 main · main
└──────────────────────────────────┘
```

### 设置项与 openclaw.json 对照表

| 客户端设置项 | 用户操作 | openclaw.json 对应字段 | 说明 |
|-------------|---------|----------------------|------|
| 协议 | 灰色不可改 | — | 仅支持 WebSocket |
| 认证方式 | 下拉选择 | `gateway.auth.mode` | Token / password / none |
| 凭据（Token/密码） | 手动输入 | `gateway.auth.token` 或 `.password` | 标签随认证方式切换 |
| 地址 | 手动输入 | — | IP 或域名 |
| 端口 | 手动输入 | `gateway.port` | 默认 18789 |
| **智能体名称** | 手动输入 | `agents.list[].id` | 默认 `main`；字母/数字/下划线 |

### 不作为用户配置项的内部逻辑

| 内部字段 | 值 | 来源 | 用户是否可见 |
|---------|---|------|------------|
| `client.id` | `openclaw-android` | App 硬编码 | ❌ |
| `client.version` | 取自 BuildConfig | App 自动 | ❌ |
| `client.platform` | `android` | 系统 API | ❌ |
| `device.id` | Ed25519 公钥 SHA256 | 首次启动生成 | ❌ |
| `device.publicKey` | Ed25519 公钥 Base64 | 首次启动生成 | ❌ |
| `device.signature` | Ed25519 签名 | 对 challenge 签名 | ❌ |
| `sessionKey` | `agent:{name}:main` | 设置页智能体名称 → `SessionKeyResolver` | 摘要 `name · main` |
| `role` | `operator` | 硬编码 | ❌ |
| `scopes` | `operator.read, operator.write` | 硬编码 | ❌ |

---

## 四、两种部署场景

### 场景一：自建 Gateway（同你家模式）

```
┌──────────────────┐     Tailscale VPN        ┌───────────┐
│  用户家里 PC      │  100.x.x.x ←→ 100.y.y.y │  手机 APK  │
│  OpenClaw Gateway │                         │           │
│  + skills 包      │                         │           │
│  + Tailscale      │                         │  Tailscale │
└──────────────────┘                         └───────────┘
```

**Gateway 侧操作：**
1. 安装 OpenClaw Gateway（`npm install -g openclaw`）
2. 配置 `openclaw.json`（`bind: "lan"`, `auth.mode: "token"`）
3. 运行 `install-skills.ps1` 安装 skill 包
4. 安装 Tailscale，加入 tailnet
5. 启动 Gateway

**手机侧操作：**
1. 安装 APK + Tailscale，加入同一 tailnet
2. 打开 App → 扫码（Gateway 主机上跑 `openclaw qr`）
3. 或手动填入 Tailscale IP + 端口 + token
4. 连接成功

**Gateway 配置参考：**
```json5
{
  "gateway": {
    "mode": "local",
    "bind": "lan",
    "port": 18789,
    "auth": {
      "mode": "token",
      "token": "your-token-here"
    },
    "tailscale": {
      "mode": "off"
    }
  }
}
```

### 场景二：云端 Gateway

```
┌──────────────────────┐
│  云端 VPS             │
│  OpenClaw Gateway     │     公网 / 反向代理
│  + skills（已装）     │ ←────────────────── ┌───────────┐
│  bind=lan             │                      │  手机 APK  │
│  公网 IP / 域名       │                      │           │
└──────────────────────┘                      └───────────┘
```

**Gateway 侧操作（管理员）：**
1. VPS 上安装 OpenClaw Gateway + skill 包
2. 配置 `openclaw.json`（地址、端口、认证方式）
3. 配置反向代理（Nginx/Caddy）+ HTTPS（可选但推荐）
4. 给用户提供：地址、端口、认证方式、token

**手机侧操作（用户）：**
1. 安装 APK（不需要 Tailscale）
2. 打开 App → 设置页手动填入地址、端口、Token
3. 保存 → 连接

---

## 五、连接握手协议（WebSocket）

### connect 参数完整结构

```json
{
  "type": "req",
  "id": "conn_1",
  "method": "connect",
  "params": {
    "minProtocol": 3,
    "maxProtocol": 4,
    "client": {
      "id": "openclaw-android",
      "version": "1.0.0",
      "platform": "android",
      "mode": "mobile"
    },
    "role": "operator",
    "scopes": ["operator.read", "operator.write"],
    "auth": { "token": "***" },
    "locale": "zh-CN",
    "device": {
      "id": "<Ed25519 公钥 SHA256>",
      "publicKey": "<Ed25519 公钥 Base64>",
      "signature": "<Ed25519 签名>",
      "signedAt": 0,
      "nonce": "<challenge nonce>"
    },
    "caps": [],
    "commands": [],
    "permissions": {}
  }
}
```

### 发送消息

```json
{
  "type": "req",
  "id": "msg_<UUID>",
  "method": "chat.send",
  "params": {
    "sessionKey": "agent:main:main",
    "message": "你好",
    "idempotencyKey": "<UUID>"
  }
}
```

连接成功后须先 **`sessions.messages.subscribe`**（`params.key` = 同上 sessionKey），再发 `chat.send`，否则收不到 `session.message` / `chat.delta`。

### sessionKey 生成规则

```
设置页智能体名称（默认 main）→ SessionKeyResolver → sessionKey = "agent:" + name + ":main"
切换智能体 → 保存并连接 → 对新 key 重新 subscribe
```

用户填 **智能体名称**；完整 sessionKey 由 App 内部组装。

---

## 六、回复格式协议（Agent → 客户端）

### 三线协议（Chat + MODAL）

```
===CHAT===
（纯文本摘要，1-2 句）

===MODAL===
{"action":"open","blocks":[...]}
```

**规则：**
- MODAL JSON 裸文本输出，**不包裹代码块**
- 不加 `===END===`
- CHAT 和 MODAL 之间空行
- action 类型：`open`（新白板）、`update`（局部刷新）、`close`（关闭白板）

### MODAL block 类型

| type | 用途 | 示例 |
|------|------|------|
| `webview` | HTML 页面（最常用） | 天气卡片、画廊、地图 |
| `table` | 数据表格 | 持仓概览 |
| `chart/line` | 折线图 | 收益走势 |
| `markdown` | Markdown 渲染 | 纯文本长文 |

### webview URL 规则

- 仅用相对路径：`/__openclaw__/canvas/xxx.html`
- 禁止绝对 IP 地址
- 文件写入 `~/.openclaw/canvas/` 目录

---

## 七、客户端识别

Gateway 通过 WebSocket connect 的 `client.id` 识别客户端类型，在 Agent 侧反映为 inbound metadata：

```json
// Android App
{ "label": "openclaw-android" }

// LoongClaw PC
{ "label": "loongclaw-desktop" }

// Control UI 浏览器
{ "channel": "webchat" }  // 无 label
```

Agent 据此选择回复格式：
- `openclaw-android` → MODAL 手机紧凑版
- `loongclaw-desktop` → MODAL PC 大屏版
- 无 label → 纯文字回复

---

## 八、安装步骤 checklist（给最终用户）

### 自建 Gateway 模式

- [ ] 安装 Node.js (≥18)
- [ ] `npm install -g openclaw`
- [ ] 配置 `~/.openclaw/openclaw.json`（参考上方示例）
- [ ] 解压 skill 包 → 运行 `install-skills.ps1`
- [ ] 安装 Tailscale，加入 tailnet
- [ ] `openclaw gateway` 启动
- [ ] Gateway 主机运行 `openclaw qr`，显示二维码
- [ ] 手机安装 APK + Tailscale
- [ ] 手机打开 App → 扫码 → 连接

### 云端 Gateway 模式

- [ ] VPS 安装 Node.js + OpenClaw Gateway
- [ ] 配置 `openclaw.json`（公网 bind + auth）
- [ ] 安装 skill 包
- [ ] 配置 Nginx/Caddy 反向代理 + SSL
- [ ] 启动 Gateway
- [ ] 手机安装 APK
- [ ] 打开 App → 设置页手动填地址、端口、Token
- [ ] 保存 → 连接

---

## 九、常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| 连接被拒 | client.id 不在 Gateway 白名单 | 运行 `patch_clientid.ps1` 或手动加白名单 |
| `unauthorized` | token 错误 | 检查 `gateway.auth.token` 是否与客户端一致 |
| `pairing required` | 设备未审批 | Gateway 侧 `openclaw devices list` → `approve <id>` |
| `AUTH_TOKEN_MISMATCH` | 设备 token 与当前共享 token 不匹配 | `openclaw devices rotate --device <id> --role operator` |
| 白板不显示 | `===END===` 或代码块包裹 | 去掉 `===END===`，JSON 裸文本输出 |
| 地图瓦片空白 | 使用了 OSM 瓦片源 | 国内环境改用高德瓦片 |
| Canvas 404 | 文件写错了目录 | 写到 `~/.openclaw/canvas/`，不是 `workspace/canvas/` |
| 下载失败 | WebView 内 `<a download>` 跨域被拦截 | 走 `__LITTLEHELPER_GALLERY__` 协议，App 原生下载 |
