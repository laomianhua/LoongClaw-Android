# LittleHelper / LoongClaw — Agent 客户端路由

> 随 Gateway Companion Bundle 安装到 `~/.openclaw/companion/agents/`。供 Agent / AGENTS.md 参考。

## 当前实现（2026-07-04，Gateway 确认）

- **`metadata.client.profile` 未在 Gateway 实现**
- Agent 靠 **AGENTS.md 注入规则** + **skill** 识别连接上的 `label`（client.id）
- 当 `label == openclaw-android` 时启用 MODAL 白板协议 — 这是 **SKILL.md 规定的行为**，不是 Gateway 原生 profile 路由

## 优先级（Agent 侧应遵守）

1. **`sender` label**：`openclaw-android` → MODAL 手机版；`loongclaw-desktop` → MODAL PC 版
2. **禁止** 单独依据 `channel=webchat` 或 `session_status.active.channel` 推断「Control UI 纯文字」——App WebSocket 同样走 webchat 通道

## 回复格式

| 客户端 | 格式 |
|--------|------|
| `openclaw-android` | 必须 `===CHAT===` + `===MODAL===`（见 `littlehelper-modal` skill） |
| `loongclaw-desktop` | 同上，布局可为 PC 大屏 |
| Control UI 浏览器（无 label） | 纯文字即可 |

完整契约：`littlehelper-modal` skill 与 `docs/OPENCLAW_GATEWAY_CONTRACT.md`。
