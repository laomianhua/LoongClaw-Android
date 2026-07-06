---
name: "file-manager"
description: "文件管理器：列出storage中用户文件，支持长按下载、点击删除"
---

# 文件管理器

## 触发
- 「文件管理器」「我的文件」「看看文件」「管理文件」等

## MODAL id
`skill-gallery`（复用画廊容器）

## 数据源
`storage/index.json` — 用户保存的文件清单（`save_to_storage.py` 写入）

## 脚本
`scripts/file_manager.py` — 生成文件列表 HTML
`scripts/upload_server.py` — 提供下载 + 删除 API（由 App 原生调用）

## 流程

### 1. 生成文件列表 HTML
```bash
python scripts/file_manager.py
```

输出 JSON：`{"ok": true, "url": "/__openclaw__/canvas/file_manager.html", "count": N}`

**脚本逻辑：**
- 读取 `storage/index.json`
- 过滤掉 tags 含"临时"的文件（遗留条目）
- 获取每个文件的实际 size
- 按保存时间倒序排列
- 生成 HTML

### 2. HTML 页面功能

**文件分组：**
- 🖼 图片（jpg/png/gif/webp）
- 📄 PDF
- 📝 文档（docx/xlsx等）
- 📁 其他

**交互（⚠️ 必须走画廊协议，App 原生请求 :18889）：**
1. **页面加载时**设置 `window.__LITTLEHELPER_GALLERY__ = { title, items: [{ downloadUrl, fileName, displayName }] }`
2. **下载** `location.href = 'littlehelper://gallery/download?index=N'`
3. **删除** 点 🗑 确认后 `location.href = 'littlehelper://gallery/delete?index=N'`

❌ **禁止** WebView 内 `fetch` 直连 `:18889`（跨域/策略会失败）

`net::ERR_UNKNOWN_URL_SCHEME` 是 WebView 正常 console 错误，不影响功能。

### 3. 上传服务器接口
`upload_server.py` 提供（由 **App 原生 OkHttp** 调用，非 WebView fetch）：
- `GET /file/download/{fileName}` — 下载文件
- `POST /file/delete/{fileName}` — 删除文件（删文件 + 缩略图 + index.json 记录）

### 4. 展示协议

**整条回复必须以 `===CHAT===` 开头**，之前不得有任何说明文字。

```
===CHAT===
共 N 个文件。

===MODAL===
{"action":"open","blocks":[{"id":"skill-gallery","type":"webview","title":"文件管理","data":{"url":"/__openclaw__/canvas/file_manager.html","scrollable":true,"fillHeight":true}}]}
```

`data.url` 必须来自 `file_manager.py` stdout 的 `url` 字段。

## 关键规则
- **禁止**手写或修改 `storage/index.json`；入库用 `save_to_storage.py`
- **禁止**在打开文件管理器时「修索引」—— 预览 PDF（`file_viewer.py`）不进列表，用户说「保存」才跑 `save_to_storage.py`
- **所有** App 端下载/删除必须走画廊 deep link（`__LITTLEHELPER_GALLERY__` + `littlehelper://gallery/...`）
- 上传服务器重启后需要重新启动
