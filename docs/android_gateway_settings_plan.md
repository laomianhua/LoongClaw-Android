# Android Gateway 设置模块 — 实施方案

> **状态**：**v1 已落地**（2026-07-01 真机验收）；**v2 多 Agent 已落地**（2026-07-04：智能体名称 + `SessionKeyResolver`）  
> **日期**：2026-07-01（文档对齐：2026-07-04）  
> **v2 变更摘要**：废弃 `AppSessionStore` UUID；设置页新增 **智能体名称**；连接顺序 hello-ok → subscribe → chat.send。详见 [`MULTI_AGENT_DEVELOPMENT_PLAN.md`](MULTI_AGENT_DEVELOPMENT_PLAN.md)。  
> **对齐依据**  
> - [`openclaw_client_handshake_guide.md`](./openclaw_client_handshake_guide.md)（Gateway Agent 设计稿）  
> - [`d:\Dev\LoongClaw\README.md`](../LoongClaw/README.md) + `AppSettings.cs` / `SettingsViewModel.cs`（PC 已落地）  
> - LittleHelper 当前握手基线（2026-06-30：`ui` + v3 + operator，真机已验）

---

## 1. 目标

| 目标 | 说明 |
|------|------|
| **开源可用** | 用户安装 APK 后可在 App 内配置 Gateway，不再依赖改 `local.properties` 重编译 |
| **与龙爪 PC 一致** | 设置字段、认证语义、connect.auth 行为与 LoongClaw 对齐 |
| **保留开发体验** | `local.properties` 仅用于 `sdk.dir`；Gateway 在 App 内配置 |
| **UI 收尾** | 聊天头栏右侧 **音量 → ⚙️ 设置**；TTS、连接、语气等收入设置 Sheet |

---

## 2. 设计稿 vs 现状 — 我们实际采用什么

Gateway 设计稿与**已验通代码**有几处不一致，Android **以 LoongClaw + LittleHelper 真机基线为准**，并在文档里标注差异。

| 项 | 设计稿 `openclaw_client_handshake_guide.md` | LoongClaw PC / LittleHelper 真机 | **Android 采用** |
|----|---------------------------------------------|-----------------------------------|------------------|
| `client.mode` | `mobile` | `ui` | **`ui`** |
| 发话 RPC | `chat.send` | Android：`sessions.send`；PC：`chat.send` | **不变**（各端已有实现） |
| 手动 Token 模式 | 文档写「无设备配对」 | 实际仍走 **Ed25519 + challenge + 可选 Approve** | **与 PC 一致**：始终 device-auth v3；共享 token 作首次 auth，配对后 `deviceToken` |
| connect.auth | 扫码用 `bootstrapToken` | PC：Token/Password **二选一**，不混发 | **对齐 PC**（见 §4.3） |
| 扫码 | `openclaw qr` → setupCode | **PC/Android 均未实现** | **分阶段**：v1 手动；v1.1 扫码（依赖 Gateway QR 格式） |

---

## 3. 功能范围

### 3.1 v1（开源阻塞项 — 已完成）

- [x] **GatewaySettingsStore**：运行时持久化 host / port / authMode / 加密凭据（`EncryptedSharedPreferences`）
- [x] **GatewayConfig 统一解析**：仅 `GatewaySettingsStore`；未配置时不连接
- [x] **connect.auth 按 authMode 拆分**（Token / Password 二选一，不混发）
- [x] **保存后重连**：断开 WS → 用新 config 走完整 v3 握手
- [x] **设置页 UI**（全屏 `Scaffold` + 顶栏返回 + 分组卡片，见 §5）
- [x] **头栏 ⚙️** 入口；TTS 开关迁入设置页
- [x] **首次无配置**：打开 App 自动弹出设置
- [x] **测试握手**：独立短连接 WebSocket `connect` → `hello-ok`（非 HTTP `/health`）
- [x] **全链路 host 更新**：上传 `:18889`、Canvas URL、下载解析等读同一份 config
- [x] **AppSessionStore**：`agent:main:<UUID>`，禁止 `agent:main:main`；`sessions.create` 引导

### 3.2 v1.1（扫码 — Gateway 就绪后）

- [ ] 解析 `openclaw qr` 输出的 setupCode（`gatewayUrl` + `bootstrapToken`）
- [ ] connect 首包 `auth: { bootstrapToken }` → challenge → 签名 → 获得 **设备专属 token**
- [ ] 设置页「扫描二维码」为主入口（与设计稿一致）

> **前置条件**：Gateway 侧 `openclaw qr` 输出格式稳定；建议在 LittleHelper 仓库增加 `docs/qr_setup_code_schema.md` 样例后再实现。

### 3.3 v1 刻意不做（与 LoongClaw 精简策略一致）

| 不做 | 原因 |
|------|------|
| Session Key 输入框 | **不提供**；App 内部生成 `agent:main:<UUID>`，**禁止**连 `agent:main:main`（见 §4.4） |
| Canvas 路径 / 18889 端口 UI | 内置：`httpBaseUrl()` + 端口 `18889` |
| 协议切换 | 固定 WebSocket，灰色只读展示 |
| 改 `client.id` / 包名 / `__LITTLEHELPER_*` | 品牌与协议层，非设置范围 |
| 自动重连间隔 UI | 沿用现有 `MainViewModel` 20s grace / 2s 间隔 |

### 3.4 设置内已有能力（搬家即可）

| 能力 | 现有代码 |
|------|----------|
| Gateway TTS 开关 | `GatewayTtsStore` |
| 助手语气 | `AssistantToneStore`（已迁入 `GatewaySettingsSheet`） |

---

## 4. 数据模型

### 4.1 与 LoongClaw 对齐的字段

对齐 `LoongClaw.Shared.Configuration.GatewaySettings`：

```kotlin
enum class GatewayAuthMode { TOKEN, PASSWORD, NONE }

data class GatewayConnectionSettings(
    val host: String = "",
    val port: Int = 18789,
    val authMode: GatewayAuthMode = GatewayAuthMode.TOKEN,
    // 持久化时为加密串，内存中解密使用
    val tokenEncrypted: String = "",
    val passwordEncrypted: String = "",
)

// 内置默认，不暴露 UI（见 §4.5 Session Key）
const val DEFAULT_UPLOAD_PORT = 18889
const val SESSION_KEY_PREFIX = "agent:main:"
```

**JSON 存储**：`EncryptedSharedPreferences`（`littlehelper_gateway_settings`），字段 host / port / authMode / token / password。

`authMode`：`0` = Token · `1` = Password · `2` = None（与 PC 枚举值一致，便于将来共享文档/QR payload）。

### 4.2 凭据加密（Android）

| PC | Android v1 |
|----|------------|
| Windows DPAPI | **EncryptedSharedPreferences**（AndroidX Security）或 Keystore 加密后 Base64 存 JSON |

明文仅在设置 Sheet 编辑期间驻内存；保存时加密。

### 4.3 connect.auth 构建规则（必须改）

**当前 LittleHelper（已修复）**：`OpenClawConnectHandshake.buildConnectAuth` 按 authMode **二选一**写入 `auth.token` 或 `auth.password`。

| authMode | connect 帧 |
|----------|------------|
| TOKEN | `{ "token": "<credential>" }` |
| PASSWORD | `{ "password": "<credential>" }` |
| NONE | `{}` 或省略 auth |

**credential 解析顺序**（与现网一致）：

1. 已配对 → `OpenClawDeviceIdentityStore` 中的 **deviceToken**
2. 否则 → 用户设置的 token 或 password（按 authMode）

### 4.4 Session Key（App 内部 UUID，不进设置）

**产品决策（2026-07-01）**：Android **不允许**连接 Gateway 的 **main session**（`agent:main:main`，与 Control UI 共用）。Session Key **不出现在设置页**，由 App 在代码里管理。

| 规则 | 说明 |
|------|------|
| 格式 | `agent:main:` + **UUID**（无连字符或小写均可，与 Gateway 约定一致即可） |
| 首次启动 | 生成 UUID，写入 `AppSessionStore`（如 `filesDir/app_session.json`） |
| 冷启动 | 读 persisted key，**复用同一会话**（聊天历史与 Gateway 侧 context 对齐） |
| 禁止值 | 不得使用 `agent:main:main`；开发兜底 `local.properties` 亦不建议覆盖为 main |
| 用户可见 | 设置页仅展示「当前会话」摘要（可选：会话 ID 前 8 位），**不可编辑** |
| 「新对话」 | **Release 前 v1 可不做**；若做则旋转 UUID + 清空本地聊天（另开 Batch） |

```kotlin
object AppSessionStore {
    fun loadOrCreateSessionKey(): String  // "agent:main:" + uuid
    fun rotateSessionKey(): String        // 将来「新对话」用
}
```

`GatewayConfig.mainSessionKey` 来自 `AppSessionStore`，**不**来自 `GatewaySettingsStore` / 设置 UI。

**与 Gateway / Skill 的影响**：

- Agent 侧应通过 **`client.id = openclaw-android`** 识别手机客户端，**不要**假设 session 一定是 `agent:main:main`
- 需更新 `scripts/skills/littlehelper-modal/SKILL.md` 与 `docs/OPENCLAW_GATEWAY_CONTRACT.md`：由「固定 main」改为「Android 独立 UUID session + **client.id 路由**」（**已完成 2026-07-01**）

**实施批次**：Batch 2 已合并；`WebSocketOpenClawSessionClient` 在自定义 key 上执行 `sessions.create` → `sessions.messages.subscribe` → `sessions.send`。

### 4.5 GatewayConfig 解析链

```kotlin
object GatewayConfigProvider {
    fun resolve(context: Context): GatewayConfig {
        val saved = GatewaySettingsStore(context).load()
        if (saved.isConfigured) {
            return saved.toGatewayConfig(context)
        }
        return GatewayConfig.fromBuildConfig() // local.properties 兜底
    }
}
```

`GatewayConfig` 扩展：

```kotlin
data class GatewayConfig(
    ...
    val authMode: GatewayAuthMode = GatewayAuthMode.TOKEN,
)
```

所有 `GatewayConfig.fromBuildConfig()` 调用点改为 `GatewayConfigProvider.resolve(context)`（或通过 `MainViewModel` / 单例注入，避免 Context 散落）。

---

## 5. UI 设计

### 5.1 头栏

```
[● 连接/重试] [📁 我的文件]     标题     [⚙️ 设置]
```

- 移除右侧 **音量** 图标
- 连接灯逻辑不变（点击离线灯仍可重试）

### 5.2 设置页（全屏 `Scaffold` + `CenterAlignedTopAppBar`）

```
┌──────────────────────────────────┐
│  ←        设置                   │  ← TopAppBar；手势返回 / BackHandler
├──────────────────────────────────┤
│  ┌─ Gateway 连接（卡片）────────┐ │
│  │ [扫描二维码（即将支持）]     │ │
│  │ 协议 WebSocket（只读）       │ │
│  │ 认证 / 地址 / 端口 / Token   │ │
│  │ [测试握手] [保存并连接]      │ │
│  │ 会话 abc12… · 配对引导文案   │ │
│  └─────────────────────────────┘ │
│  ┌─ 声音（卡片）───────────────┐ │
│  │ Gateway 语音播报 [开关]      │ │
│  └─────────────────────────────┘ │
│  ┌─ 助手语气（卡片）───────────┐ │
│  │ RadioButton 列表             │ │
│  └─────────────────────────────┘ │
└──────────────────────────────────┘
```

**交互**

| 操作 | 行为 |
|------|------|
| 保存并连接 | 校验 host/port → 加密存盘 → 重连 Gateway；静默关闭设置页 |
| 测试握手 | 独立 WS 短连接：`connect.challenge` → v3 签名 → `hello-ok`（15s 超时） |
| 返回（箭头 / 系统手势） | `BackHandler`；未改表单则直接关闭；已改则 `AlertDialog` 提示保存或放弃 |
| 切换 authMode | 显示/隐藏 Token 或 Password 字段 |
| 改 TTS / 语气 | 立即写 Store；语气仍走现有 `syncAssistantToneToGateway` |

### 5.3 首次启动

```
if (!GatewaySettingsStore.isConfigured && BuildConfig 也为空)
    → 自动弹出设置 Sheet，连接区获得焦点
else
    → 正常启动，后台 connect
```

---

## 6. 架构改动清单

### 6.1 新文件

| 文件 | 职责 |
|------|------|
| `settings/GatewayAuthMode.kt` | 枚举 |
| `settings/GatewayConnectionSettings.kt` | 数据类 + `isConfigured` |
| `settings/GatewaySettingsStore.kt` | 读写 JSON + 加密凭据 |
| `settings/AppSessionStore.kt` | 生成/持久化 `agent:main:<UUID>` |
| `shell/transport/GatewayConfigProvider.kt` | 解析链 |
| `ui/settings/GatewaySettingsSheet.kt` | 设置 UI |
| `test/.../GatewaySettingsStoreTest.kt` | 序列化 / authMode |
| `test/.../OpenClawConnectHandshakeAuthTest.kt` | auth 二选一 |

### 6.2 修改文件（核心）

| 文件 | 改动 |
|------|------|
| `OpenClawConnectHandshake.kt` | `BuildConnectAuth(authMode)`；按 mode 写 auth |
| `OpenClawDeviceAuth.kt` | `resolveAuthToken` 传入 authMode |
| `OpenClawSessionClientFactory.kt` | 改为接收 `GatewayConfig` 或 Context+Provider |
| `WebSocketOpenClawSessionClient.kt` | 支持 `updateConfig` + 重连；或工厂重建 client |
| `MainViewModel.kt` | 持有 `GatewayConfig`；`reconnectGateway()`；设置回调 |
| `MainScreen.kt` / `ChatFlowSection.kt` | ⚙️ 入口；去掉头栏 TTS |
| `FileUploadManager.kt` | 构造时注入 host（或每次 upload 读 Provider） |
| `StoredImageDownloadUrlResolver.kt` | 同上 |
| `ModalCanvasShell.kt` / `SessionReducer.kt` / … | `fromBuildConfig()` → Provider |

### 6.3 WebSocket 客户端生命周期

**推荐方案 A（改动较小）**

- `MainViewModel` 保存 `gatewayConfig` StateFlow
- 设置变更 → `sessionController.disconnect()` → 更新 config → `sessionController.connect()` 
- `OpenClawSessionClientFactory.create` 在 ViewModel 初始化时调用一次；config 变更时 **重建 client**（或给 `WebSocketOpenClawSessionClient` 加 `reconnectWith(config)`）

**方案 B**：Client 构造时注入 `() -> GatewayConfig` 懒读取 — 仅适合 host 不变场景，**不推荐**。

---

## 7. 实施顺序（建议 3 个 PR / commit 批次）

### Batch 1 — 数据层 + auth 修复（可单测）

1. `GatewaySettingsStore` + 加密
2. `GatewayConfigProvider`
3. `OpenClawConnectHandshake` authMode 拆分 + 单测
4. LoongClaw 对照测试：Token 模式帧内**无** password 字段

### Batch 2 — 注入 + 重连 + Session Key

1. `AppSessionStore`：`agent:main:<UUID>`，禁止 `agent:main:main`
2. 替换所有 `fromBuildConfig()`；`mainSessionKey` 走 `AppSessionStore`
3. `MainViewModel.reconnectGateway()`
4. `FileUploadManager` / 下载 / Canvas 跟随新 host

### Batch 3 — UI

1. `GatewaySettingsSheet` + ⚙️ 入口
2. TTS / 语气迁入
3. 首次无配置引导
4. strings / README 更新（设置说明取代 local.properties 为主路径）

---

## 8. 验收标准

| # | 场景 | 预期 |
|---|------|------|
| 1 | 全新安装，无 local.properties | 弹出设置；填 Tailscale IP + token → 保存 → 连接成功 |
| 2 | authMode=Token，Gateway auth.mode=token | connect 帧**仅**含 `auth.token` |
| 3 | authMode=Password | connect 帧**仅**含 `auth.password` |
| 4 | 修改 host 后保存 | 上传/Canvas/下载 URL 指向新 host |
| 5 | 配对 pending | 设置页/ Banner 显示 deviceId + Control UI 提示 |
| 6 | 测试握手 | WS `hello-ok` 成功文案；失败展示配对/鉴权错误 |
| 7 | 开发者 local.properties | 未填 App 内设置时仍自动连接（向后兼容） |
| 8 | 手势返回 | 不闪退；未保存改动有确认框 |

---

## 9. 扫码 v1.1 预留（接口 sketch）

```kotlin
data class QrSetupPayload(
    val gatewayUrl: String,      // ws://100.x.x.x:18789 或 host+port 分开
    val bootstrapToken: String,  // 一次性
    val version: Int = 1,
)

fun parseSetupCode(raw: String): QrSetupPayload?
```

解析成功后：预填 host/port，`authMode` 临时为 bootstrap 流程；connect 成功后清除 bootstrapToken，持久化 deviceToken。

---

## 10. 文档同步（v1 已完成）

- [x] 更新 `README.md` § 连接配置：主路径为 App 内设置
- [x] 更新 `OPENCLAW_GATEWAY_CONTRACT.md` §1：Android `agent:main:<UUID>` + `client.id` 路由
- [x] 更新 `scripts/skills/littlehelper-modal/SKILL.md` 会话说明
- [x] `local.properties.example` 注明「可选开发者默认，开源用户用 App 内设置」
- [ ] `openclaw_client_handshake_guide.md`：扫码 v1.1 章节与 `client.mode=ui` 全文对齐（手动模式 device-auth 已对齐）

---

## 11. 已确认决策（归档）

1. **扫码入口**：v1 显示灰色「扫描二维码（即将支持）」占位按钮。  
2. **Session Key**：设置中**不提供**；代码内 `agent:main:<UUID>`，**禁止** `agent:main:main`（§4.4）。  
3. **设置载体**：v1 初版为 `ModalBottomSheet`；**2026-07-01 改为全屏 Scaffold**（修复全面屏手势返回闪退）。  
4. **保存前是否强制测试**：**不强制**；允许保存后由主连接自行握手。  
5. **测试连接**：已升级为 **WebSocket 完整握手**，非 HTTP `/health`。
