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
`storage/index.json` — 用户保存的文件清单

## 脚本
`scripts/file_manager.py` — 生成文件列表 HTML
`scripts/save_to_storage.py` — 保存文件并写入 mimeType
`scripts/mime_utils.py` — 共享 MIME 类型推断
`scripts/upload_server.py` — 提供下载 + 删除 API

## 流程

### 1. 生成文件列表 HTML
```bash
python scripts/file_manager.py
```

输出 JSON：`{"ok": true, "url": "/__openclaw__/canvas/file_manager.html", "count": N}`

**脚本逻辑：**
- 读取 `storage/index.json`
- 过滤掉 tags 含"临时"的文件（临时文件会被 cleanup 定期清理）
- 获取每个文件的实际 size
- 为每个 item 确保有 `mimeType`（index 有则用，无则按扩展名推断）
- 按保存时间倒序排列
- 生成 HTML

### 2. HTML 页面功能

**文件分组：**
- 🖼 图片（jpg/png/gif/webp）
- 📄 PDF
- 📝 文档（docx/xlsx等）
- 📁 其他

**每个文件显示：**
- 类型图标 + 显示名称 + 大小 + 保存日期

**交互（⚠️ 必须走画廊协议，两步缺一不可）：**
1. **页面加载时**设置 `window.__LITTLEHELPER_GALLERY__ = { title, items: [{ fileId, fileName, displayName, mimeType, downloadUrl }] }`
2. **下载触发** `location.href = 'littlehelper://gallery/download?index=N'`

**`mimeType` 必填**，缺失会导致 App 下载校验失败。值与 `upload_server.py _guess_mime` / `mime_utils.guess_mime` 一致：
- PDF → `application/pdf`
- DOCX → `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- HTML → `text/html`
- 图片 → `image/jpeg` / `image/png`
- 未知 → `application/octet-stream`

`file_manager.py` 渲染时优先使用 `index.json` 中的 `mimeType`，缺失时按扩展名使用 `mimeFromName()` 推断。

`net::ERR_UNKNOWN_URL_SCHEME` 是 WebView 正常 console 错误，不影响下载。

**删除：** 点击 🗑 → 确认弹框 → `POST /file/delete/{fileName}` → 刷新页面

### 3. 上传服务器接口
`upload_server.py` 提供：
- `GET /file/download/{fileName}` — 下载文件（返回 `Content-Disposition: attachment`）
- `POST /file/delete/{fileName}` — 删除文件（删文件 + 缩略图 + index.json 记录）

### 4. 展示协议
```
===MODAL===
{
  "action": "open",
  "blocks": [{
    "id": "skill-gallery",
    "type": "webview",
    "title": "文件管理",
    "data": {
      "url": "/__openclaw__/canvas/file_manager.html",
      "scrollable": true,
      "fillHeight": true
    }
  }]
}
```

## 关键规则
- **所有 App 端文件下载必须走画廊协议**（`__LITTLEHELPER_GALLERY__` + `littlehelper://gallery/download?index=N`）
- ❌ 不要用 `<a download>` 标签（跨端口被安全策略拦截）
- ❌ 不要用 fetch + Blob（文件进 WebView 缓存，不进系统 Downloads）
- 上传服务器重启后需要重新启动
- 删除不可恢复，确认弹框是最后一道防线
