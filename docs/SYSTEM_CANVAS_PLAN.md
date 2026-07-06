# 系统白板（System Canvas）规划

> **状态**：规划中（2026-07-06）  
> **前置**：先完成 **2.1.3** 发布（删除 deep link、删除后列表刷新、聊天气泡乐观插入修复）  
> **目标里程碑**：**2.2.x** — 将助手侧「文件库」「相册」做成固定系统 Tab，降低小白用户对 Agent 口语的依赖

## 产品定位

**LoongClaw = 超级助理**：聊天是主界面；助手在 Gateway 侧帮用户存的**文件与图片**应像系统功能一样可直达，而不必每次说「打开文件管理器」。

与手机自带能力的关系：

| 能力 | 手机已有 | 助手侧系统 Tab |
|------|----------|----------------|
| 天气 | 系统天气 App | **不做**固定 Tab；对话里 `weather_card` 一次性展示即可 |
| 备忘录 | 系统备忘录 | **待定**「助手备忘」— 需差异化设计，非简单复刻 |
| 相册 | 系统相册 | **要做**「全部图片」— 仅展示助手 `storage` 里保存的图 |
| 文件 | 文件管理器 / Download | **要做**「助手文件库」— Gateway `storage`；本机下载仍走 App 顶部入口 |

## 双入口：本机 vs 助手侧（文案）

**不合并入口**，用命名区分：

| 入口 | 数据 | 建议文案 |
|------|------|----------|
| App 聊天栏上方文件夹 | `Download/LoongClaw/` | **手机下载** / 已保存到手机的文件夹 |
| 系统白板 · 文件 Tab | `workspace/storage/` + `index.json` | **助手文件库** / Agent 文件 |
| 系统白板 · 相册 Tab | `storage` 中图片子集 | **全部图片** / 助手相册 |

用户动线：**助手文件库**里点下载 → 进 **手机下载**。

## 范围（2.2）

### In scope（必做）

1. **助手文件库**（`file_manager.html` 固定 URL）
   - App 固定 Tab，无需对话
   - 删除/下载继续走 `littlehelper://gallery/...` deep link（App 原生请求 `:18889`）
   - 可选后续：点行打开 `file_viewer` 预览

2. **全部图片**（`gallery_all.html` 固定 URL）
   - `generate_gallery.py --all`（或等价空关键词 + 仅图片扩展名）
   - 排除 `tags` 含「临时」的项（与 file_manager 一致）

3. **Agent 智能相册**（保留现有能力）
   - 用户说「看大胖的相册」→ `generate_gallery.py 大胖` → `gallery_cache_{ts}.html`
   - **新开 Modal 标签**，标题如「大胖相册」；**不覆盖**系统「全部图片」Tab

4. **刷新机制**（见下文）

5. **App 壳层**：系统 Tab 状态与 Agent `modalSlots` 分层；Gateway 未连接时的空态

### Deferred（设计后再做）

- **助手备忘**：基于 `notepad.py` 重做——强调与对话联动、停车/待办等结构化数据、Agent 可维护；不单是静态月历 HTML

### Out of scope（不作为系统 Tab）

- 天气、地图、一次性图表/天气卡 → 继续 **Agent + `===MODAL===`**
- 手机已覆盖的通用工具

### Later 候选（规划池）

| 候选 | 条件 |
|------|------|
| 上传收件箱 | `uploads/` 未入库文件一览 |
| 最近查看 | `viewer_*.html` 历史 |
| 连接/健康 | 更适合设置页，非白板 Tab |

筛选三问：用户是否常在不对话时打开？数据是否在 Gateway 固定路径？能否固定 URL + 按需 refresh？

## 技术架构

### 一个引擎，两种打开方式

```
storage/index.json  (displayName, tags, fileName)
         │
    ┌────┴────┐
    ▼         ▼
系统 Tab     Agent 筛选
固定 HTML    gallery_cache_*.html
file_manager gallery_all + keyword
```

相册与文件管理共用思路：**Python 生成静态 HTML** → App WebView 加载 `/__openclaw__/canvas/...`。

### 刷新：两层

| 层 | 动作 | 执行方 |
|----|------|--------|
| 1 | 重读 `index.json`，写 `file_manager.html` / `gallery_all.html` | Gateway 主机 Python |
| 2 | WebView 加载 URL（`?rev=` 防缓存） | App |

**不要**以定时刷新为主。

**推荐策略：**

1. **主**：用户切换到某系统 Tab 时 → `GET /canvas-meta`（index mtime）→ 有变则 `POST /refresh?target=file_manager|gallery` → 再加载 WebView；同一 Tab **节流 10～30s**
2. **辅**：`save_to_storage`、`file/delete`、`tag_images` 成功后标记或刷新对应 HTML
3. **可选**：App 回前台 / Gateway 重连时检查 mtime，**延迟到用户打开 Tab 再刷**
4. **可选**：下拉手动刷新

Agent 动态相册 `gallery_cache_*.html` 为**快照**，不跟系统 Tab 一起定时刷新。

删除：文件管理器已支持 App 调 `__LH_removeGalleryItem` 就地更新；**新增**文件仍依赖 refresh。

### Sidecar 扩展（2.2 待实现）

当前 `:18889` 仅有 upload/delete/download。计划增加：

- `GET /system/canvas-meta` — `index.json` mtime、各固定页 revision
- `POST /system/refresh?target=file_manager|gallery|all` — 子进程跑 workspace 脚本

`install.ps1` 预生成 `file_manager.html`、`gallery_all.html`。

## 实现分期

| 阶段 | 内容 | 工期（粗估） |
|------|------|----------------|
| **P0** | 固定 Tab + 固定 URL；Tab 聚焦仅 `WebView.reload()`；文案区分本机/助手；Agent MODAL 不覆盖系统 Tab | 5～8 人天 |
| **P1** | sidecar meta/refresh；`gallery_all`；事件驱动失效；Tab 聚焦带 mtime | +5～10 人天 |
| **P2** | 文件点行 preview；助手备忘 redesign；下拉刷新 | 按需 |

**风险**：App 状态机 + WebView 多 Tab + refresh 竞态是主要 debug 来源；应与 2.1.3 发布**分开里程碑**测试。

## 与现有组件对照

| 组件 | 今天 | 2.2 后 |
|------|------|--------|
| `file_manager.py` | Agent 触发生成 | 固定页 + refresh |
| `generate_gallery.py` | 仅 keyword → cache 文件 | + `--all` → `gallery_all.html` |
| `MyFilesSheet` | App 顶部 | 不变，文案「手机下载」 |
| `ModalSlotReducer` | Agent 多 Tab | + 系统 Tab 与 Agent Tab 层级规则 |
| `gallery-display` SKILL | 智能相册 | 保留；写明不覆盖系统 Tab |

## 2.1.3 回归要点（发布前）

删除刷新需 **新 APK** + **install 后重跑 `file_manager.py`**（或 Agent 重新打开文件管理），仅 `install.ps1` 不够。

```powershell
cd $env:USERPROFILE\.openclaw\loongclaw-gateway-bundle-2.1.3
.\install.ps1
python $env:USERPROFILE\.openclaw\workspace\scripts\file_manager.py
```

预期：删除后列表行立即消失；sidecar 日志 `deleted: ...`。

## 相关文档

- [GATEWAY_COMPANION_BUNDLE_PLAN.md](GATEWAY_COMPANION_BUNDLE_PLAN.md) — Bundle 分发
- [OPENCLAW_GATEWAY_CONTRACT.md](OPENCLAW_GATEWAY_CONTRACT.md) — MODAL wire
- [BUNDLE_2.1.1_REMEDIATION.md](BUNDLE_2.1.1_REMEDIATION.md) — 2.1.x 整改记录
