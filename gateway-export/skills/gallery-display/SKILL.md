---
name: "gallery-display"
description: "多图画廊展示与分类浏览：标签系统、缩略图、长按下载、纵向单列布局"
---

# 多图画廊展示

## 触发条件
- 「显示大胖的照片」「小黑的相册」「我的相册」「查看图片」「显示会员卡」「看看证件照」等

## MODAL id
固定使用 `skill-gallery`

## 数据流
```
用户说关键词 → 搜索 storage/index.json (匹配 displayName + tags)
             → python scripts/generate_gallery.py <关键词>
             → 生成 gallery_cache_{ts}.html → canvas 目录
             → MODAL webview
```

## 标签系统

### 图片保存时打标签
```
老夏：帮我保存这张照片，是大胖的
→ python scripts/save_to_storage.py <fileId> "大胖（睡觉）" --tags 猫,大胖
```

### 事后批量打标签
```
python scripts/tag_images.py --add 猫,大胖 --match 大胖    # 给所有"大胖"图片加标签
python scripts/tag_images.py --add 风景,旅行 --match 钟楼   # 给风景照分类
python scripts/tag_images.py --list                         # 列出所有标签
```

### 推荐标签体系
| 标签 | 用途 |
|------|------|
| 猫, 大胖 | 大胖照片 |
| 猫, 小黑 | 小黑照片 |
| 风景, 旅行 | 旅行风景照 |
| 证件 | 会员卡、证件照 |
| 医疗, 老爷子 | 夏维儒检查报告 |
| 美食 | 食物照片 |

### 搜索逻辑
`generate_gallery.py` 搜索时同时匹配 `displayName` 和 `tags` 字段，支持：
- 「大胖」→ 命中 displayName 含"大胖"或 tags 含"大胖"
- 「风景」→ 命中 tags 含"风景"（即使 displayName 里没这个词）
- 「猫」→ 大胖+小黑（如果小黑也有猫标签）

## 画廊页面

### 布局
- 纵向单列，居中缩略图（400px 自动生成）
- 顶部标题："{关键词}相册（共N张）"
- 暗色背景 `#111`

### 交互
- 长按 500ms → `littlehelper://gallery/download?index=N`
- App 拦截 Deep Link，用 `downloadUrl` 下载原图到 `Download/LittleHelper/`
- `downloadUrl` 必须用 `http://{host}:18889/file/download/{filename}`

### 页面结构
- `window.__LITTLEHELPER_GALLERY__ = { title, items: [{ downloadUrl, thumbUrl, displayName, ... }] }`
- 每个 item 是 `<div class="lh-item">` 含 `<img>` + `<div class="lh-caption">`

## 展示协议

响应格式（手机端）：
```
===CHAT===
大胖照片 3 张。

===MODAL===
{
  "action": "open",
  "blocks": [{
    "id": "skill-gallery",
    "type": "webview",
    "title": "大胖相册",
    "data": {
      "url": "/__openclaw__/canvas/gallery_cache_1782787464460.html",
      "scrollable": true,
      "fillHeight": true
    }
  }]
}
```

## 脚本位置
- `scripts/generate_gallery.py` — 画廊生成
- `scripts/save_to_storage.py` — 保存图片（支持 --tags）
- `scripts/tag_images.py` — 批量标签管理

## 缩略图
- 首次生成画廊时自动用 Pillow 生成 400x400 缩略图
- 缩略图命名：`thumb_{filename}`，存于 canvas 目录
- thumbUrl 走 Gateway 托管 `/__openclaw__/canvas/thumb_xxx.jpg`
