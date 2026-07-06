功能定位：可以这么理解

> **文档状态（2026-07-03）**  
> 已落地：`client.mode=ui` · device-auth **v3** · `role=operator` · App 内 Gateway 设置 · `agent:main:<UUID>` · `sessions.create` · WS 测试握手 · **UNAVAILABLE 握手重试** · **hello-ok.policy 心跳** · **`GatewayConnectionManager` + 前台 `GatewayConnectionService` 保活**。  
> 暂不做：扫码 Setup Code · `wss://` · v2「后台 30s 断连」开关 · `client.mode=operator` 试验（现网 `ui` 已验通，以真机为准）。

官方 App	龙虾助手
第一性原理
手机是 Agent 的 外设/节点（node）
手机是人类的 对话与看板终端（operator）
谁受益
Agent 能「用手机干活」
用户能「舒服地聊、看、下文件」
交互方向
Gateway → 手机：node.invoke（相机、Canvas、通知…）
用户 → Gateway：sessions.send；Gateway → 用户：聊天气泡 + MODAL 白板
官方 App 也有聊天，但产品重心是「给 Agent 装身体」；你们是「给人类做专用 UI」，MODAL 白板、画廊下载、高德跳转、仿微信输入都是为人设计的，不是 node 命令面。

一句话：官方是 Agent-centric，你们是 Human-centric——在 OpenClaw 生态里这是合理分工，不是谁替代谁。

握手/认证：已经对齐不少，还有几处值得跟官方靠拢
你们当前实现（OpenClawConnectHandshake、OpenClawDeviceAuth、WebSocketOpenClawSessionClient）核心路径是对的：

connect.challenge → 带 nonce 的 Ed25519 签名
role: operator + operator.read / operator.write
client.id: openclaw-android（与官方 Android operator 侧一致）
配对后持久化 deviceToken，重连优先用 token
签名用的 token 与 auth.token 发送值一致（官方 #39417 曾踩过的坑，你们没踩）
和官方 Gateway 协议对比，稳定性上最值得研究的差异如下：

1. client.mode（已与官方 operator 示例不同，现网可工作）

**当前实现（2026-07-03，真机已验）**：

```
client.mode = "ui"       // GatewayConfig.clientMode
role = "operator"        // GatewayConfig.connectRole
```

官方部分 operator 客户端示例为 `mode` 与 `role` 均为 `operator`。你们曾用 `mode=node` 绕旧 schema；现已改为 **`ui` + v3 签名**，配对与重连正常。

**可选试验**（非 blocking）：改为 `client.mode = "operator"` 并同步 device-auth payload，观察 metadata pinning / scope-upgrade 是否更稳。若与现网 Gateway 冲突，**保持 `ui`**。

2. 设备签名 v2 → v3（**已落地**）

签名为 v3 payload（含 `platform`、`deviceFamily`、challenge nonce）：

```
v3|deviceId|clientId|clientMode|role|scopes|signedAt|token|nonce|android|Android
```

3. 配对入口（体验）

| | 官方 | 你们 |
|---|------|------|
| 首次连接 | Setup Code / QR | App 内手动 Token + Control UI 批准（**暂无扫码计划**） |
| 远程 | 常配合 `wss://` | **Tailscale + `ws://`**（主线）；公网 `wss` **暂不做** |
| 发现 | mDNS | 手写 IP |

4. 连接健壮性

| 项 | 状态 |
|----|------|
| UNAVAILABLE + `retryAfterMs` 握手重试 | **已落地**（2026-06-30） |
| 前台 Service 保活 + 通知栏状态 | **已落地**（2026-07-03，`GatewayConnectionService`） |
| `hello-ok.policy` / `tickIntervalMs` → OkHttp WS ping | **已消费** |
| 进程级 `GatewayConnectionManager` + 静默重连 | **已落地**（2026-07-03 Phase A） |
5. 不必对齐的部分（刻意保持差异）
官方能力	龙虾助手要不要跟
role: node + camera/notification/SMS
不要，除非你想让 Agent 遥控手机
chat.send / chat.history
可选；你们用 `sessions.send` + **`agent:main:<UUID>`**（2026-07-01 起；非 `agent:main:main`），换 API 收益不大
A2UI / canvas.navigate 命令面
不要；你们走 ===MODAL=== 推送，是另一套白板模型
Talk 连续语音
看需求；你们已选「输入法语音 + TTS」，更轻
建议的研究路线（可选，非开源 blocking）

1. 试验 `client.mode=operator`（仅当现网 `ui` 遇 metadata 问题时）
2. 公网 `wss://`（有人需要再做）
3. Gateway 侧 `client.profile`（缓解 MODAL 偶发纯文字，见契约讨论）

**明确不做**：扫码 v1.1、v2「后台 30s 断连」开关（保持长连 + 前台 Service）。

总结
功能差异：官方 App 主服务 Agent（node），龙虾助手主服务人类用户（operator + 定制白板）。
握手认证：已在 operator + device-auth v3 + deviceToken + 前台保活主路上；与官方差距主要在扫码/`wss` 体验，非协议不通。
策略：握手层保持稳定即可；业务层（MODAL、MRU 白板、画廊、高德）继续走自有 skill 契约。



\--------------------------------------------------------------

关于支持app端独立进程的实现： 

方案：App 用独立 Session（推荐，改动最少）



**（历史建议，2026-07-01 已采用 UUID 方案）** 让 App 连独立 session key：`agent:main:<UUID>`，由 `AppSessionStore` 管理；**禁止** `agent:main:main`。



Gateway 里两个 session 是完全隔离的，各自的 turn 互不阻塞：



复制

agent:main:main（Control UI）→ 跑自己的 turn

agent:main:app（Android App）→ 并行跑自己的 turn

唯一的代价是两个 session 上下文不共享——你在 Control UI 聊了一半的内容，App 那边看不到。但这通常是可接受的，甚至可以故意让 App 端用更轻量的上下文（比如只保留最近的几条）。



实现上 App 在握手与 `sessions.create` 时使用 `AppSessionStore` 的 key；Gateway Agent 通过 **`client.id=openclaw-android`** 识别手机，勿硬编码 `agent:main:main`。

