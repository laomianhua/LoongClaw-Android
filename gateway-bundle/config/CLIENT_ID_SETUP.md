# client.id 白名单

| client.id | 客户端 | install 默认 |
|-----------|--------|--------------|
| `openclaw-android` | 龙爪 App | **无需 patch**（OpenClaw `>=2026.6.9` 内置） |
| `loongclaw-desktop` | LoongClaw PC（规划） | 需 **`--with-pc-patch`** 或手动 patch |

## 不要写入 openclaw.json

`gateway.clients.allowedIds` 与 `companion.*` **不在** OpenClaw JSON Schema 中。bundle **install 仅 merge** `skills.load.extraDirs`。

## Android App（默认 install）

龙爪使用 `openclaw-android`，**不必**运行 `patch_clientid.ps1`。

```powershell
.\install.ps1
# 或 standard：.\install.ps1 -Profile standard
```

## PC 客户端（可选）

仅在使用 **LoongClaw PC 客户端**（`loongclaw-desktop`）时需要 patch（过渡方案，目标上游正式合入）：

```powershell
.\install.ps1 -WithPcPatch
# 或手动：.\scripts\patch_clientid.ps1
```

脚本行为（Gateway 原版）：

1. `%APPDATA%\npm\node_modules\openclaw\dist\client-info-*.js`
2. 插入 `LOONGCLAW_DESKTOP: "loongclaw-desktop"`（在 `ANDROID_APP` 之后）
3. 同步 `.d.ts` / plugin-sdk 类型文件
4. **不**修改 `openclaw.json`

**升级 OpenClaw 后** `dist/` 会被覆盖 → 需 **重跑** `-WithPcPatch` 或 `patch_clientid.ps1`。

## 平台

- **v2.1 Release**：**Windows Gateway 主机 only**
- Linux/macOS Gateway：不在 Release 范围；无 patch 脚本

路由说明见 `../agents/client-routing.md`（Agent 靠 label + AGENTS.md，非 Gateway profile 路由）。
