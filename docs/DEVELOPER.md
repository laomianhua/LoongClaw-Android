# 开发者文档

面向贡献者与 Gateway 联调。普通用户请读 [README.zh.md](../README.zh.md)（中文）或 [README.md](../README.md)（英文）。

## 文档索引

| 文档 | 内容 |
|------|------|
| [OPENCLAW_GATEWAY_CONTRACT.md](OPENCLAW_GATEWAY_CONTRACT.md) | App ↔ Gateway 集成契约（MODAL、Canvas、会话、故障对照） |
| [multi_agent_gateway_setup.md](multi_agent_gateway_setup.md) | 家庭 / 多 Agent Gateway 搭建（shared-skills、agents.list） |
| [MULTI_AGENT_DEVELOPMENT_PLAN.md](MULTI_AGENT_DEVELOPMENT_PLAN.md) | 多 Agent 功能规划与实施记录（已落地） |
| [openclaw_client_handshake_guide.md](openclaw_client_handshake_guide.md) | 客户端握手与设置设计稿 |
| [android_gateway_settings_plan.md](android_gateway_settings_plan.md) | Android Gateway 设置模块实施方案（v1 已落地） |
| [GATEWAY_COMPANION_BUNDLE_PLAN.md](GATEWAY_COMPANION_BUNDLE_PLAN.md) | Gateway Companion Bundle 分发计划（阶段 B：见 `gateway-bundle/`） |
| [CHANGELOG.md](../CHANGELOG.md) | 版本与真机验收历史 |

Gateway skill 镜像：`scripts/skills/littlehelper-modal/SKILL.md`  
推送：`python scripts/publish_littlehelper_gateway_skill.py`

---

## 架构概览

LittleHelper 是 **OpenClaw Gateway 哑终端壳**：

```
用户 → App（Compose UI + WebSocket）
         ↕ chat.send + sessions.messages.subscribe → chat.delta / session.message
      Gateway（意图、MODAL 生成、Canvas HTTP :18789、上传 :18889）
```

- **进程级连接**（`gateway/`、`LittleHelperApplication`）：`GatewayConnectionManager` 持有 WS 与静默重连；`GatewayConnectionService` 前台保活 + 通知
- **Shell 层**（`shell/`）：`SessionReducer` 归约 Gateway 事件；`MessageBlockParser` 解析 `===CHAT===` / `===MODAL===`
- **传输**（`shell/transport/`）：`WebSocketOpenClawSessionClient`、device-auth v3、`SessionKeyResolver` → `GatewayConfig.mainSessionKey`
- **白板**（`shell/modal/`、`shell/modules/`）：MRU 标签 + 单 WebView；table/md/chart 叠加
- **设置**（`settings/`、`ui/settings/`）：`GatewaySettingsStore`、`GatewaySettingsSheet`（含智能体名称）

配置解析链：**仅 App 内设置**（`GatewaySettingsStore`）；未保存前不自动连接 Gateway。

---

## MODAL wire 格式（摘要）

```
===CHAT===
（聊天气泡摘要）

===MODAL===
{"action":"open","blocks":[...]}
```

- **禁止** `===END===`（会导致解析失败）
- JSON 为 `===MODAL===` 后裸文本，勿用 markdown 代码块包裹
- 详情见契约 §2

---

## 编译与测试

```bat
cd d:\Dev\LittleHelper
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest
```

APK：`app/build/outputs/apk/debug/app-debug.apk`

`local.properties`：仅需 `sdk.dir`（见 [local.properties.example](../local.properties.example)）。Gateway 在 App 内设置保存，不通过本文件注入。

---

## 项目结构（精简）

```
app/src/main/java/com/littlehelper/
├── LittleHelperApplication.kt
├── MainActivity.kt
├── viewmodel/MainViewModel.kt
├── gateway/               # GatewayConnectionManager, GatewayConnectionService
├── settings/              # GatewaySettingsStore, SessionKeyResolver
├── shell/
│   ├── transport/         # WebSocket, 握手, GatewayConfigProvider
│   ├── session/           # SessionController, SessionReducer
│   ├── modal/             # ModalSlotReducer, MRU 标签
│   └── modules/           # ModalCanvasShell, WebView 渲染器
└── ui/
    ├── MainScreen.kt
    └── settings/GatewaySettingsSheet.kt
```

---

## 联调与脚本

| 脚本 | 用途 |
|------|------|
| `scripts/openclaw_gateway_probe.py` | CLI 握手探针（session 可自定；App 默认 `agent:main:main`） |
| `scripts/openclaw_dev_session.py` | Cursor 开发协调（`agent:main:cursor-dev`） |
| `scripts/upload_server.py` | 本地上传服务 :18889 |
| `scripts/publish_littlehelper_gateway_skill.py` | 推送 MODAL skill 到 Gateway |
| `scripts/build_gateway_bundle.py` | 组装 `dist/loongclaw-gateway-bundle-<ver>.zip` |
| `scripts/harvest_gateway_export.ps1` | 从 `~/.openclaw` 提取资源到 `gateway-export/` |

---

## 已知限制（开发向）

1. 聊天 JSON 持久化；卸载丢失
2. 后台保活依赖前台 Service + 系统省电策略；极端 ROM 仍可能杀进程，回前台自动重连（建议用户省电「无限制」）
3. WebView 仅支持 URL，不支持 inline HTML
4. 白板标签冷启动不恢复
5. 扫码配对、`wss://`：**当前版本不做**（见 [README.zh.md](../README.zh.md)）

完整列表见旧版 README 迁移前的「已知限制」；用户向 FAQ 见 [README.zh.md § 常见问题](../README.zh.md#常见问题)。
