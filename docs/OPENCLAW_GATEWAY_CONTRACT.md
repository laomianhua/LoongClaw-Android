# LittleHelper ↔ OpenClaw Gateway 集成契约

> **版本**：2026-07-04（多 Agent + chat.send）  
> **App 角色**：哑终端壳（WebSocket + MODAL 渲染 + TTS）  
> **Gateway 角色**：意图理解、生成 MODAL、发布 Canvas HTML  

持久化副本：
- Gateway skill（标准路径）：`~/.openclaw/workspace/skills/littlehelper-modal/SKILL.md`
- Gateway 备份 / MEMORY：`~/.openclaw/skills/littlehelper-modal.md`（非自动扫描，仅备查）
- 本仓库镜像：`scripts/skills/littlehelper-modal/SKILL.md`
- 开发协调：`scripts/openclaw_dev_session.py`（session `agent:main:cursor-dev`）

推送命令：`python scripts/publish_littlehelper_gateway_skill.py`

---

## 1. 会话与通道

| 标识 | 用途 |
|------|------|
| `client.id = openclaw-android` | **识别 LittleHelper 手机客户端**（MODAL / 回复应路由到此连接） |
| `agent:{name}:main` | **App 用户会话**（设置页「智能体名称」→ `SessionKeyResolver`；默认 `main` → `agent:main:main`） |
| `agent:laoxia:main` 等 | 家庭/云端多 Agent：每人独立 agent + workspace |
| `agent:main:main` | 默认 agent 主会话；App 默认与 Control UI **共用**此会话 |
| `agent:main:cursor-dev` | Cursor IDE 开发协调，**勿与 App 混用** |

**App 连接顺序**（与 PC 客户端一致；subscribe 为收消息必需）：

`WebSocket connect` → `hello-ok` → **`sessions.messages.subscribe`** → **`chat.send`** → 收 `chat.delta` / `session.message`。

**Gateway Agent 注意**：不要假设手机用户会话固定为某一 key。应依据 **`client.id=openclaw-android`**（及该连接上的 `sessionKey`）下发 MODAL 与助手回复。

---

## 2. 助手回复 wire 格式（必须）

凡需打开/更新/关闭白板，**禁止只回纯文字**。必须：

```
===CHAT===
（一两句摘要；不要贴 JSON、Markdown 表格、大段 HTML）

===MODAL===
{"action":"open|update|close","blocks":[...]}
```

| 段 | App 行为 |
|----|----------|
| `===CHAT===` | 聊天气泡正文（`MessageBlockParser.chatDisplayText`） |
| `===MODAL===` | JSON → `ModalState` → `ModalCanvasHost`；**紧跟完整 JSON，勿换行插标记** |

**禁止 `===END===`**：App 真机已确认该标记会导致 MODAL 解析失败；JSON 须为 `===MODAL===` 后的裸文本，不要用 markdown 代码块包裹。

**分轨下发**：`payload.modal` 或 `content[]` 中 `type:modal` 与 `deltaText` 可并存；App 在 `GatewayEventMapper.resolveAssistantWire` 合并。

**解析失败**：App 显示黄条「白板内容解析失败，已仅显示文字回复」，白板保持上一帧。

---

## 3. MODAL JSON schema

### 3.1 顶层

```json
{
  "action": "open",
  "blocks": [ { "id": "...", "type": "...", "title": "...", "data": {} } ]
}
```

| action | App 行为 |
|--------|----------|
| `open` | 替换 blocks，`loadRevision++`，白板展开 |
| `update` | 按 `id` 合并 blocks，`loadRevision++` |
| `close` | 关闭白板 |
| `noop` | 忽略 |

每个 block **必须有** `id`、`type`；`title` 可选。

### 3.2 支持的 `type`

| type | 渲染器 | data 要点 |
|------|--------|-----------|
| `webview` | `WebViewBlockRenderer` | 见 §4 |
| `table` | `TableBlockRenderer` | 见 §5 |
| `markdown` | `MarkdownBlockRenderer` | `{ "content": "..." }` — **Phase 1 轻量子集**：段落、列表、粗体、行内代码、标题、引用；**无** Markdown 表格、**无** `[text](url)` 链接；复杂排版用 `table` 或 webview 脚本 |
| `chart/line` | `ChartLinePlaceholderRenderer` | `{ "series": [...], "options": {} }` |

---

## 4. webview 块

```json
{
  "id": "map-1",
  "type": "webview",
  "title": "华亭嘉园 → 天安门",
  "data": {
    "url": "/__openclaw__/canvas/route.html",
    "scrollable": false,
    "fillHeight": true
  }
}
```

| 规则 | 说明 |
|------|------|
| **url** | 相对路径 `/__openclaw__/canvas/<file>`；**禁止** `http://100.x` / `192.168.x`（App 用 `gatewayBaseUrl` 拼接） |
| **fillHeight** | 单 webview 块时 `true`，撑满白板 |
| **scrollable** | 地图/全屏页建议 `false` |
| 发布顺序 | 先写 `~/.openclaw/canvas/<file>`，`curl 200` 后再 MODAL |

**地图 canonical**：
- 使用 `/__openclaw__/canvas/map.littlehelper.html`（受保护副本）
- **禁止**覆写 `map.html`；App 会将 `map.html` 别名重定向到 `map.littlehelper.html`

**App 兜底**：绝对 URL 若路径含 `/__openclaw__/canvas/`，会改写到当前连接的 Gateway 基址。

---

## 5. table 块

**推荐 schema（对象格式）**：

```json
{
  "headers": [
    { "key": "name", "label": "基金名称" },
    { "key": "amount", "label": "持有金额", "align": "right" }
  ],
  "rows": [
    { "name": "沪深300ETF", "amount": "580,000" }
  ],
  "options": { "highlight": "red_up_green_down", "striped": false }
}
```

**App 兼容简写**（不推荐，但可解析）：
- `headers`: `["列1","列2"]`
- `rows`: `[["a","b"],["c","d"]]`

---

## 6. Canvas HTTP

| 项 | 值 |
|----|-----|
| 磁盘目录 | `~/.openclaw/canvas/` |
| HTTP 路径 | `/__openclaw__/canvas/<filename>` |
| 鉴权 | `Authorization: Bearer <token>`（App WebView 自动注入） |

误发 `/openclaw/canvas/...`（单下划线）会 404；App 侧 `GatewayCanvasUrlNormalizer` 有部分兜底。

---

## 7. App 侧已实现（不依赖 Agent 记忆）

| 能力 | 模块 |
|------|------|
| wire 解析 | `MessageBlockParser` |
| Gateway 事件 → MODAL | `GatewayEventMapper`, `GatewayModalAdapter` |
| URL 规范化 | `ModalCanvasUrlResolver`, `GatewayCanvasUrlNormalizer` |
| 白板 UI | `ModalCanvasHost`, `ModuleHost` |
| WebView 切换重建 | `loadRevision` + Compose `key()` |
| 解析失败提示 | `modalParseWarning` → `OpenClawStatusBanner` |
| 高德 App 跳转 | `AmapDeepLink` + `GatewayCanvasWebViewClient.shouldOverrideUrlLoading` |

---

## 7.1 白板地图 → 高德 App（底栏入口 + Gateway 传参）

App 白板**底栏**提供紧凑入口「用高德地图查看」（与历史切换、长按删除同一行小字）。  
**跳什么、带哪些参数，全部由 Gateway 在 Canvas HTML 里决定**；App 只拦截 `androidamap://` / `amapuri://` 并唤起高德（未安装则 H5 降级）。

### Gateway 必须设置 `window.__LITTLEHELPER_MAP__`

在 Canvas `<script>` 中、地图逻辑之前或之后写入：

#### 方式 A（推荐）：直接给完整 URI

```javascript
window.__LITTLEHELPER_MAP__ = {
  amapUrl: "amapuri://route/plan/?sid=&slat=39.9785&slon=116.3617&sname=%E5%8D%8E%E4%BA%AD%E5%98%89%E5%9B%AD&did=&dlat=39.9087&dlon=116.3975&dname=%E5%A4%A9%E5%AE%89%E9%97%A8&dev=0&t=0"
};
```

Gateway 完全控制 URI；中文须 URL 编码。

#### 方式 B：结构化 `action` + 字段

```javascript
// 单点查看
window.__LITTLEHELPER_MAP__ = {
  action: "view",
  lat: 39.9785,
  lng: 116.3617,
  name: "华亭嘉园"
};

// 起终点路线（驾车 t=0）
window.__LITTLEHELPER_MAP__ = {
  action: "route",
  route: {
    sLat: 39.9785, sLng: 116.3617, sName: "华亭嘉园",
    dLat: 39.9087, dLng: 116.3975, dName: "天安门",
    t: "0"
  }
};

// 单点导航（起点取用户 GPS）
window.__LITTLEHELPER_MAP__ = {
  action: "navi",
  lat: 39.9087,
  lng: 116.3975,
  name: "天安门"
};
```

| `action` | 生成 URI | 高德内表现 |
|----------|----------|------------|
| `view`（默认） | `androidamap://viewMap?...` | 查看 POI |
| `route` | `amapuri://route/plan/?...` | 路线规划（起终点） |
| `navi` | `androidamap://navi?...` | 导航到终点 |

`action` 省略时：有 `route` → 路线；仅有 `lat/lng` → 查看。

#### 方式 C：URL 查询参数（动态页备用）

```
/__openclaw__/canvas/route.html?lh_action=route&lh_slat=39.9785&lh_slng=116.3617&lh_sname=华亭嘉园&lh_dlat=39.9087&lh_dlng=116.3975&lh_dname=天安门&lh_t=0
```

### 约定

- 坐标系：**GCJ-02**，URI 内 `dev=0`
- `t`：0驾车 1公交 2步行 3骑行
- Canvas **禁止**自绘任何高德跳转按钮；**必须**设置 `__LITTLEHELPER_MAP__`，由 App 底栏触发 `window.__LH_openAmap()`
- 勿用 `https://uri.amap.com/navigation` 作主跳转（WebView 难唤起原生 App）

### App 侧（无需 Gateway 关心）

- 白名单：`androidamap://`、`amapuri://`
- 模块：`AmapDeepLink`、`AmapCanvasInjector`、`CanvasWebViewBridge`
- 底栏文案（历史）：`左右滑动切换 · 长按删除`
- 地图页在标题栏右侧显示「用高德地图查看」（15sp，与标题同大；仅 `__LITTLEHELPER_MAP__` 有效时出现）

### Gateway 禁止项（硬约束）

| 禁止 | 原因 |
|------|------|
| 在 Canvas 里画「高德导航」「网页版」「在高德查看」「用高德地图查看」等**任何跳转按钮** | 入口由 **App 底栏**统一提供 |
| 「网页版」按钮 / 链接 | 未装高德时 **App 自动 H5 降级**，无需 Gateway 提供 |
| 自绘 `#lh-amap-bar` 或底部大按钮 | 与 App 冲突；只设 `__LITTLEHELPER_MAP__` 即可 |

**Gateway 只做一件事**：在 HTML 里写 `window.__LITTLEHELPER_MAP__ = { action, route, ... }`，其余交给 App。

---

## 8. 联调验收清单（已通过 2026-06-22）

| 项 | 触发示例 | 验收 |
|----|----------|------|
| 静态 WebView | 白板测试1 | 按钮 + JS |
| table | 白板测试2 | 表头+多行 |
| markdown | 白板测试3 | 粗体/列表 |
| chart/line | 白板测试4 | 折线占位 |
| 组合 | 白板测试5 | 多 block |
| 动态 HTML | 白板测试6 | Agent 写盘 |
| 真地图 | 打开华亭嘉园地图 | 高德瓦片 |
| update | 白板测试8 | 增量更新 |
| close | 关闭白板 | 收起 |
| 真场景 | 华亭嘉园→天安门 | 聊天气泡 + 路线地图 |

---

## 9. 常见故障对照

| 现象 | 原因 | 处理方 |
|------|------|--------|
| 「等待 OpenClaw 指令」 | 未收到 MODAL | Gateway 补 wire |
| 黄条「解析失败」 | MODAL JSON 语法错误/截断；或含 `===END===` | Gateway 修 JSON；**去掉** `===END===` |
| 白板不刷新 | 新回复无 MODAL | Gateway 必须 `action:open` |
| 表格空灰行 | headers/rows 格式错误 | 用 §5 对象 schema |
| WebView 白屏 | URL 404 或绝对地址不可达 | 相对路径 + curl 200 |

---

## 10. 变更流程

1. 修改本文件 + `scripts/skills/littlehelper-modal/SKILL.md`（保持同步）
2. `python scripts/publish_littlehelper_gateway_skill.py` 推送到 Gateway
3. App 协议变更时更新单测：`SessionReducerTest`, `GatewayEventMapperTest`, `MessageBlockParserTest`
