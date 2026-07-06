# LoongClaw Gateway Bundle 2.1.1 整改方案

> 日期：2026-07-06
> 基于：2.1.0 新机（Richard）验收结果
> 目标：下一版新机 install 即可交付，无需手工修补

---

## 一、验收结果总表（2026-07-06）

| # | 场景 | 结果 | 归属 |
|---|------|------|------|
| 1 | 静态 webview | ✅ | — |
| 2 | table | ✅ | — |
| 3 | markdown | ✅ | — |
| 4 | chart/line | ⚠️ 首次失败，纠正格式后 ✅ | SKILL 缺口 |
| 5 | 表+折线组合 | ✅ | — |
| 6 | update | ✅ | — |
| 7 | close | ✅ | — |
| 8 | App 上传 :18889 | ✅ | — |
| 9 | 单图 file_viewer | ✅ | — |
| 10 | 画廊+长按下载 | ✅（Agent 手修 index 后） | bundle 缺口 |
| 11 | 文件管理器 | ⚠️ 开/下载 ✅，删除 ❌ | sidecar 缺口 |
| 12 | 记事本 | ✅（空数据正常） | — |
| 13 | 天气卡片 | ✅ | — |
| — | PDF file_viewer | ❌ 白板空 | canvas 静态资源未交付 |
| — | 「我的文件」点本地图 | ❌ | App（非 bundle） |
| — | 协议确认长 markdown | ❌ 黄条误解析 | SKILL/AGENTS |
| — | doctor 6/6 但 PDF 不可用 | ⚠️ 检查项不足 | doctor 缺口 |

---

## 二、问题清单

### A. Bundle 缺文件/缺实现（必须改 repo）

| ID | 现象 | 根因 | 严重度 |
|----|------|------|--------|
| B1 | PDF 白板空白 | file_viewer.py 依赖 canvas/pdf.min.js + pdf.worker.min.js，install 未部署 | P0 |
| B2 | 文件管理器删除失败 | file-manager SKILL 写 POST /file/delete/{fileName}，bundle upload_server.py 无此路由 | P0 |
| B3 | 保存到 storage 无标准脚本 | SKILL 引用 save_to_storage.py，repo/bundle 不存在 | P0 |
| B4 | 画廊前 index 格式错 | 脚本要求 {"items":[...]}，Agent 曾写成数组根 | P0 |
| B5 | MODAL url 路径 | Agent 手填；App 兜底不保证 | P1 |

### B. SKILL / AGENTS 文档

| ID | 现象 | 根因 | 严重度 |
|----|------|------|--------|
| S1 | 折线图黄条 | Agent 用 Chart.js labels/datasets；App 要 series[].points[] | P1 |
| S2 | 协议确认黄条 | 正文出现 ===CHAT=== 字面量触发 MessageBlockParser 误解析 | P1 |
| S3 | url 必须来自脚本 stdout | 已有铁律但 PDF/画廊仍偶发手填 url | P1 |

### C. install / doctor

| ID | 现象 | 根因 | 严重度 |
|----|------|------|--------|
| D1 | doctor 全过但 PDF 不可用 | manifest.doctor.canvasFiles 为空 | P0 |
| D2 | 无 storage 种子 | 新机 storage/index.json 不存在时无模板 | P2 |
| D3 | sidecar 需手动前台跑 | 已知限制 | P3 |

### D. App 侧

| ID | 现象 | 根因 | 严重度 |
|----|------|------|--------|
| A1 | 「我的文件」点图片「无法打开」 | file_paths.xml 仍为 Download/LittleHelper/ | P1 |

---

## 三、整改方案

### 阶段 1 — P0：补齐交付物（新机必达）

#### 1.1 Canvas：pdf.js 静态资源

- **做法：** repo 增加 `gateway-bundle/canvas/`（或 `gateway-export/canvas/`），包含 `pdf.min.js`、`pdf.worker.min.js`
  - 锁定 pdfjs-dist@3.11.174（与 file_viewer.py `<script src="/__openclaw__/canvas/pdf.min.js">` 一致）
- **install：** `install_bundle.py` 增加一步 → 复制到 `{OPENCLAW_STATE_DIR}/canvas/`
- **doctor：** `GET http://127.0.0.1:18789/__openclaw__/canvas/pdf.min.js` → 200
  - `manifest.doctor.canvasFiles` 增加 `["pdf.min.js","pdf.worker.min.js"]`
- **验收：** 上传 PDF → file_viewer.py → 白板可翻页

#### 1.2 Sidecar：文件删除 API

- **做法：** `upload_server.py` 实现 `POST /file/delete/{fileName}`
  - 删 uploads 匹配文件、storage 副本、缩略图、index.json 对应项
  - ✅ 已实现在生产环境，直接 harvest
- **doctor：** standard profile 可选 — POST 探测或文档化
- **验收：** 文件管理器 🗑 删除成功

#### 1.3 Workspace：save_to_storage.py

- **做法：** 从生产 Gateway harvest 进 `gateway-export/scripts/save_to_storage.py`
- **行为：** `python scripts/save_to_storage.py <fileId> "<displayName>" --tags tag1,tag2`
  - 复制到 workspace/storage/ + 更新 index.json（`{"items":[...]}`）
- **验收：** 上传图 → 保存+标签 → generate_gallery.py 无需手修 index

#### 1.4 install + doctor 收紧

- `manifest.json` → `doctor.canvasFiles: ["pdf.min.js","pdf.worker.min.js"]`
- `doctor_bundle.py` → 检查 canvas 文件存在；standard 下检查 save_to_storage.py、file_viewer.py
- **目标：** doctor 不过 = 不宣称验收通过

### 阶段 2 — P1：SKILL / AGENTS 对齐

#### 2.1 littlehelper-modal

- 增加 chart/line 完整示例（series/points 结构）
- 增加禁止事项：正文不得出现 wire 标记字面量
- 明确 Chart.js labels/datasets 禁止

#### 2.2 file-viewer-display

- 前置条件：install 已部署 pdf.js
- 强调：file_viewer.py 的 url 只取自 stdout

#### 2.3 gallery-display / file-manager

- index.json 规范块（JSON 示例 + 禁止根数组）
- file-manager：删除依赖 POST /file/delete

#### 2.4 AGENTS.inject.md（v2.1 或 v3）

- 标准触发语 + 禁止手写 index
- 确认类回复用纯文字，禁止含 wire 标记

### 阶段 3 — P2：体验与文档

- storage 种子：install 若不存在则创建 `workspace/storage/index.json`：`{"items":[]}`
- EXPORT_NOTES.md：更新 harvest 清单
- README / bundle README：「标准 profile 交付清单」一页表
- bundle 版本：bump 2.1.1（patch）

### 阶段 4 — App

- `file_paths.xml`：`Download/LittleHelper/` → `Download/LoongClaw/`
- 可选：AndroidManifest.xml `<queries>` 增加 ACTION_VIEW + image/\*

---

## 四、Harvest 交付物（已从生产环境收集）

| # | 索取项 | 用途 | 状态 |
|---|--------|------|------|
| ① | `save_to_storage.py` | B3 标准保存 + index 写入 | ✅ 已 harvest → `gateway-export/scripts/` |
| ② | `upload_server.py`（含 delete） | B2 与生产行为对齐 | ✅ 已 harvest（含 POST /file/delete/） |
| ③ | `pdf.min.js` + `pdf.worker.min.js` | B1 版本对齐 | ✅ 已 harvest → `gateway-export/canvas/` |
| ④ | `index.json` 样例 | 文档/测试 | ✅ 已确认格式（含 tags 字段） |
| ⑤ | `tag_images.py` | gallery SKILL 提及 | ✅ 已 harvest（可选） |

---

## 五、执行顺序（Cursor Agent 模式）

1. ~~生产 Gateway harvest~~ ✅ 已完成（2026-07-06 10:57）
2. 合入 gateway-export
3. install 部署 canvas+scripts
4. sidecar 补 delete（✅ 已有）
5. SKILL/AGENTS 更新
6. doctor 加检查项
7. build_gateway_bundle.py
8. 新机干净 install 回归
9. App file_paths

---

## 六、下一版 bundle 回归清单（15 分钟）

1. 静态 webview、table、markdown、chart/line（一次过）
2. update / close
3. 上传图片 → save_to_storage.py 保存+标签 → 画廊+长按下载
4. 文件管理器列表、下载、删除
5. 上传 PDF → 打开看看（无需手工 pdf.js）
6. 记事本、天气
7. doctor 全 OK 且含 pdf.js 检查
8. App「我的文件」打开已下载图片（file_paths 修后）

---

## 七、备注

- 新机不必长期维护手工修补；pdf.js CDN 安装仅作临时验证
- 不必为通过验收去改 OpenClaw 本体
- client.id / patch 已确认 Android 不需要
