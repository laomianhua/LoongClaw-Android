# 语音小帮手（LittleHelper）

面向长辈的极简语音记事 Android 应用。用户**按住说话、松开发送**即可**记下**日程、生日、停车位置、物品存放等信息，或**查询**之前记过的内容。语音采用**整轨录音 + 火山引擎（Volcengine）云端 ASR** 识别，理解与决策由 DeepSeek 大模型完成，数据保存在本机 SQLite（Room），无需登录、无需云端账号。

---

## 目录

- [功能概览](#功能概览)
- [快速开始](#快速开始)
- [Dashboard 记忆卡片](#dashboard-记忆卡片)
- [对话记录管理](#对话记录管理)
- [界面与交互设计](#界面与交互设计)
- [使用说明与示例话术](#使用说明与示例话术)
- [架构设计](#架构设计)
- [AI 集成与 DB_OPS 协议](#ai-集成与-db_ops-协议)
- [status=ignore 拒绝协议](#statusignore-拒绝协议)
- [本地数据库](#本地数据库)
- [同音字与人名消歧](#同音字与人名消歧)
- [AI 自我纠错与确认轮加固](#ai-自我纠错与确认轮加固)
- [本地兜底机制（已移除）](#本地兜底机制已移除)
- [语音识别（ASR）](#语音识别asr)
- [语音播报（TTS）](#语音播报tts)
- [提醒通知](#提醒通知)
- [项目结构](#项目结构)
- [技术栈与依赖](#技术栈与依赖)
- [开发与测试](#开发与测试)
- [权限说明](#权限说明)
- [重构与修复历史](#重构与修复历史)
- [已知限制与后续方向](#已知限制与后续方向)

---

## 功能概览

### 核心能力

| 能力 | 说明 |
|------|------|
| **语音记下** | 陈述句自动识别为「记下」意图，AI 秘书理解后输出结构化写库指令，App 执行 insert/update/delete |
| **语音查询** | 含「什么/哪里/几号/哪天/谁/吗」等疑问标记时走查询路径：本地检索 → AI 口语化回答 |
| **多轮对话** | 信息不完整时 AI 温柔追问（如缺日期）；用户短答「1月1号」「下午两点」即可补充 |
| **同音字处理** | 王纲/王刚、夏子涵/夏子杭等同音不同字：支持改字更正、新增第二人、查询时选号消歧 |
| **删除与更正** | 用户说「删除」「不是…是大纲的纲」等，AI 输出带 id 的 update/delete；可一次删多条 |
| **全局清空** | 用户明确「全部删掉/清空所有记录」时 AI 输出 `clear_all`，App 弹窗二次确认后执行 |
| **本地提醒** | 带日期的记录在约定时刻推送通知（默认当天 8:00；含 `event_time` 则精确到分钟，如「10 分钟后提醒我」） |
| **模糊拒绝** | 用户话语无法理解时，AI 返回 `status=ignore`，App 拦截、TTS 引导重说、按钮变为【按住 重说】，零脏数据入库 |
| **重要等级** | AI 自动判定 `importance_level`（`normal` / `important` / `critical`），卡片以徽章与底色区分，标题不拼接「（紧急）」等后缀 |
| **记录类型** | AI 输出 `type`（`todo` / `event` / `note` / `birthday` / `general`），Dashboard 卡片展示对应标签 |
| **待办勾选** | `type=todo` 的卡片右侧提供 Checkbox，本地一键标记 `done`，零网络、零 AI |
| **记忆卡片管理** | 下半屏 Dashboard 实时展示 Room 记录；长按卡片可本地删除单条记忆 |
| **对话记录管理** | 长按单条聊天气泡可删除；右上角 🧹 长按可清空全部对话（保留欢迎语，欢迎语也可单删） |

### 界面与交互设计（概览）

- **上半屏**：玻璃风聊天气泡流（可滚动），自动滚到最新消息
- **下半屏**：可拖拽 `BottomSheet` 记忆 Dashboard（展开约 60% 屏高，**启动时默认收起**露出拉条与首张卡片边缘）
- **底部悬浮**：红宝石拟物对讲机按钮（按住说话 / 松开发送），不随抽屉位移
- **背景**：**全透明沉浸式背景（直接透出手机桌面壁纸）** + 半透明玻璃卡片
- **零闪屏**：启动瞬间直接透出桌面，无过渡动画
- **误触过滤**：按住不足 500ms 视为误触，提示「录音时间太短」，不上传 ASR

按钮文案随状态变化：

| 状态 | 按钮文字 |
|------|----------|
| 空闲 | 按住 说话 |
| 录音中 | 松开 发送 |
| 上传识别中 | 发送中…（禁用） |
| AI 处理中 | 请稍候…（禁用） |
| 播报中 | 按住 补充（可打断 TTS） |
| AI 追问日期/时间 | 按住 回答 |
| AI 追问是否记下（SAVE 确认轮） | 按住 确认 |
| 等同音记录选号 | 按住 选号 |
| AI 明确拒绝（status=ignore） | 按住 重说 |

- **TTS 播报高亮**：助手正在播报的气泡淡黄底 + 加粗，便于长辈对照听读

欢迎语：*「您好，我是语音小帮手。您可以记下日程、停车位置、药放哪里，或问我之前记过的内容。」*

---

## 快速开始

### 1. 配置 API Key

复制 `local.properties.example` 为 `local.properties`（该文件已在 `.gitignore` 中，不会提交）：

```properties
sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
DEEPSEEK_API_KEY=sk-你的密钥

# 火山引擎 ASR（一句话识别）
VOLC_APPID=你的AppId
VOLC_TOKEN=你的Token
VOLC_CLUSTER=volcasr_default
```

- `DEEPSEEK_API_KEY` → `BuildConfig.DEEPSEEK_API_KEY`，由 `DeepSeekService` 读取
- `VOLC_*` 三个字段 → `BuildConfig`，由 `VolcengineAsrService` 读取
- 未配置 DeepSeek 时 App 提示：*「请先在 local.properties 配置 DEEPSEEK_API_KEY」*
- 未配置火山 ASR 时识别会失败并提示配置 `VOLC_APPID / VOLC_TOKEN / VOLC_CLUSTER`

### 2. 编译安装

```bat
cd d:\Dev\LittleHelper
gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 3. 运行单元测试

```bat
gradlew.bat testDebugUnitTest
```

测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`

### 4. 调试语音识别

火山 ASR 相关日志可在 Logcat 中按包名或 `VolcengineAsrService` 过滤。录音文件为缓存目录下的临时 `.wav`，识别结束（成功或失败）后会在 `finally` 中删除，不占用手机存储。

```bat
adb logcat -s LittleHelperSTT
```

---

## Dashboard 记忆卡片

下半屏 **记忆 Dashboard** 通过 `recordsFlow`（Room `Flow`）实时驱动，与上半屏对话流解耦。

### 卡片展示

| 元素 | 说明 |
|------|------|
| **类型标签** | 根据 `type` 显示：📌 待办 / 📅 事件 / 📝 备忘 / 🎂 生日 / 💬 通用 |
| **重要徽章** | `critical` → ❗ 浅红底；`important` → ⭐；`normal` → 无图标（素雅） |
| **摘要** | 展示 `summary`，**不**在标题后拼接「（紧急）」「（重要）」 |
| **相关人** | 有 `person` 时展示；缺省主语由 AI 填「您」 |
| **时间** | 卡片角标展示创建时间 |

### 交互

| 操作 | 行为 |
|------|------|
| **待办勾选** | `type=todo` 时右侧 Checkbox → `toggleTodoDone()` 本地更新 `done`；完成后置灰 + 删除线 |
| **长按删除** | 弹出「确定要删除这条记录吗？」→ 确认后 `deleteRecord()` 本地删除，列表即时刷新 |
| **拖拽抽屉** | `BottomSheetScaffold`：展开约 60% 屏高；收起时露出拉条与首张卡片边缘（peek ≈ 140dp） |

> Dashboard 操作**仅改本地数据库**，不经过 DeepSeek，不触发 TTS。

---

## 对话记录管理

上半屏聊天气泡保存在 `MainViewModel` 的内存状态 `MainUiState.messages` 中，**不写入 Room**，与下半屏记忆卡片完全隔离。

| 操作 | 触发方式 | 行为 |
|------|----------|------|
| **单条删除** | 长按任意聊天气泡（含欢迎语） | 确认后 `deleteChatMessage(id)`，从列表移除 |
| **一键清空** | 长按右上角 🧹 扫把图标 | 确认「是否删除当前所有对话？」→ `clearChatMessages()` |
| **欢迎语保留规则** | 一键清空时 | 若第一条仍为系统欢迎语（含「我是语音小帮手」），**仅保留该条**，其余全部清空 |
| **欢迎语可单删** | 长按欢迎语气泡 | 与普通过话相同，可彻底删除；删后一键清空不会再「复活」欢迎语 |

> 说「删除聊天记录」等**不会**被 App 语音拦截；清空对话请用 UI 操作，避免与 AI「删除记忆」意图混淆。

---

## 界面与交互设计

### 布局层级

```
Box（全透明背景，透出系统壁纸）
├── BottomSheetScaffold（初始状态：PartiallyExpanded）
│   ├── sheetContent：玻璃风记忆 Dashboard（LazyColumn + 拖拽拉条）
│   └── content：上半屏聊天气泡 LazyColumn
├── VoiceHoldButton（底部居中悬浮，红宝石对讲机造型）
└── 🧹 清空对话入口（右上角，长按触发）
```

### 红宝石对讲机按钮

- 尺寸 240×76dp，圆角胶囊；金属色外圈 + 红宝石纵向渐变 + 右侧扬声器微孔（Canvas 绘制）
- 按住时 `scale` 缩至 0.94、`elevation` 降低，松手弹簧回弹
- **按住** → `AudioRecorderManager.start()`；**松开** → `stop()` 并自动上传火山 ASR（无需二次点发送）
- 短按 &lt; 500ms → 取消录音并 Toast「录音时间太短」

### 玻璃风卡片

- 聊天气泡与 Dashboard 卡片：`Color.White.copy(alpha = 0.75f)` 背景 + 0.5dp 白色高光边框
- `critical` 记忆卡片：浅粉红底与边框强调

---

## 使用说明与示例话术

### 记下

```
「夏子涵的生日是6月8号」
「车停在 B2 区 128 号」
「降压药放在厨房左边第二个抽屉」
「明天下午要去医院复查」
「后天上午去商场，下午去医院」（一句多安排 → AI 应输出多个 insert）
「10 分钟后提醒我」
```

AI 若觉得信息不够，会追问；此时按钮变为 **「按住 回答」** 或 **「按住 确认」**，短说即可：

```
「下午两点」
「1月1号」
「好的，记上吧」
「是的」
```

### 问时间与相对提醒

```
「现在几点？」
「今天几号？」
「10 分钟后提醒我」
```

每次请求 DeepSeek 前，App 会将**手机当前日期与时刻**（`SystemTimeContext`）注入 system prompt 顶部，供 AI 推算「后天」「10 分钟后」等并填入 `event_time`。

### 查询

```
「夏子涵生日是哪天？」
「车停哪儿了？」
「药放哪里了？」
```

### 同音字更正（改同一条记录）

```
用户：「王刚生日1月1号」
AI：「好的，记下了。」
用户：「不是刚才的刚，是大纲的纲」
→ AI 应 output update + id，把 person 从「王刚」改为「王纲」
```

### 同音不同人（新增第二条）

```
用户：「新增一条王纲的生日，1月1号」
→ insert 新记录，与已有「王刚」并存
```

### 同音查询消歧

```
用户：「王刚生日哪天？」（库里同时有王刚、王纲）
App：「我找到 2 条读音相近的记录：1. 王刚 … 2. 王纲 … 请问是第几个？」
用户：「第二个」或「2」
```

### 删除

```
「删掉王刚那条生日记录」
「把一分钟后提醒的记录都删掉」（匹配多条 → operations 中多个 delete）
「不要这条了」
```

### 全局清空（记忆数据库）

```
「把里面的东西都清空」
「全部删掉，重新开始记」
```

→ AI 输出 `clear_all`，App 弹出全屏确认框，用户确认后才清空 **Room 记忆记录**（与右上角 🧹 清空**对话**无关）。

---

## 架构设计

### 设计原则

```
用户按住说话 → AudioRecord 录 WAV(16kHz PCM) → 松手自动上传
                      ↓
              VolcengineAsrService（火山一句话识别）→ rawText
                      ↓
              DeepSeek（理解 + 决策）
                      ↓
              ___DB_OPS_START___ { operations: [insert|update|delete] }
                      ↓
              MemoryOperationExecutor → Room (little_helper.db)
                      ↓
查询路径：App 规则（精确匹配、同音列表、消歧）+ AI 口语化回答
```

- **AI 负责**：语义理解、多轮对话、决定写库操作（DB_OPS JSON）、`importance_level` / `type` 判定、判断是否 ignore
- **App 负责**：录音、云端 ASR、执行写库、本地检索、同音消歧、TTS、确认轮缝合、DB_OPS 自我纠错、ignore 拦截、Dashboard 与对话 UI

### 应用状态机（`AppPhase`）

```
IDLE → RECORDING → SENDING → PROCESSING → ANSWERING → IDLE
  ↑        ↓（<500ms 误触）                              ↑
  └──── onHoldCancel ───────────────────────────────────┘
                      ↓
                  ignore 拦截 → ANSWERING(引导重说)
                      ↓
                    ERROR → IDLE（TTS 播完后）
```

| 阶段 | 含义 |
|------|------|
| `RECORDING` | `AudioRecorderManager` 正在录 WAV |
| `SENDING` | 上传火山 ASR、等待识别文本 |
| `PROCESSING` | DeepSeek 请求或本地写库/检索 |
| `ANSWERING` | TTS 播报助手回复 |

> `LISTENING` 仍保留于枚举，供旧版 `SpeechManager` 路径备用；主流程已切换为录音 + 云端 ASR。

`MainViewModel` 是核心编排层，持有：

- `followUpContext` — 跟进类型：`NONE` / `SAVE`（确认记下）/ `QUERY`（查询补充）/ `DELETE`（删除消歧）
- `pendingDisambiguationRecords` — 同音多条时的选号上下文
- `lastSavedRecordId` — 最近一次写入，供改字更正定位
- `confirmedPersonByPrefix` — 人名前缀 → 已确认全名
- `retryListening`（`MainUiState`）— status=ignore 后等用户重说，UI 呈现「按住 重说」

### 主流程

```mermaid
flowchart TD
    A[用户按住说话] --> B[AudioRecorderManager 录 WAV]
    B --> C[松手 → VolcengineAsrService]
    C --> D{VoiceIntentDetector}
    D -->|SAVE| E[DeepSeek 秘书模式]
    D -->|QUERY| F[DeepSeek planQuery]
    E --> G[LlmResponseParser]
    G --> H{status=ignore?}
    H -->|是| I[handleAiIgnoredResponse\n零写库 + 引导重说]
    H -->|否| J[MemoryOperationExecutor]
    J --> K[(Room memories)]
    F --> L[MemoryRepository 检索]
    L --> M{多条同音?}
    M -->|是| N[DisambiguationHelper 选号]
    M -->|否| O[DeepSeek answerQuery]
    I --> P[TtsManager 播报]
    N --> P
    O --> P
    E --> P
    J --> Q[ReminderScheduler]
    K --> R[recordsFlow → Dashboard UI]
```

### 意图识别（`VoiceIntentDetector`）

| 输入特征 | 判定 |
|----------|------|
| 含「什么/哪里/几号/哪天/谁/吗」等 | **QUERY** |
| 含「不是/更正/说错了/删除」等 | **SAVE**（走秘书改库路径） |
| 上一条助手在 SAVE 追问（「帮您记上吗」等）且用户短确认 | **SAVE**（`FollowUpContext.SAVE`，API 层缝合上文） |
| 上一条助手在 QUERY 追问 | **QUERY**（`FollowUpContext.QUERY`） |
| 其余陈述句 | **SAVE** |

---

## AI 集成与 DB_OPS 协议

### DeepSeek 调用场景

| 方法 | 模型 | 用途 |
|------|------|------|
| `sendSecretaryTurn()` | `deepseek-v4-flash` | 记下 / 更正 / 删除，多轮秘书对话 |
| `planQuery()` | `deepseek-v4-flash` | 查询意图 → JSON 检索计划 |
| `answerQuery()` | `deepseek-v4-flash` | 基于候选记录生成口语化回答 |

- API：`https://api.deepseek.com/v1/chat/completions`
- `temperature = 0.2`，关闭 thinking
- 超时：connect 30s / read 60s / write 30s
- 每次请求前 **`SystemTimeContext`** 将手机当前**日期 + 时刻（HH:mm）**注入 system prompt 顶部
- 秘书模式会将最近 **12 条**本地记录（id、person、pinyin、category、date、summary）拼入 system prompt

### 秘书系统提示词要点（`DeepSeekService.SECRETARY_SYSTEM_PROMPT`）

1. 角色：中老年随身语音记事秘书，温和简短
2. **不能直接改库**，只能输出结构化 DB_OPS，由 App 执行
3. **首轮直接写入（铁律）**：事项与时间可推算时（如「后天去医院」「后天上午商场下午医院」）必须首轮 insert，禁止礼貌性二次确认；**仅三种例外**可追问：缺关键字段、同音人名歧义、person+日期与已有记录完全重复
4. **多事件拆分**：一句多安排（上午/下午）→ `operations` 中多个 insert
5. **相对时刻提醒**：「10 分钟后提醒我」→ 根据时间基准心算 `event_time`（24h `HH:mm`）+ 当天 `formatted_date_for_alarm`（ISO `YYYY-MM-DD`）
6. **同音不同字**（极重要）：改同一条 → `update` + id；另一位 → `insert`；禁止只口头答应而不输出 DB_OPS
7. **删除**：必须从本地记录查 id；匹配多条时多个 `delete`；禁止只口头说「都删掉」而不输出 JSON
8. **全局清空**：仅明确全局意图时用 `clear_all`（App 弹窗确认）；**与 UI 清空对话无关**
9. **确认轮**：用户对「要帮您记吗」短答「是的/好的」后必须立刻输出 DB_OPS
10. **无法理解时**：输出 `status=ignore`，禁止猜测写库；由 App 向用户引导重说
11. **`importance_level`**：AI 自主判定 `normal` / `important` / `critical`；解析层缺省或非法值兜底为 `normal`
12. **`type`**：AI 输出 `todo` / `event` / `note` / `birthday` / `general`；解析层非法值兜底为 `general`
13. **`person` 主语缺省**：未提及具体人名或第一人称「我…」时填 `"您"`；**严禁**把时间词（明天下午、后天等）填入 `person`
14. **`summary`**：纯内容摘要，禁止拼接「（紧急）」「（重要）」等括号后缀
15. **周期性事件**：用户表达「每天/每周」等周期意图时，必须设 `is_recurring=true`。对于「每天XX:XX」的时刻提醒，仅需填 `event_time`，无需填 `formatted_date_for_alarm`。

### DB_OPS 格式（推荐）

AI 回复 = **给用户看的日常中文** + **结构化操作块**：

```
好的，王纲的生日是1月1日，我记下了。

___DB_OPS_START___
{
  "status": "success",
  "operations": [
    {
      "op": "insert",
      "record": {
        "summary": "王纲的生日是1月1日",
        "raw_text": "用户原话",
        "person": "王纲",
        "category": "birthday",
        "event_date": "1月1日",
        "formatted_date_for_alarm": "2026-01-01",
        "is_recurring": true,
        "importance_level": "important",
        "type": "birthday"
      }
    }
  ]
}
___DB_OPS_END___
```

> **注意**：`person_pinyin` 由 App 在 `MemoryRepository.normalizeFields()` 写库前用 `PinyinHelper` 本地重算，AI 无需（也不应）提供。

**操作规则：**

| op | 说明 |
|----|------|
| `insert` | 永远新建，不覆盖旧记录 |
| `update` | 必须带 `id`（或唯一 `match`），`fields` 填要改的字段 |
| `delete` | 必须带 `id` |
| `clear_all` | 全局清空（须用户口头明确 + App 弹窗确认） |

可一次输出多个 `operations`（多 insert、多 delete 等），按顺序执行。

### 兼容旧格式

`LlmResponseParser` 同时支持：

- `___SAVE_START___` / `___SAVE_END___` → 当作 insert
- `___DELETE_START___` / `___DELETE_END___` → 删除

解析优先级：**dbOpsPayload > deletePayload / savePayload**。

### 查询分析 JSON（`planQuery`）

```json
{
  "category": "birthday",
  "keywords": ["夏子涵", "生日"],
  "prefer_latest": false,
  "answer_hint": "简短口语"
}
```

`category` 可选：`schedule` | `birthday` | `parking` | `item_place` | `mood` | `general` | `null`

---

## status=ignore 拒绝协议

当用户输入过于模糊（如「嗯嗯」「就那个」「随便」）且 AI 无法推断任何记录意图时，AI 必须输出：

```
没听懂您想记什么，请您再清楚地说一遍好吗？

___DB_OPS_START___
{
  "status": "ignore",
  "reason": "text_too_vague_or_no_intent",
  "operations": []
}
___DB_OPS_END___
```

### App 侧全链路处理

1. **`LlmResponseParser.parseDbOpsBlock`**：解析到 `status=ignore` 时构造 `LlmOpsResponse(status="ignore", operations=emptyList())`
2. **`LlmResponseParser.resolveDbOpsStatus`** fallback 规则：
   - AI 有明确 status → 使用 AI 的值
   - AI 无 status 但 operations 非空 → `"success"`
   - AI 无 status 且 operations 为空 → **`"ignore"`**（防止 AI 漏写 status 时被误当成成功）
3. **`MainViewModel.isAiIgnoredResponse`**：检测 `dbOpsPayload?.status == "ignore"`
4. **`MainViewModel.handleAiIgnoredResponse`**：
   - 清空所有跟进上下文（`followUpContext`、`pendingDisambiguationRecords` 等）
   - 设置 `retryListening = true` → UI 按钮变为【按住 重说】
   - TTS 播报：*「没听懂您想记什么，请您再清楚地说一遍好吗？」*
   - **保证零写库**：`executeMemoryChanges` 在检测到 ignore 时立即 return null
5. **`MemoryOperationExecutor`** 不被调用，`insert/update/delete` 调用次数为 0

### 自我纠错中的 ignore

`resolveSecretaryResponse` 在后台纠错（二次请求）时，若纠错结果也是 ignore，同样走上述全链路，不会产生任何 DB 写入。

---

## 本地数据库

### 库信息

- 文件名：`little_helper.db`
- 版本：**6**（Migration 1→2 新增 `person_pinyin`；2→3 新增 `formatted_date_for_alarm`；4→5 新增 `importance_level`；5→6 新增 `type`、`done`）
- ORM：Room + KSP
- 详细 Schema 见 [`docs/DATABASE.md`](docs/DATABASE.md)

### `MemoryRecord` 表结构（`memories`）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 自增主键 |
| `created_at` | Long | 创建时间戳（毫秒），`MemoryRepository.prepareForInsert` 自动填充 |
| `raw_text` | String | 用户原话留底 |
| `summary` | String | AI 提炼的口语化摘要，用于气泡展示 |
| `category` | String | 分类（见下表）；非法值入库时归一为 `general` |
| `event_date` | String? | 长辈口中的相对日期（如「后天」「6月8号」） |
| `formatted_date_for_alarm` | String? | AI 心算的 ISO 日期（`YYYY-MM-DD` 或 `YYYY-MM-DDTHH:mm:ss`）；`ReminderScheduler` 只认此字段 |
| `event_time` | String? | 精确时刻（24 小时制 `HH:mm`，如 `14:30`） |
| `is_recurring` | Boolean | 是否每年循环；birthday 类自动为 true |
| `person` | String? | 人物姓名（规范汉字），用于同音消歧 |
| `person_pinyin` | String? | 人名全拼（无空格小写），**由 App 写库前自动计算**，不依赖 AI 提供 |
| `importance_level` | String | 重要等级：`normal` / `important` / `critical`，默认 `normal`；非法值入库时归一为 `normal` |
| `type` | String | 记录类型：`todo` / `event` / `note` / `birthday` / `general`，默认 `general` |
| `done` | Boolean | 待办完成标记，仅 `type=todo` 时有意义，默认 `false` |

### 拼音自动填充机制

每条记录写入/更新 SQLite 之前，`MemoryRepository.normalizeFields()` 必然执行：

```kotlin
personPinyin = personName?.let { PinyinHelper.toPinyinKey(it) }
importanceLevel = ImportanceLevel.normalize(importanceLevel)
type = RecordType.normalize(type)
```

- Insert 路径：`prepareForInsert()` → `normalizeFields()`
- Update 路径：`prepareForUpdate()` → `normalizeFields()`

无论 AI 是否提供 `person_pinyin`，App 侧**始终用本地 `PinyinHelper` 覆盖**，保证数据可靠性。

### 重要等级（`ImportanceLevel`）

| 枚举 | value | AI 判定典型场景 |
|------|-------|-----------------|
| `NORMAL` | normal | 日常琐碎、流水账、无强时效（默认） |
| `IMPORTANT` | important | 家人生日、大额财务、用户强调「很重要/千万别忘」 |
| `CRITICAL` | critical | 健康医疗、强截止事务、不立刻做有严重后果的事 |

UI 以徽章呈现（❗ / ⭐），**不在 `summary` 文字后硬编码后缀**。

### 记录类型（`RecordType`）

| 枚举 | value | 典型场景 |
|------|-------|----------|
| `TODO` | todo | 待办（可配合 `done` 勾选完成） |
| `EVENT` | event | 日程、活动 |
| `NOTE` | note | 随手备忘、静态信息 |
| `BIRTHDAY` | birthday | 生日纪念日 |
| `GENERAL` | general | 通用（默认） |

Dashboard 卡片左上角展示类型 emoji 标签；`todo` 类型右侧提供 Checkbox。

### 记忆分类（`MemoryCategory`）

| 枚举 | value | 典型场景 |
|------|-------|----------|
| `SCHEDULE` | schedule | 日程、待办、预约 |
| `BIRTHDAY` | birthday | 生日（`isRecurring` 自动为 true） |
| `PARKING` | parking | 停车位置（查询时 `preferLatest`） |
| `ITEM_PLACE` | item_place | 物品存放位置 |
| `MOOD` | mood | 心情记录 |
| `GENERAL` | general | 通用 |

### `isRecurring` 三态更新规则

`MemoryRecordPayload.isRecurring` 为可空 `Boolean?`，`MemoryOperationExecutor.mergeRecord` 按三路 `when` 处理：

| AI 传值 | 结果 |
|---------|------|
| `true` | 设为 true |
| `false` | 设为 false（**允许清除生日标记**） |
| `null`（未传） | 保留原记录的值（不改变） |

> 历史版本为非空 `Boolean = false`，导致 `isRecurring` 一旦为 true 无法通过 update 清除（单向棘轮 bug），已修复。

### 写库执行链

```
DeepSeek 回复
  → LlmResponseParser.parse()
  → MainViewModel.isAiIgnoredResponse()   ← status=ignore 时此处短路，零写库
  → MainViewModel.resolveSecretaryResponse()  # LlmResponseValidator 自我纠错
  → MainViewModel.executeMemoryChanges()
  → MemoryOperationExecutor.execute()
  → MemoryRepository → MemoryDao
```

`MemoryOperationExecutor` 规则：

- **insert**：始终新建
- **update/delete**：必须能唯一定位到一条记录（id 或唯一 match）
- **clear_all**：返回 `pendingClearAll`，由 UI 确认后 `repository.clearAllRecords()`
- 删除后调用 `ReminderScheduler.cancelReminder(id)`；insert/update 后 `scheduleIfNeeded(record)`

---

## AI 自我纠错与确认轮加固

SAVE 秘书轮 **无 DB_OPS 即默认后台纠错**（不再维护「记下了/已记下」等承诺词表）。写库成功后，**App 统一生成确认文案**（`MemoryChangeConfirmationBuilder`），不播报 AI 随口说的成功措辞。

| 场景 | 检测 | 处理 |
|------|------|------|
| SAVE 轮无 JSON（非合法追问） | `needsSaveTurnWithoutOpsCorrection` | 后台补要 DB_OPS |
| SAVE 确认轮空回 | `needsSaveConfirmEmptyReplyCorrection` | 拦截套话并纠错 |
| 删除意图无 JSON | `needsDeleteWithoutOpsCorrection` | 后台补 delete ops |
| 写库/删库成功 | `MemoryChangeConfirmationBuilder` | 「好的，已经记下：{summary}。」等 |

合法追问（如「需要我现在帮您记下吗？」）允许本轮无 JSON。

### SAVE 确认轮缝合（`SaveConfirmationHelper`）

用户处于 `FollowUpContext.SAVE` 且说「是的/好的」等短确认时：

- **UI 气泡**仍显示用户原话
- **API 请求**将最后一条 user 消息缝合为：`[系统强制指令]…` + `是的，请帮我记下上文提到的：{上轮原话}`

---

## 本地兜底机制（已移除）

早期版本在 AI 未输出 DB_OPS 时，由 `MainViewModel.buildFallbackOperations()` 用本地正则补 insert/update/delete。**当前版本已移除该路径**，改由 AI + DB_OPS 自我纠错负责；`PersonCorrectionHelper` / `RecordInsertHelper` 等仍保留于代码库供测试与参考，但**不再接入主流程**。

---

## 同音字与人名消歧

### 拼音策略（`PinyinHelper` + pinyin4j）

- `toPinyinKey("夏子杭")` → `"xiazihang"`
- **全名拼音相同**才视为同音：`namesLikelySame()`
- 「涵 (han)」与「杭/航 (hang)」**不会**混为同一人

### 人名提取（`NameMatcher`）

- 从文本中提取 2~4 字中文名
- 结合「XX的生日」上下文模式
- 过滤「生日」「哪天」「停车」等常见非人名词

### 查询时的消歧（`DisambiguationHelper`）

当 `resolvePersonMatches` 返回 ≥2 条同音记录：

1. App 本地列出编号选项，TTS 播报
2. 按钮变为 **「按住 选号」**，启用 STT 短答模式
3. 用户说「第一个」「2」「是/对」→ `parseChoiceIndex()` 解析
4. 单条同音异字时问「是这位吗？」

### AI 与 App 的分工

| 场景 | 负责方 |
|------|--------|
| 改字 / 新增第二人 / 删除 | AI 输出 DB_OPS |
| 查询多条同音 → 选号 | App 本地消歧 |
| 查询回答措辞 | AI `answerQuery`（基于 App 提供的候选记录） |
| AI 未输出 DB_OPS | `LlmResponseValidator` 后台自我纠错 |
| 话语无法理解 | AI 输出 `status=ignore`，App 引导重说，零写库 |

---

## 语音识别（ASR）

主链路已切换为 **整轨录音 + 火山引擎云端识别**，不再依赖设备自带 `SpeechRecognizer` 的实时 STT。

### 录音（`AudioRecorderManager`）

- 基于 `AudioRecord`，输出标准 **WAV（PCM 16-bit / 16kHz / 单声道）**
- API：`start()` → `stop()` / `cancel()`；状态 `IDLE` / `RECORDING` / `FINISHED`
- 文件写入 `context.cacheDir`，识别结束后在 `finally` 中**必定删除**，不占长辈手机空间
- 按住 &lt; 500ms：`onHoldCancel()`，不上传网络

### 云端识别（`VolcengineAsrService`）

- 对接火山引擎 **一句话识别** API（HTTP POST，音频 Base64）
- 配置项（`local.properties` → `BuildConfig`）：`VOLC_APPID`、`VOLC_TOKEN`、`VOLC_CLUSTER`
- 实现 `AsrService` 接口，便于未来切换其他 ASR 提供商
- 识别结果从响应 `result` 字段解析为 `rawText`，再进入既有 DeepSeek 流程

### 主流程交互（微信模式）

| 手势 | 行为 |
|------|------|
| 按下 | 立即 `start()` 录音，按钮文案「松开 发送」 |
| 松开 | `stop()` → 自动上传 ASR → `onSpeechFinished(text)` |
| 误触（&lt;500ms） | 取消录音，Toast「录音时间太短」 |

### 遗留：`SpeechManager`（本机 STT）

代码库仍保留 `SpeechManager`（Android `SpeechRecognizer`、短答模式、续听合并等），供测试与备用；**当前 `MainActivity` 主流程已接入 `AudioRecorderManager`**。

短答模式（追问日期、同音选号）在旧 STT 路径下会自动收紧超时；录音 + ASR 路径下用户仍通过「按住说话」完成短答，整段音频一次性识别。

---

## 语音播报（TTS）

实现：`TtsManager`

- 多引擎回退：系统默认 TTS → 其他已安装引擎
- 强制中文 `Locale.CHINA`，优先离线中文 Voice
- `speechRate = 0.95f`，`QUEUE_FLUSH` 打断式播报
- 播报期间按住按钮可打断并开始新的录音
- Compose 侧 `speakingMessageId` 高亮正在播报的助手气泡（淡黄底 + 加粗）
- `onDone` 回调驱动 `AppPhase` 回到 `IDLE`；TTS 为 null 时 `speakAssistantText` 直接 fallback 回 IDLE，避免状态卡死

---

## 提醒通知

### 机制

- 通知渠道：`little_helper_reminders_v5`（系统默认通知铃声；可在 **通知类别 → 语音小帮手提醒** 里改为「无」或自选铃声）
- 调度：`AlarmManager.setExactAndAllowWhileIdle`
- **触发时间**（`ReminderTimeParser.resolveTriggerMillis`）：
  - 有 `event_time`（如 `14:10` 或「下午2点10分」）→ 当天该时刻
  - 仅 `formatted_date_for_alarm` / `event_date` → 当天 **8:00**（`LocalTime.of(8, 0)` 默认值）
  - 仅 `event_time` 无日期（如「每天晚上九点半」）→ 自动锚定今天
  - 支持 ISO 日期时间 `2026-06-14T14:44:00`
  - **年份动态取 `LocalDate.now().year`**，不硬编码年份；`ReminderScheduler` 不再传 `defaultYear` 参数
- **过期顺延**：若创建时当天触发时刻已过，每日循环记录会自动推迟到明天同一时刻注册闹钟。
- **到点振动**：`ReminderVibrator` 主动调用系统 Vibrator（三次短振），兼容 HyperOS 等对静音通知不振的问题
- **写入后即时调度**：insert/update 成功后 `scheduleIfNeeded(record)`；删除时 `cancelReminder(id)`
- **循环提醒**（`isRecurring=true`）：支持每日循环（如「每天晚上九点半」→ 触发后加一天）与年度循环（如生日 → 触发后加一年）并重新调度。
- 开机：`BootReceiver` → `rescheduleAll()`
- 点击通知打开 `MainActivity`，TTS 播报提醒内容
- **权限检测**：Android 12+ 自动检测并引导用户开启 `SCHEDULE_EXACT_ALARM`（精确闹钟）权限。

### 小米 / HyperOS 设置提示

安装或升级后，请到 **设置 → 通知与状态栏 → 语音小帮手 → 通知类别 → 语音小帮手提醒**，确认：

- **悬浮通知**、**振动** 已开启
- **声音** 若默认为「无」则不会响铃（可选手滴声等）；振动仍可由 App 主动触发

---

## 项目结构

```
LittleHelper/
├── app/
│   ├── src/main/java/com/littlehelper/
│   │   ├── MainActivity.kt              # 入口：权限、Compose UI、录音/TTS 绑定
│   │   ├── AppModels.kt                 # AppPhase、ChatMessage、RecordType、ImportanceLevel
│   │   ├── VoiceIntentDetector.kt       # SAVE / QUERY 意图识别
│   │   ├── viewmodel/
│   │   │   └── MainViewModel.kt         # 状态机：录音→ASR→DeepSeek→Room；对话/卡片操作
│   │   ├── data/
│   │   │   ├── MemoryRecord.kt          # Room 实体（v6：含 type、done）
│   │   │   ├── MemoryOperationModels.kt # DB_OPS JSON 模型
│   │   │   ├── AppDatabase.kt           # DB v6 + MIGRATION_5_6
│   │   │   ├── MemoryDao.kt             # 含 getAllFlow()
│   │   │   ├── MemoryRepository.kt
│   │   │   ├── MemoryOperationExecutor.kt
│   │   │   └── …（消歧、删除、拼音等 Helper）
│   │   ├── network/
│   │   │   ├── DeepSeekService.kt       # API + 系统提示词（importance/type/person 规则）
│   │   │   ├── VolcengineAsrService.kt  # 火山一句话识别
│   │   │   ├── LlmResponseParser.kt
│   │   │   └── …
│   │   ├── speech/
│   │   │   ├── AudioRecorderManager.kt  # WAV 录音（主流程）
│   │   │   └── SpeechManager.kt         # 本机 STT（遗留备用）
│   │   ├── tts/
│   │   │   └── TtsManager.kt
│   │   ├── reminder/
│   │   │   └── …
│   │   └── ui/
│   │       ├── MainScreen.kt            # Box + BottomSheetScaffold + 背景图
│   │       └── components/
│   │           ├── VoiceHoldButton.kt   # 红宝石对讲机按钮
│   │           ├── DashboardCard.kt     # 记忆卡片（标签/徽章/勾选/长按删）
│   │           └── ChatBubble.kt        # 玻璃风气泡 + 长按删
│   ├── src/main/res/
│   │   ├── drawable/background.jpg      # 全屏背景
│   │   └── values/strings.xml
│   └── src/test/java/com/littlehelper/  # 单元测试
├── docs/DATABASE.md                     # v6 Schema 详解
├── gradle/libs.versions.toml
├── local.properties.example             # DeepSeek + 火山 VOLC_* 配置示例
└── README.md
```

主界面为 **Jetpack Compose**：上半屏对话、下半屏可拖拽 Dashboard、底部悬浮对讲机按钮、右上角对话清空入口。

---

## 技术栈与依赖

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.2.10 |
| 最低 SDK | 24（Android 7.0） |
| 目标 SDK | 36 |
| UI | Jetpack Compose（Material3）、BottomSheetScaffold、玻璃风卡片 |
| 架构 | ViewModel + StateFlow + Room Flow |
| 本地存储 | Room 2.7.1 + KSP（v6） |
| 网络 | Retrofit 2.11.0 + OkHttp + Gson |
| AI | DeepSeek API（deepseek-v4-flash） |
| ASR | 火山引擎一句话识别（`VolcengineAsrService`） |
| 录音 | AudioRecord → WAV 16kHz PCM |
| 拼音 | pinyin4j 2.5.1 |
| 语音播报 | Android TextToSpeech |

---

## 开发与测试

### 单元测试（22 套件，123 用例，0 失败）

| 测试类 | 用例数 | 覆盖范围 |
|--------|--------|----------|
| `VoiceIntentDetectorTest` | 23 | SAVE/QUERY、更正、删除、跟进、FollowUpContext |
| `LlmResponseValidatorTest` | 13 | 承诺无 JSON、确认轮空回、删除空口答应 |
| `SaveConfirmationHelperTest` | 8 | 短确认识别、确认轮 API 缝合 |
| `LlmResponseParserTest` | 8 | SAVE/DELETE/DB_OPS 解析、ignore 状态、fallback 规则 |
| `NameMatcherTest` | 8 | 拼音区分涵/杭/航、同音检索 |
| `DeleteRequestHelperTest` | 7 | 删除词项构建、模糊删除检测 |
| `ReminderTimeParserTest` | 7 | event_time、8:00 默认、ISO 日期时间 |
| `DisambiguationHelperTest` | 6 | 选号提示、序数解析 |
| `MemoryIgnoreProtocolTest` | 6 | **status=ignore 全链路：零写入断言、fallback ignore 检测、isRecurring 棘轮解除** |
| `SpeechMergeTest` | 5 | STT 多段 partial 合并 |
| `MemoryRepositoryNormalizationTest` | 5 | **拼音自动填充（王纲→wanggang）、update 路径刷新拼音、非法 category 归一** |
| `ChatHistoryBuilderTest` | 4 | 过滤 partial、确认轮缝合 |
| `PersonCorrectionHelperTest` | 4 | 改字提取、**Repository 层拼音重算验证** |
| `RecordInsertHelperTest` | 3 | 新增检测 |
| `PinyinHelperTest` | 3 | 拼音 key、同音判断 |
| `MemoryRepositoryDeleteTest` | 2 | 删除词项构建 |
| `SystemTimeContextTest` | 2 | 日期时刻注入 |
| `NotificationHelperTest` | 2 | 振动模式、渠道 v5 |
| `MemoryChangeConfirmationBuilderTest` | 3 | 写库后确认文案生成 |
| `MemoryOperationExecutorClearAllTest` | 1 | clear_all 待确认 |
| `RecordListQueryHelperTest` | 2 | 列表全量查询识别 |
| `DeepSeekServiceTest` | 1 | 系统提示词结构完整性 |

**尚未覆盖**：MainViewModel 集成流程（需 Roboelectric）、SpeechManager/TtsManager 仪器化测试、Room 仪器化测试、DeepSeek API mock、UI/E2E 测试。

---

## 权限说明

| 权限 | 用途 |
|------|------|
| `RECORD_AUDIO` | 本地 WAV 录音 |
| `INTERNET` | DeepSeek API + 火山 ASR |
| `POST_NOTIFICATIONS` | 提醒通知（Android 13+） |
| `VIBRATE` | 到点提醒振动 |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | 精确闹钟 |
| `RECEIVE_BOOT_COMPLETED` | 开机恢复提醒 |

Manifest 中 `<queries>` 声明了 TTS 与 RecognitionService，以便查询设备上的语音引擎。

---

## 重构与修复历史

### 第三阶段（2026-06）：去聊天化 + 纯记事本协议化

本阶段核心目标：App 侧「绝对愚蠢」，完全依赖云端 AI 的 DB_OPS 协议，不做本地猜意图。

**架构改动：**

- 引入 `DB_OPS` 协议（`___DB_OPS_START___` / `___DB_OPS_END___`），统一 insert/update/delete/clear_all 操作
- 引入 `status` 字段：`success` / `ignore`
- `MemoryRecord` 精简到 11 个字段（去除 `item`、`keywords`、`search_text`、`reminder_scheduled` 等冗余字段）；后续 v5 新增第 12 字段 `importance_level`（见 [`docs/DATABASE.md`](docs/DATABASE.md)）
- `formatted_date_for_alarm` 解耦时间心算责任：AI 负责计算 ISO 日期，App 侧 `ReminderScheduler` 只认此字段
- 人名拼音由 App 本地 `PinyinHelper` 在入库前统一计算，不依赖 AI 提供
- 删除 `MainViewModel.buildFallbackOperations()`（本地正则猜意图）
- `MemoryChangeConfirmationBuilder` 统一生成写库成功文案

**Bug 修复（代码审计，2026-06-14）：**

| # | 严重级 | 文件 | 问题 | 修复 |
|---|--------|------|------|------|
| 1 | P0 | `ReminderScheduler.kt` | `DEFAULT_YEAR = 2026` 硬编码，2027年起闹钟全部静默跳过 | 删除常量，`buildTriggerTime` 不再传 `defaultYear`，下层动态取 `LocalDate.now().year` |
| 2 | P0 | `MainViewModel.kt` | `handleVagueDeleteRequest` 异步 DB 读取前无 PROCESSING 保护，可被连击 | 方法入口加 `_uiState.update { phase=PROCESSING }` |
| 3 | P1 | `LlmResponseParser.kt` | `resolveDbOpsStatus` 两分支都返回 `"success"`，AI 漏写 status 时 ignore 无法被识别 | else 分支改为 `"ignore"` |
| 4 | P1 | `MemoryOperationModels.kt` | `isRecurring: Boolean = false` 非空，update 无法将 true 改回 false（单向棘轮） | 改为 `Boolean? = null`，null 代表 AI 未传保留原值 |
| 5 | P1 | `MemoryOperationExecutor.kt` | `mergeRecord` 棘轮逻辑错误（化简后等价于 `existing.isRecurring` 一旦 true 永远 true） | 改为三路 `when`（true/false/null） |
| 6 | P2 | `MainViewModel.kt` | `MemoryOperation.normalizedOp()` 孤儿私有扩展函数，从未被调用 | 删除 |
| 7 | P2 | `MemoryOperationModels.kt` | `MemoryRecordPayload` 含 `location`、`placePinyin`、`personPinyin` 幽灵字段，App 侧从不读取 | 删除三个字段 |
| 8 | P2 | `DeepSeekModels.kt` | `SavePayload` 含 `location`、`personPinyin`、`placePinyin` 幽灵字段 | 删除三个字段 |

**新增单元测试（+16 用例）：**

- `MemoryIgnoreProtocolTest`（6 用例）：ignore 全链路零写入断言、fallback ignore 检测、isRecurring 棘轮解除验证
- `MemoryRepositoryNormalizationTest`（+3 用例）：王纲→wanggang 拼音、update 路径拼音刷新、无人名记录不误填拼音
- `PersonCorrectionHelperTest`（+1 用例）：验证 Repository 层保证拼音重算

**Schema 升级（2026-06-14，v4→v5）：**

- `MemoryRecord` 新增 `importance_level`（`normal` / `important` / `critical`），默认 `normal`
- `AppDatabase` 版本升至 5，`MIGRATION_4_5` 执行 `ALTER TABLE memories ADD COLUMN importance_level TEXT NOT NULL DEFAULT 'normal'`，旧数据自动补默认值
- `ImportanceLevel` 枚举 + `MemoryRepository.normalizeFields()` 归一化非法值

### 第四阶段（2026-06）：P0 长语音 + 火山 ASR + DB v6

**P0 — 录音与识别改造：**

- 新增 `AudioRecorderManager`：`AudioRecord` 输出 WAV（16kHz PCM），替代实时 STT 主链路
- 新增 `VolcengineAsrService`：对接火山引擎一句话识别；`local.properties` 配置 `VOLC_APPID` / `VOLC_TOKEN` / `VOLC_CLUSTER`
- `AppPhase` 新增 `RECORDING`、`SENDING`；交互回归「按住说话、松开发送」，移除二次点发送
- 短按 &lt; 500ms 误触过滤；临时录音文件识别后 `finally` 删除
- `onHoldEnd` 同步切至 `SENDING`，修复连击导致重复写库

**DB v6：**

- 新增 `type`（`todo` / `event` / `note` / `birthday` / `general`）、`done`（待办完成）
- `MIGRATION_5_6` 平滑升级；`RecordType.normalize()` 入库归一

### 第五阶段（2026-06）：P1 前台 UI 与交互

**Dashboard 记忆流：**

- `recordsFlow` 驱动 `LazyColumn` + `DashboardCard`：类型标签、重要等级徽章、`todo` Checkbox 本地勾选
- 长按卡片 → 确认删除单条记忆（纯本地，不经 AI）

**玻璃风 + 对讲机 UI：**

- 全透明沉浸式背景（透出手机壁纸） + 半透明玻璃卡片（聊天气泡与记忆卡片）
- `VoiceHoldButton`：红宝石拟物对讲机（渐变、金属边框、扬声器微孔、按压动画）
- `BottomSheetScaffold` 可拖拽记忆抽屉（展开 ~60% 屏高，收起 peek ~140dp，**启动默认收起**）
- 底部对讲机按钮固定悬浮，不随抽屉移动

**AI 提示词与展示：**

- `importance_level` 三级判定写入 System Prompt；解析层缺省兜底 `normal`
- `person` 缺省填「您」；禁止时间词误入 `person`
- 卡片标题去「（紧急）」后缀，改由 ❗ / ⭐ 徽章与底色表达

**对话记录管理：**

- 长按 `ChatBubble` 单条删除（含欢迎语）
- 右上角 🧹 长按 → 一键清空对话（保留欢迎语；欢迎语可手动单删后不再复活）
- 清空对话**仅改内存 messages**，与记忆数据库完全隔离；不做语音指令拦截

### 第六阶段（2026-06）：每日循环提醒修复

修复了「每天晚上九点半吃药」等**每日固定时刻循环提醒**的 4 个 P0/P1 级 Bug：

1. **无日期调度失效 (P0)**：修复 `ReminderTimeParser`，当 AI 仅下发 `event_time` 而无日期时，自动锚定今天，避免 `resolveDate` 返回 `null` 导致闹钟静默丢弃。
2. **每日循环续命 (P0)**：修复 `ReminderReceiver`，区分 `isDailyRecurring()` 与生日的年度循环。每日循环触发后通过 `plusDays(1)` 自动滚动注册明天的闹钟。
3. **过期顺延 (P1)**：修复 `ReminderScheduler`，若创建记录时当天的触发时刻已过，每日循环记录会自动推迟到明天同一时刻注册，不再静默丢弃。
4. **精确闹钟权限 (P2)**：`MainActivity` 新增 Android 12+ `SCHEDULE_EXACT_ALARM` 权限检测，未授权时弹 Toast 并跳转系统设置引导开启。
5. **AI 提示词加固**：明确指示 DeepSeek 遇到「每天/每周」必须设 `is_recurring=true`，且每日时刻提醒无需填 `formatted_date_for_alarm`。

### 第七阶段（2026-06）：全透明沉浸式桌面 UI

为了让长辈能看到更大面积的手机壁纸，同时给语音聊天区留出更宽裕的空间，对 App 进行了沉浸式透明化改造：

1. **系统级透视**：在 `themes.xml` 中引入 `android:windowShowWallpaper` 与 `android:windowIsTranslucent`，让系统壁纸直接作为 App 背景。
2. **零闪屏体验**：引入 `androidx.core:core-splashscreen`，在 `MainActivity` 启动时强行打断并移除闪屏动画（`splashScreenView.remove()`），实现点击图标瞬间透出桌面的丝滑体验。
3. **抽屉默认收起**：将 `BottomSheetScaffold` 的初始状态从 `Expanded` 改为 `PartiallyExpanded`，启动时记忆抽屉默认乖乖缩在底部，只露出拉条和首张卡片边缘。

---

## 已知限制与后续方向

### 已知限制

1. **AI 仍可能漏 JSON**：已有多层自我纠错与确认轮缝合，但极端话术下仍可能静默不写库/不删库
2. **ASR 依赖网络与火山配置**：需正确配置 `VOLC_*` 并开通一句话识别；识别失败时无法进入 DeepSeek
3. **长语音识别延迟**：整轨上传比实时 STT 多一步网络往返，弱网下等待更明显
4. **HyperOS 通知**：新通知类别默认声音可能为「无」；横幅/超级岛因系统策略可能不稳定，需用户在类别里手动开启
5. **提醒铃声**：依赖系统通知渠道发声设置；App 另备主动振动兜底
6. **对话记录不持久化**：聊天气泡仅存内存，杀进程后除欢迎语初始化逻辑外会丢失；记忆卡片在 Room 中持久保存

### 后续可改进

- [ ] Logcat 标签 `LittleHelperDB` 记录 DB_OPS 执行详情
- [ ] MainViewModel / Room 集成测试（Roboelectric）
- [ ] 可选：到点短促播放 App 内置提示音（不完全依赖系统通知铃声）

---

## 许可证

本项目为个人/家庭使用场景开发，API Key 与本地数据请自行保管，勿提交到公开仓库。
