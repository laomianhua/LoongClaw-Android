# P1 Skill Export — 老夏 Gateway 真机验证清单

## 导出日期
2026-07-04（初版）/ 2026-07-06（2.1.1 整改补 harvest）

## 源 Gateway
- OpenClaw: 2026.6.9
- OS: Windows 10, Xiaomi Air
- Agent: main (agent:main:main)

## 导出资源清单

| 资源 | 源路径 | 导出路径 | 用途 |
|------|--------|----------|------|
| generate_gallery.py | ~/.openclaw/workspace/scripts/ | gateway-export/scripts/ | 画廊生成 |
| gallery-display | ~/.openclaw/workspace/skills/ | gateway-export/skills/ | 画廊 SKILL |
| file-viewer-display | ~/.openclaw/workspace/skills/ | gateway-export/skills/ | 文件查看 SKILL |
| file-manager | ~/.openclaw/workspace/skills/ | gateway-export/skills/ | 文件管理 SKILL |
| notepad | ~/.openclaw/workspace/skills/ | gateway-export/skills/ | 记事本 SKILL |
| weather-display | ~/.openclaw/workspace/skills/ | gateway-export/skills/ | 天气 SKILL |
| **2.1.1 新增 ↓** | | | |
| save_to_storage.py | ~/.openclaw/workspace/scripts/ | gateway-export/scripts/ | 2026-07-06 harvest |
| upload_server.py | ~/.openclaw/workspace/scripts/ | gateway-export/scripts/ | 含 POST /file/delete/ |
| tag_images.py | ~/.openclaw/workspace/scripts/ | gateway-export/scripts/ | 标签管理（可选） |
| pdf.min.js | ~/.openclaw/canvas/ | gateway-export/canvas/ | PDF 渲染（pdfjs-dist@3.11.174） |
| pdf.worker.min.js | ~/.openclaw/canvas/ | gateway-export/canvas/ | PDF Worker 线程 |
| index.json 样例 | ~/.openclaw/workspace/storage/ | 见 docs/BUNDLE_2.1.1_REMEDIATION.md | 格式参考 |

## 真机验证清单（17 项全通过）

| # | 场景 | 日期 | 涉及组件 | 状态 |
|---|------|------|----------|------|
| 1 | 白板静态 WebView | 6/22 | MODAL + webview | ✅ |
| 2 | 白板表格 | 6/22 | MODAL + table | ✅ |
| 3 | 白板 Markdown 渲染 | 6/22 | MODAL + markdown | ✅ |
| 4 | 白板折线图 | 6/22 | MODAL + chart/line | ✅ |
| 5 | 组合白板（表+图） | 6/22 | MODAL multi-block | ✅ |
| 6 | 动态生成 WebView | 6/22 | canvas 写盘 + MODAL | ✅ |
| 7 | 真地图 + 高德跳转 | 6/23 | map.littlehelper.html + __LITTLEHELPER_MAP__ | ✅ |
| 8 | 白板 update 增量 | 6/22 | MODAL update | ✅ |
| 9 | 白板 close | 6/22 | MODAL close | ✅ |
| 10 | 画廊 + 下载（图片） | 7/2 | generate_gallery.py + __LITTLEHELPER_GALLERY__ + 18889 | ✅ |
| 11 | 单文件下载（PDF） | 7/2 | upload_server + 18889 | ✅ |
| 12 | 文件上传 | 6/27 | upload_server.py + 18889 | ✅ |
| 13 | 文件查看器（PDF/TXT/图片） | 持续 | file-viewer-display | ✅ |
| 14 | 多 agent 切换 | 7/4 | agents.list + subscribe + chat.send | ✅ |
| 15 | subscribe → chat.send 回复 | 7/4 | rev3.1 订阅路径 | ✅ |
| 16 | 英文界面 | 7/4 | i18n | ✅ |
| 17 | 基金实时估值（白板） | 持续 | fund-realtime-display | ✅ |

## 2.1.1 整改新增限制说明

### install 必须部署的静态资源
- `pdf.min.js` + `pdf.worker.min.js` → `{OPENCLAW_STATE_DIR}/canvas/`（file_viewer.py 依赖，缺则 PDF 白板空白）
- `upload_server.py` → 需包含 `POST /file/delete/{fileName}` 路由（file-manager 删除功能依赖）
- `save_to_storage.py` → `workspace/scripts/`（gallery-display SKILL 引用）
- `workspace/storage/index.json` → install 时若不存在则创建 `{"items":[]}` 种子

### doctor 必须检查项（新增）
- `GET /__openclaw__/canvas/pdf.min.js` → 200
- `GET /__openclaw__/canvas/pdf.worker.min.js` → 200
- 存在 `workspace/scripts/save_to_storage.py`
- 存在 `workspace/scripts/file_viewer.py`

### 已知限制
- generate_gallery.py 依赖 upload_server.py (端口 18889) 供下载
- gallery-display 硬编码 `http://{host}:18889/file/download/` 下载路径
- file-manager 读 `storage/index.json`，需 upload_server 支持
- notepad 数据存储路径为 `beancount/data/notepad.json`，通用化需改为 workspace 根路径
- weather-display 依赖 wttr.in 免费接口（国内可能被墙）
- upload_server.py 需手动前台启动（或计划任务），非 Gateway 自动拉起

## 交付给 Cursor 的操作
1. 将 `gateway-export/` 整个目录复制到 LoongClaw 仓库根目录
2. 运行 `scripts/build_gateway_bundle.py` 重新打包
3. 四个通用 skill 中的硬编码路径需通用化（参考各自的 SKILL.md）
