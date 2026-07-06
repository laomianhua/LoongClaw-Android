# LittleHelper Android — MODAL & Canvas 规范

> **适用客户端**：`client.id = openclaw-android`（LittleHelper Android App）  
> **会话 key**：设置页 **智能体名称** → `agent:{name}:main`（默认 `agent:main:main`）  
> **契约全文**：LittleHelper 仓库 `docs/OPENCLAW_GATEWAY_CONTRACT.md`  
> **更新**：2026-07-04（与 `SKILL.md` 同步；多 Agent · subscribe + chat.send）

## 你必须遵守

### 1. 打开白板时必须用 wire 双线

```
===CHAT===
（一两句摘要）

===MODAL===
{"action":"open","blocks":[...]}
```

**禁止** `===END===`（会导致 App 解析失败）。**禁止**只回复纯文字而不带 `===MODAL===`。

（其余章节与 `scripts/skills/littlehelper-modal/SKILL.md` 保持一致，推送 skill 请以 SKILL.md 为准。）
