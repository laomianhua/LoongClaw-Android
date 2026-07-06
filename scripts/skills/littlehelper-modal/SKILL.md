---
name: littlehelper-modal
description: LittleHelper Android App MODAL/Canvas 下发规范。client.id=openclaw-android 的手机会话打开白板、webview、table 时必须遵守。
---

# LittleHelper Android — MODAL & Canvas 规范

> **适用客户端**：`client.id = openclaw-android`（LittleHelper Android App）  
> **会话 key**：设置页 **智能体名称** → `agent:{name}:main`（默认 `agent:main:main`；家人各填各的 agent id）  
> **路由原则**：Gateway Agent 通过 **client.id** 识别手机用户，向该连接的 `sessionKey` 下发 MODAL  
> **契约全文**：LittleHelper 仓库 `docs/OPENCLAW_GATEWAY_CONTRACT.md`  
> **更新**：2026-07-06（chart/line 示例 · 纯聊天勿写 wire 标记 · pdf.js 由 bundle install 部署）

## 你必须遵守

### 0. ⚠️ Canvas 功能铁律（bundle 脚本）

面向 **openclaw-android** 的 gallery / 文件查看 / 天气 / 记事本 / 文件管理：

- **禁止**手写 HTML 写入 canvas、**禁止**用 markdown 块替代、**禁止**猜测 `gallery_cache_*.html` 等路径
- **禁止**使用 `/openclaw/canvas/`（单下划线）；**必须** `/__openclaw__/canvas/...`
- **必须**先执行对应 `scripts/*.py`，MODAL `data.url` **只能**来自脚本 stdout JSON 的 `url`
- **违者后果**：App 无长按下载、无 18889 下载、白板空白或解析失败——不是「样式差一点」

保存上传到 storage（画廊前置）：`python scripts/save_to_storage.py <fileId> "<显示名>" --tags 标签1,标签2`（写入 `workspace/storage/index.json`，格式 `{"items":[...]}`）。

**纯聊天确认**（如「好的」「已理解」）：一两句普通文字即可，**禁止**在正文里写 `===CHAT===` / `===MODAL===` 字面量，**禁止**长 markdown 清单。

细则与各 skill 的 **标准触发语 + 完整 wire 示例** 见 `AGENTS.md` 铁律表及 `gallery-display` 等 skill。

### 1. 打开白板时必须用 wire 双线

```
===CHAT===
（一两句摘要）

===MODAL===
{"action":"open","blocks":[...]}
```

**禁止** `===END===`（会导致 App 解析失败）。**禁止**只回复「好的」「收到」「已打开」而不带 `===MODAL===`。

### 2. webview 块

```json
{
  "id": "unique-id",
  "type": "webview",
  "title": "标题",
  "data": {
    "url": "/__openclaw__/canvas/your-page.html",
    "scrollable": false,
    "fillHeight": true
  }
}
```

- `url`：**仅相对路径** `/__openclaw__/canvas/...`
- **禁止** `http://100.x`、`http://192.168.x`
- bundle 功能页（gallery / viewer / weather 等）：**禁止**自己写 canvas 文件；**必须**跑 workspace 脚本，url 只取脚本 JSON
- 非 bundle 自定义页：先由脚本或工具写入 `~/.openclaw/canvas/`，`curl 200` 后再 MODAL

### 3. 地图（可选 · 安装包不包含）

Companion Bundle **不附带**地图 HTML。若 Gateway 管理员已自行部署 canvas 文件（参考仓库 `scripts/canvas_map_littlehelper.html`），可：

- **使用**：`/__openclaw__/canvas/map.littlehelper.html`（或自建的等价页面）
- **禁止**覆写 OpenClaw 内置 `map.html`
- **高德跳转**：只设 `window.__LITTLEHELPER_MAP__`（见契约 §7.1）；**禁止**在 HTML 里画「高德导航」「网页版」等按钮（App 底部统一注入「用高德地图查看」）

未部署地图 canvas 时，不要向 App 用户承诺地图功能。

### 4. table 块

```json
{
  "headers": [
    {"key": "name", "label": "名称"},
    {"key": "amount", "label": "金额", "align": "right"}
  ],
  "rows": [
    {"name": "示例", "amount": "100"}
  ],
  "options": {"highlight": "red_up_green_down"}
}
```

不要用 `headers: ["列1"]` / `rows: [["a"]]`（App 可兜底，但易出错）。

### 5. 支持的 type

`webview` | `table` | `markdown` | `chart/line`

### 5.1 markdown 块（Phase 1 轻量子集 — 不是万能排版）

⚠️ **禁止**用 markdown 块替代 gallery / 天气 / 文件查看 / 记事本 / 文件管理（必须 webview + 脚本）。

仅用于**短说明**（一两段话、简单列表）。`data.content` 字符串内：

| 允许 | 禁止 |
|------|------|
| 段落、`-` 无序列表、`**粗体**`、`` `行内代码` ``、`#`/`##` 标题、`>` 引用 | Markdown **表格**（含 `\|---\|` 分隔行） |
| | **链接** `[text](url)`（App 未实现，会显示纯文本） |
| | ASCII 框图、`+---+` 等伪表格 |
| | 未转义会破坏 MODAL JSON 的 `"`、`\`、裸换行 |

**复杂排版**（表格、多列、可下载、长按交互）→ **`type: table`** 或 **跑 workspace 脚本 + webview**。**禁止**在 markdown 里硬写表格/HTML；**禁止**用 markdown 冒充脚本生成的 Canvas 页。

### 5.2 chart/line 块（折线图）

**禁止** Chart.js 的 `labels` / `datasets`。**必须**用 `series` + `points`：

```json
{
  "id": "sales-chart",
  "type": "chart/line",
  "title": "上半年销售趋势",
  "data": {
    "series": [{
      "name": "销售额",
      "color": "#1890FF",
      "points": [
        {"x": "1月", "y": 12.5},
        {"x": "2月", "y": 18.3}
      ]
    }],
    "options": {"yLabel": "万元"}
  }
}
```

### 6. action

| action | 何时 |
|--------|------|
| `open` | 新白板 |
| `update` | 仅改部分 block（同 id） |
| `close` | 用户说关闭白板；按 `block.id` 关标签 |

### 6.1 MRU 标签（webview）

- 每个 **webview** 块必须有稳定 **`id`**（如 `skill-fund-realtime`）；同 id 再 `open`/`update` **不增标签**，只刷新 URL
- App 最多 **6 个可见标签** + `…` 溢出；**冷启动不恢复**标签
- **`table` / `markdown` / `chart/line` 不占标签**：在内容区叠加显示；用户点 webview 标签可切回页面

### 7. 画廊 v2（多文件：图片 + PDF 混排）

用 `generate_gallery.py` 生成 `gallery_cache_*.html`，MODAL 加载该 URL。**禁止** v1 多选/批量底栏。

页面设置（**不要**与 `__LITTLEHELPER_IMAGE__` 同页）：

```javascript
window.__LITTLEHELPER_GALLERY__ = {
  title: "相册标题",
  items: [{
    fileId: "...",
    fileName: "storage文件名.jpg",      // 18889 路径用此名
    displayName: "人类可读标题",         // App 保存文件名用此字段
    mimeType: "image/jpeg",              // 或 application/pdf
    downloadUrl: "http://__HOST__:18889/file/download/{fileName}",
    thumbUrl: "/__openclaw__/canvas/..." // 18789 预览；PDF 可空，用占位图
  }]
};
```

- **thumbUrl** → **:18789** Canvas 同源（仅展示）
- **downloadUrl** → **:18889** `http://{host}:18889/file/download/{storageFileName}`（App 下载）
- 布局：**纵向单列**；**长按 ~500ms** → `littlehelper://gallery/download?index=N`
- **禁止** Canvas 内自绘「下载」按钮

### 8. 单文件下载（单图 / 单 PDF）

```javascript
window.__LITTLEHELPER_IMAGE__ = {
  fileId: "...",
  fileName: "storage文件名.pdf",
  displayName: "持仓估值",
  mimeType: "application/pdf",
  downloadUrl: "http://__HOST__:18889/file/download/{fileName}"
};
```

App 显示白板右下角 **↓**（非 Canvas 按钮）。用户文件保存到 **`Download/LittleHelper/`**，文件名 `{displayName}_{MMdd}.{ext}`。

### 9. PDF / 游记

- 白板 HTML → Playwright 生成 PDF → 写入 storage → 18889 提供下载
- 游记、多 PDF 与图片统一走 **gallery_cache + `__LITTLEHELPER_GALLERY__`**
- 上传：`POST :18889/upload`；消息 `[upload:fileId:fileName]`

## 参考模板 — 打开 Canvas 页

```
===CHAT===
已为你打开页面。

===MODAL===
{
  "action": "open",
  "blocks": [{
    "id": "page-1",
    "type": "webview",
    "title": "页面标题",
    "data": {
      "url": "/__openclaw__/canvas/page.html",
      "scrollable": false,
      "fillHeight": true
    }
  }]
}
```

## 参考模板 — 表格

```
===CHAT===
持仓如下。

===MODAL===
{
  "action": "open",
  "blocks": [{
    "id": "holdings",
    "type": "table",
    "title": "持仓一览",
    "data": {
      "headers": [
        {"key": "name", "label": "基金"},
        {"key": "pnl", "label": "收益", "align": "right"}
      ],
      "rows": [
        {"name": "纳指ETF", "pnl": "+2.1%"}
      ],
      "options": {"highlight": "red_up_green_down"}
    }
  }]
}
```
