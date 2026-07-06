# Gateway Export（开发机 → 仓库）

从**开发用 OpenClaw Gateway** 提取资源，供审核后合入 `gateway-bundle` 发布。

## 何时做

App 功能冻结后、打 Release bundle 前。一次性或 skill 大改后重复。

## 在 Gateway 主机上运行

**Windows：**

```powershell
cd D:\Dev\LittleHelper
.\scripts\harvest_gateway_export.ps1
```

**macOS / Linux：**

```bash
cd /path/to/LittleHelper
./scripts/harvest_gateway_export.sh
```

输出目录：`gateway-export/`（可提交 PR 或本地审核后由 `build_gateway_bundle.py` 打入 zip）。

## 应提取的内容

| 源（Gateway 主机） | 目标（本仓库） |
|-------------------|----------------|
| `~/.openclaw/workspace/skills/<name>/` | `gateway-export/skills/<name>/` |
| `~/.openclaw/workspace/scripts/*.py` | `gateway-export/workspace/scripts/` |
| `~/.openclaw/canvas/<static>.html` | `gateway-export/canvas/` |

## 不要提取

- 个人无关 skill
- `MEMORY.md` 全文
- `gallery_cache_*.html` 运行时缓存
- 含密钥的 `openclaw.json`

## 审核后

1. 填写 `EXPORT_NOTES.md`
2. `python scripts/build_gateway_bundle.py`
3. 在干净 Gateway 上解压 zip → `install` → `doctor` → 真机 App 验收
