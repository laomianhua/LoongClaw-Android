# 家庭 / 多 Agent Gateway 搭建指南

> 配合 App 设置页「智能体名称」与 [`MULTI_AGENT_DEVELOPMENT_PLAN.md`](MULTI_AGENT_DEVELOPMENT_PLAN.md) 使用。

## 1. 安装 LoongClaw 配套（一次性）

```powershell
cd gateway-bundle
.\install.ps1
.\doctor.ps1
```

install 会将 `littlehelper-modal` 安装到 `~/.openclaw/shared-skills/`，并在 `openclaw.json` merge `skills.load.extraDirs`。

## 2. 新增家庭成员 agent

> **⚠️ 必须保留 `main`**：下面示例中 **`main` 始终在 list 的第一项**。若你只追加新 id（如 `laoxia`）而**删掉或未写 `main`**，Gateway 重启后默认主 agent 可能被覆盖，龙爪 App 与 Control UI 主会话会异常。

编辑 `~/.openclaw/openclaw.json`：

```json
{
  "agents": {
    "list": [
      { "id": "main", "workspace": "~/.openclaw/workspace" },
      { "id": "laoxia", "workspace": "~/.openclaw/workspace_laoxia" },
      { "id": "erzi", "workspace": "~/.openclaw/workspace_erzi" }
    ]
  }
}
```

创建 workspace 目录（空目录通常即可；AGENTS.md 是否必须请按 Gateway 版本实测）：

```powershell
mkdir $env:USERPROFILE\.openclaw\workspace_laoxia
mkdir $env:USERPROFILE\.openclaw\workspace_erzi
```

若 `agents.list` 变更不支持 hot reload，**重启 Gateway**。

## 3. 手机 App 设置

| 用户 | 智能体名称 | sessionKey |
|------|-----------|------------|
| 老夏 | `laoxia` | `agent:laoxia:main` |
| 儿子 | `erzi` | `agent:erzi:main` |
| 默认单人 | `main` | `agent:main:main` |

无需复制 skill — `shared-skills` + `extraDirs` 对所有 agent 可见。

## 4. 旧 skill 路径迁移

若 doctor 提示 `workspace/skills/littlehelper-modal/` 仍存在：

1. 确认 `shared-skills/littlehelper-modal/` 已安装
2. 手动删除或归档旧 `workspace/skills/littlehelper-modal/`（doctor 仅 WARN，不自动删除）

## 5. 个人数据边界

install **不会**写入各 agent workspace 的 MEMORY、持仓、账本等私人数据；这些由用户或 agent 运行时自行积累。
