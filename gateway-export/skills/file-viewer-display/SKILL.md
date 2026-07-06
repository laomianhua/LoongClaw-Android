---
name: "file-viewer-display"
description: "多格式文件白板查看器：PDF (pdf.js)、TXT、图片，上传即看，一个固定标签"
---

# 文件查看器 & 解读器

## 使用逻辑

| 用户意图 | 脚本 | 输出 |
|---------|------|------|
| 「打开看看」「显示 PDF」 | `file_viewer.py` | ✅ MODAL 白板 |
| 「解读/解析这个文件」 | `file_reader.py` | 文字回复 |
| 我生成的 PDF（游记等） | `file_viewer.py` | ✅ 白板 + 下载 |
| 无明确意图 | 自主判断或询问 | — |

## 支持格式

| 格式 | 白板展示 | 文字解读 |
|------|---------|---------|
| PDF | ✅ pdf.js | ✅ PyPDF2 |
| DOCX | — | ✅ python-docx |
| XLSX | — | ✅ openpyxl |
| PPTX | — | ✅ python-pptx |
| TXT/MD/CSV | ✅ `<pre>` | ✅ 直接读取 |

## 解读脚本
`python scripts/file_reader.py <fileId>`
→ JSON `{ content: "..." }` → 提取后作为文字回复

## 展示脚本  
`python scripts/file_viewer.py <fileId> [title]`

## ⚠️ 下载规则
1. 文件 → `storage/{ascii_name}`
2. downloadUrl → `http://__HOST__:18889/file/download/{ascii_name}`
3. displayName → 用户友好名称
