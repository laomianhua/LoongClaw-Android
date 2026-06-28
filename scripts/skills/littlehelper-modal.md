# LittleHelper Android — MODAL & Canvas 规范

> **适用会话**：`agent:main:main`（手机 App 用户）  
> **契约全文**：LittleHelper 仓库 `docs/OPENCLAW_GATEWAY_CONTRACT.md`  
> **更新**：2026-06-22

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
