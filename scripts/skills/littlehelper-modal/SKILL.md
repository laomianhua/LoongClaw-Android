---
name: littlehelper-modal
description: LittleHelper Android App MODAL/Canvas 下发规范。用户会话 agent:main:main 打开白板、webview、table、地图时必须遵守。
---

# LittleHelper Android — MODAL & Canvas 规范

> **适用会话**：`agent:main:main`（手机 App 用户）  
> **契约全文**：LittleHelper 仓库 `docs/OPENCLAW_GATEWAY_CONTRACT.md`  
> **更新**：2026-06-25（含画廊 v2、多模态下载 PDF/图片）

## 你必须遵守

### 1. 打开白板时必须用 wire 三线

```
===CHAT===
（一两句摘要）

===MODAL===
{"action":"open","blocks":[...]}
===END===
```

**禁止**只回复「好的」「收到」「已打开」而不带 `===MODAL===`。

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
- 先写 `~/.openclaw/canvas/your-page.html`，`curl 200` 后再 MODAL

### 3. 地图

- **使用**：`/__openclaw__/canvas/map.littlehelper.html`
- **禁止**覆写 `map.html`
- **高德跳转**：只设 `window.__LITTLEHELPER_MAP__`（见契约 §7.1）；**禁止**在 HTML 里画「高德导航」「网页版」等按钮（App 底部统一注入「用高德地图查看」）

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

### 6. action

| action | 何时 |
|--------|------|
| `open` | 新白板 |
| `update` | 仅改部分 block（同 id） |
| `close` | 用户说关闭白板 |

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
===END===
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
===END===
```
