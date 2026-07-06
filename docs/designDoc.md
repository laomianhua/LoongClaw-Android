📝 移动端 App 功能扩展设计文档：多层卡片抽屉堆栈与高德地图模块化集成

**产品版本：2.0**（`com.littlehelper` · `versionCode` 2）

🎯 一、 需求目标 (Project Objectives)

1\. 业务背景

本期开发属于增量迭代开发，宿主项目为已完成本地纯净版 Commit 的 “语音小帮手（我查查）” App。原 App 的核心资产（语音输入、AMA 记事本、已有数据存储、自适应红宝石图标等）必须受到绝对保护。



2\. 核心功能需求

多层卡片抽屉堆栈 (Multi-layered Bottom Sheets Stack)：



交互行为：放弃传统 Android 左侧滑菜单，采用纵向层叠卡片架构。新模块（高德地图）作为全新的一层独立卡片，平滑插入到现有“记事本抽屉”的后方。



高扩展性（无限加抽屉）：整个架构必须设计为“卡片堆栈管理器”。未来若继续扩展新应用（如股市、航班），可以像洗牌一样，无缝在后方追加新的卡片层。



默认状态：App 启动时，依然默认展示最前台的“语音小帮手”原核心卡片。 

参考效果图： 本文档同级目录内的  UIReferenceDesign.png 文件



模块化高德地图集成：



点击对应的“图层标签”或触发手势时，后方的“地图卡片”平滑动画升起/平移至前台，完整渲染 3D 矢量地图，支持标准/卫星图层切换。



运行时动态申请 GPS 权限，通过后在地图显示用户当前位置蓝点；**查具体地点时镜头停在目标 POI，查附近时以 GPS 为中心检索。**



🏗️ 二、 设计思想与架构解耦 (Design Philosophy)

为了防止第三方 SDK 的代码渗透，同时支撑“多抽屉无限扩展”的野心，本设计严守以下三大核心技术指标：



1\. 零污染与高内聚 (Zero Pollution)

高德地图 SDK 的所有 API、上下文和生命周期必须完全封装在独立的 data/map 模块内。除该具体实现类外，整个项目的其他任何 .kt 文件里，绝对不允许出现任何以 com.amap 开头的包名或关键字。



2\. 面向接口编程 (Interface-Driven)

主程序与地图模块通过统一的领域层抽象协议接口进行通信。如果未来需要将高德地图更换为百度地图或 Google Maps，只需重写一套 IMapService 的实现类，主界面 UI 代码无需做任何改动。



3\. 卡片堆栈 UI 视图层级 (UI Layer Hierarchy)

严格按照效果图的纵向 Z 轴（Z-Index）层叠架构设计：



Plaintext

\[ 顶部前景层 ]  -->  VoiceRecordCard (原语音输入与记事本交互卡片)

&#x20;      |

\[ 核心底板层 ]  -->  TabNavigationCard (带有 记事/天气/股市/航班 的磨砂玻璃标签组)

&#x20;      |

\[ 增量背景层 ]  -->  MapContainerCard (本次新增：高德地图画布卡片，层叠在后方)

&#x20;      |

\[ 未来扩展层 ]  -->  FutureAppCard... (无限追加的平行宇宙抽屉)

4\. 模块独立化目录树

请 Cursor 严格按照以下目录结构进行原子化类文件的创建，不得与原有功能文件混淆：



Plaintext

app/

└── src/main/java/com/example/app/

&#x20;   ├── domain/map/

&#x20;   │   ├── IMapService.kt       <-- 抽象地图服务接口（纯净，无SDK依赖）

&#x20;   │   └── MapType.kt           <-- 地图类型枚举

&#x20;   ├── data/map/

&#x20;   │   └── AMapServiceImpl.kt   <-- 高德SDK的具体生命周期与API实现（唯一污染区）

&#x20;   └── presentation/

&#x20;       ├── stack/

&#x20;       │   ├── CardStackManager.kt   <-- 卡片堆栈管理器（核心：控制多层抽屉的前后层叠与切换）

&#x20;       │   └── MainStackActivity.kt  <-- 宿主主Activity（管理多层磨砂玻璃卡片）

&#x20;       └── mapview/

&#x20;           └── MapCardFragment.kt    <-- 作为独立抽屉层载入的高德地图卡片

🚀 三、 领域层核心接口协议定义

在 domain/map/IMapService.kt 中定义标准抽象接口：



Kotlin

package com.example.app.domain.map



import android.content.Context

import android.view.View



interface IMapService {

&#x20;   fun initialize(context: Context, apiKey: String)

&#x20;   fun getMapView(): View

&#x20;   fun setCenterLocation(latitude: Double, longitude: Double)

&#x20;   fun switchMapType(type: MapType)

&#x20;   fun onDestroy()

}



enum class MapType { STANDARD, SATELLITE }

🏁 四、 引导式开发步骤与“卡点分步测试”逻辑 (Execution \& Testing Plan)

⚠️ 【给 Cursor 的最高核心指令】：

你必须严格按照以下 Step-by-Step 步骤依次实现。每完成一个 Step 的代码编写，你必须立刻停止输出，并提示我进行编译测试。 > 只有当我确认该步骤测试通过，并向你发出“继续下一个 Step”的指令后，你才能开始下一步的代码编写。绝对不允许跨步骤抢跑！



🛠️ Step 1: 依赖配置与安全隔离

开发任务：



在 local.properties 中安全写入高德 Key：AMAP\_API\_KEY=d6d7cb614ff40b3bf34912df23e7ee7



在 app/build.gradle.kts 中通过 BuildConfig 注入该 Key，并引入高德 3D 地图与定位 SDK 依赖。



🛑 阶段性测试逻辑：

修改完成后立刻停止。提示用户在 Android Studio 中点击 Sync Project with Gradle Files 并执行编译。确保现有项目在引入依赖后，Build Successful 且原“语音小帮手”功能不受任何干扰。



🧩 Step 2: 编写领域抽象接口 domain/map

开发任务：



建立 IMapService.kt 接口与 MapType.kt 枚举。



🛑 阶段性测试逻辑：

完成后立刻停止。静态检查：确保该步骤的代码中绝对禁止出现任何以 com.amap 开头的导包（import）。进行一次本地编译（Make Project），验证接口层架构是否纯净通过。



⚙️ Step 3: 编写高德专属实现类 data/map

开发任务：



创建 AMapServiceImpl.kt 并实现 IMapService 接口。



在该类内部集中处理高德 MapView 的生命周期映射（onCreate, onResume, onDestroy）。



动态读取 BuildConfig 中的 Key 完成高德合规隐私政策初始化。



🛑 阶段性测试逻辑：

完成后立刻停止，提示用户编译。专门验证高德 SDK 的 API 被正确调用，无编译期语法错误。



🎨 Step 4: 实现卡片堆栈架构，无损追加新抽屉图层

开发任务：



编写 CardStackManager，重构 XML 根布局，将原“语音小帮手”卡片作为 Stack 的顶部前景层。



结合效果图设计：将高德地图卡片（MapCardFragment）作为全新的抽屉图层，平滑置于原有记事本卡片的后方。点击磨砂玻璃标签组中的“天气/地图”时，触发流畅的卡片推拉/层叠置换动画。



🛑 阶段性测试逻辑：

完成后立刻停止。提示用户运行模拟器或真机！



测试多层卡片抽屉切换时的视觉连贯性，动画是否达到 60fps。



核心验收：确认切换回原“语音小帮手”卡片时，原有的语音输入、记事列表等功能 100% 完好无损，样式未发生任何位移或破坏。



📡 Step 5: 地图联调与权限防御

开发任务：



在后方的 MapCardFragment 中通过接口驱动，将地图 View 挂载到画布上。



动态申请 ACCESS\_FINE\_LOCATION 运行时权限。权限通过后，驱动地图服务刷新当前定位，激活定位蓝色小箭头。



🛑 阶段性测试逻辑：

全部完成后，进行最终的全量编译与跑测：



拒绝权限时，App 是否有优雅的提示且不闪退。



允许权限时，高德地图是否能精准定位，且在卡片切回后台或销毁时正常关闭高德定位监听。



🛑 五、 开发防线与验收标准 (Definition of Done)

代码洁癖检查：除 data/map/AMapServiceImpl.kt 之外，整个项目的其他任何原有和新写的 .kt 文件里，绝对不能出现 amap 警告和关键字。



零破坏原则：多层抽屉架构调整后，原有的所有功能、界面样式、数据存储必须 100% 正常运行。



高扩展性验证：代码中必须留有清晰的 addCardToStack() 或类似的机制，确保后续如果想要继续追加“股市卡片”、“航班卡片”，可以在不改动地图模块的情况下直接追加。



内存泄漏防护：当底层的地图卡片因切换被隐藏或销毁时，必须严格调用 IMapService.onDestroy()，释放底层纹理与定位监听，防止背景功耗和内存偷跑。

---

## 🛰️ 六、 AI 顶层意图路由协议（第九 / 第十阶段）

### 6.1 背景

上半屏聊天区与各抽屉模块**共用**；下半屏抽屉已集成高德地图。用户可能说「现在开车去机场要多久」或「看看颐和园在哪」——此类话语不应走记事写库，而应由 AI 将意图路由至地图模块。

**第十阶段原则（协议先行、App 愚蠢）：**

| 层级 | 职责 | 禁止 |
|------|------|------|
| 云端 DeepSeek | 理解 ASR、输出 `intent_route` + 标准 payload + 口语回复 | — |
| App 解析 | `LlmResponseParser`；`VoiceIntentDetector` 仅读 `FollowUpContext` | 对用户原话做汉字意图猜测 |
| App 执行 | MAP → 画线/切图层；MEMO → Room | SDK 向聊天气泡追加语义文案 |

已移除：`MapInstructionFallback`、`MapTravelMode` 字符串猜测、`VoiceIntentDetector` 内 nav/query 关键词表。

### 6.2 JSON 顶层扩展（不破坏 operations）

在既有 `___DB_OPS_START___` / `___DB_OPS_END___` 块内，**增量追加**三个顶层字段：

| 字段 | 取值 | 说明 |
|------|------|------|
| `intent_route` | `MEMO` \| `MAP` \| `WEATHER` \| `STOCK` | 模块路由；记事默认 `MEMO` |
| `action` | `VIEW_LOCATION` \| `NAVIGATE` \| `MAP_CONTROL` \| … \| `null` | 模块内动作 |
| `payload` | object \| `null` | 模块参数；地图见 `MapInstructionPayload` |

原有 `operations` 数组**保持不变**。纯地图查询时 `operations` **必须**为 `[]`。

### 6.3 地图契约（MAP）

**VIEW_LOCATION** — 搜索指定机构/地标并移动镜头（与 `POI_SEARCH` 物理隔离）：

```json
{
  "status": "success",
  "intent_route": "MAP",
  "action": "VIEW_LOCATION",
  "payload": { "keywords": "北京协和医院", "city": "北京市", "zoom_level": 16 },
  "operations": []
}
```

- App：`PoiSearch` 首条 + `MapPoiRelevance.isRelevant` 校验；乱编地名不相关则 `failureMessage` 认错
- 成功：红针 + 信息窗 + `locationAnnouncement`；**不**显示地图抽屉底部蓝卡（`distanceMeters=0`）
- 仅「路/街/号」类地址允许 `GeocodeSearch` 兜底

**MAP_CONTROL** — 当前位置 / 清图 / 附近 POI：

```json
{ "action": "MAP_CONTROL", "payload": { "query_type": "LOCATION" }, "operations": [] }
{ "action": "MAP_CONTROL", "payload": { "query_type": "CLEAR" }, "operations": [] }
{ "action": "MAP_CONTROL", "payload": { "query_type": "POI_SEARCH", "keywords": "美食" }, "operations": [] }
```

| `query_type` | 行为 |
|--------------|------|
| `LOCATION` | 蓝点跟拍 + 镜头居中 GPS |
| `CLEAR` | 清除路线/标记，恢复默认跟拍 |
| `POI_SEARCH` | GPS 半径 3km 检索；地图抽屉「附近推荐」蓝卡（`distanceMeters>0`）+ 红针 |

**NAVIGATE** — 路径规划 / 通勤时间（`origin` 默认 `CURRENT_LOCATION`，由宿主替换为 GPS）：

```json
{
  "status": "success",
  "intent_route": "MAP",
  "action": "NAVIGATE",
  "payload": {
    "origin": "CURRENT_LOCATION",
    "destination": "北京首都国际机场",
    "mode": "DRIVING",
    "query_type": "DURATION"
  },
  "operations": []
}
```

**payload 原子字段**（`MapInstructionPayload` + `MapProtocol.kt`，wire 值须与高德 SDK 对齐）：

| Key | wire 枚举 / 格式 | 高德 SDK |
|-----|------------------|----------|
| `origin` | `CURRENT_LOCATION` 或地名 | 蓝点 GPS / PoiSearch→GeocodeSearch |
| `destination` | 纯文本地名 | PoiSearch → GeocodeSearch → 坐标 |
| `mode` | `DRIVING` \| `WALKING` \| `BICYCLING` \| `TRANSIT` | `RouteSearch.calculate*RouteAsyn` |
| `query_type` | `DURATION` \| `DISTANCE` \| `ROUTE_PLAN` \| `ROUTE_DETAIL`（NAVIGATE）；`LOCATION` \| `CLEAR` \| `POI_SEARCH`（MAP_CONTROL） | 路径或地图控制 |
| `layer_type` | `STANDARD` \| `SATELLITE` | `AMap.mapType` |
| `keywords` | POI 名 / 类别 | VIEW_LOCATION 机构全名；POI_SEARCH 美食/超市等 |
| `city` | 可选 | GeocodeSearch / PoiSearch 城市限定 |
| `zoom_level` | Int（兼容 `zoomLevel`） | VIEW_LOCATION 镜头缩放 |

法定枚举：`MapRouteMode`、`MapQueryType`、`MapLayerType`；常量 `MapOrigin.CURRENT_LOCATION`。

### 6.4 宿主消费链

```
ASR 文本
  → VoiceIntentDetector（仅 FollowUpContext：QUERY → 本地检索；其余 → SAVE）
  → DeepSeek 秘书模式
  → LlmResponseParser（intent_route / action / payload）
  → MAP: pendingMapInstruction + mapExecutionToken（**不自动**切抽屉/展开）
       → LaunchedEffect → IMapService.executeInstruction
       → AMapServiceImpl: VIEW_LOCATION / NAVIGATE / MAP_CONTROL
       → consumeMapInstruction: 时长/标出/认错 TTS + MAP_VIEW_HINT
  → MEMO: executeMemoryChanges(operations)
```

**聊天气泡与 TTS（`MapTtsAuthorization` + `MapExecuteResult`）：**

- 默认：AI 口语为初始气泡；SDK 算路/查点后以 `durationAnnouncement` / `locationAnnouncement` 更新
- VIEW_LOCATION 失败：`failureMessage` → 聊天认错 + TTS，清空 `mapPoiResults`
- 唯一例外：AI 回复含 `[CALCULATING]` 时，SDK 数值替换占位符并播报

**助手追问（`AssistantFollowUpDetector`）：**

- 匹配 App 自身输出的固定追问模板（确认记下、选号消歧等），设置 `FollowUpContext`。
- **不**解析用户 ASR 文本，**不**参与 MAP/MEMO 路由。

领域类型：`domain/map/IntentRoute.kt`、`MapProtocol.kt`、`MapInstructionPayload.kt`、`MapTtsAuthorization.kt`；实现：`data/map/AMapServiceImpl.executeInstruction`。

### 6.5 Compose 体验加固

| 项 | 实现 |
|----|------|
| 地图省电 | `MapCard`：`DisposableEffect(currentCard)` — 非 MAP 标签立即 `onPause` + `stopMyLocation` |
| 镜头跟拍 | `setAutoCenterOnUserEnabled`：VIEW_LOCATION 用 `LOCATION_TYPE_SHOW`（蓝点不抢镜头）；LOCATION/POI_SEARCH 用 `LOCATION_TYPE_LOCATION_ROTATE` |
| 查点重绘 | `LaunchedEffect(isMapVisible, poiResults)`：打开地图抽屉时 `displayPoiMarkers` + `animateCamera` 到目标 |
| 附近推荐 UI | `NearbyPoiPanel` 仅 `distanceMeters>0` 显示；底部 `padding(124.dp)` 避开红宝石语音按钮 |
| 手势隔离 | `sheetSwipeEnabled=false`；拖柄+标签栏独占纵向拖拽；`AMapServiceImpl` 对 MapView 设置 `requestDisallowInterceptTouchEvent(true)` |

### 6.6 秘书 System Prompt 规则

1. 中枢调度：路线/时间/距离/导航/查看位置 → `intent_route="MAP"`
2. 纯地图不写库：`operations=[]`
3. 记事向下兼容：`intent_route="MEMO"`，`operations` 照旧 insert/update/delete
4. **Few-Shot 在云端**：开车/地铁/公交/步行/卫星图/未来时段等常见口语示例写入 `SECRETARY_SYSTEM_PROMPT`，App 本地**零**对照表
5. **payload 必须用 wire 枚举**：`mode`=`DRIVING|WALKING|BICYCLING|TRANSIT`；`query_type`=`DURATION|DISTANCE|ROUTE_PLAN|ROUTE_DETAIL`；`layer_type`=`STANDARD|SATELLITE`
6. **SDK 实时时长**：算路成功后 `MapExecuteResult.durationAnnouncement` 更新聊天气泡并 TTS
7. **`ROUTE_DETAIL`**：换乘文本由 `BusTransitDetailExtractor` 追加至聊天气泡（非抽屉面板）
8. **画线前 `aMap.clear()`**：防止多轮路线叠加
9. **VIEW_LOCATION vs POI_SEARCH**：Prompt 铁律 + App `MapPoiRelevance` 双保险；认错话术在聊天区，非地图蓝卡

### 6.7 验收

- `./gradlew testDebugUnitTest assembleDebug` 全绿（含 `MapPoiRelevanceTest`）
- `LlmResponseParserTest.parse_mapRoute_withoutOperations_isSuccess`
- `MapProtocolTest`、`MapTtsAuthorizationTest`、`TodoProtocolParserTest`
- 真机：「开车去机场要多久」→ 聊天气泡时长 + 手动打开地图见路线
- 真机：「北京协和医院在哪里」→ 打开地图见红针在目标处，镜头不跳回 GPS
- 真机：「冥王星医院在哪里」→ 聊天认错，不标天王星
- 真机：「附近有什么好吃的」→ 地图「附近推荐」蓝卡在红宝石按钮上方可读

### 6.8 第十阶段：协议先行大清洗（摘要）

| 变更 | 说明 |
|------|------|
| 删除 `MapInstructionFallback` | 禁止本地从原话拼 MAP JSON |
| 删除 `MapTravelMode` | 合并为 `MapProtocol.kt` |
| 瘦身 `VoiceIntentDetector` | 仅 `FollowUpContext` → SAVE/QUERY |
| 新增 `AssistantFollowUpDetector` | 助手模板 → UI 跟进状态 |
| 新增 `MapTtsAuthorization` | SDK 静默执行 + `[CALCULATING]` 例外 |
| `AMapServiceImpl` 加固 | GeocodeSearch 链、四种出行模式、8s 超时配合 |
| `MainViewModel` | 聊天优先：不自动展开地图抽屉；`consumeMapInstruction` 合并 SDK 播报 |

详见 [`README.zh.md`](../README.zh.md) 第十～十三阶段与 [AI 意图路由协议](../README.zh.md#ai-意图路由协议) 章节。

---

## 🗺️ 八、 VIEW_LOCATION 查地名加固（第十三阶段，2026-06）

### 8.1 问题背景（真机反馈）

| 现象 | 根因 |
|------|------|
| 说已标出协和医院，地图却在 GPS 附近、无红针 | 打开地图时 `LOCATION_TYPE_LOCATION_ROTATE` 持续跟拍 GPS，覆盖 `animateCamera` |
| 「冥王星医院」标成「天王星商务大厦」 | 高德 `PoiSearch` 返回模糊首条，App 未校验相关性 |
| 蓝色 POI 卡被红宝石按钮挡住 | `NearbyPoiPanel` 与 `VoiceHoldButton` 同屏层叠；VIEW_LOCATION 不应显示该卡 |

### 8.2 设计决策

```
用户问「XX在哪里」
  → AI: VIEW_LOCATION（keywords=XX）
  → AMapServiceImpl.drawViewLocation
       → PoiSearch 首条
       → MapPoiRelevance.isRelevant(keywords, poi.title) ?
            ├─ 否 → failureMessage（聊天认错）
            └─ 是 → setAutoCenterOnUserEnabled(false)
                    → displayPoiMarkers（红针 + 飞到目标）
                    → locationAnnouncement
  → 用户手动打开地图标签
       → MapCard: LaunchedEffect 再次 displayPoiMarkers + SHOW 模式蓝点
```

**与 POI_SEARCH 的 UI 分工：**

| 协议 | `distanceMeters` | 地图抽屉蓝卡 | 镜头 |
|------|------------------|--------------|------|
| VIEW_LOCATION | 0 | 不显示 | 停在目标 POI |
| POI_SEARCH | >0 | 「附近推荐」列表 | 以 GPS 为中心 fitBounds |

### 8.3 关键文件

| 文件 | 职责 |
|------|------|
| `domain/map/MapPoiRelevance.kt` | 关键词与 POI 标题相关性（单元测试覆盖） |
| `domain/map/MapExecuteResult.kt` | `failureMessage` / `locationAnnouncement` |
| `data/map/AMapServiceImpl.kt` | `drawViewLocation`、`applyMyLocationStyle`、`setAutoCenterOnUserEnabled` |
| `presentation/mapview/MapCard.kt` | `viewingNamedPlace`、`showNearbyPanel`、`LaunchedEffect` 重绘 |
| `viewmodel/MainViewModel.kt` | `consumeMapInstruction` 失败/成功 TTS |
| `network/DeepSeekService.kt` | VIEW_LOCATION / POI_SEARCH 铁律 Few-Shot |

### 8.4 默认回复文案（中性通用）

| 场景 | 文案 |
|------|------|
| ignore / 无法理解 | 没太听明白，请再说清楚一点。 |
| VIEW_LOCATION 失败 | 地图上没有找到「{keyword}」，请换个说法再试。 |
| 地图通用失败 | 地图查询未成功，请换个说法再试。 |
| 网络超时 | 网络不太稳定，请稍后再试。 |

**产品文案铁律**：系统默认回复面向全体普通用户，禁止使用「长辈」「老年」「中老年」等人群限定称呼。

### 8.5 验收

- 单元：`MapPoiRelevanceTest`（协和医院命中；冥王星→天王星拒绝）
- 真机：查远方医院 → 红针在目标、手动拖地图不跳回 GPS
- 真机：乱编地名 → 聊天认错，地图无错标
- 真机：附近美食 → 蓝卡完整可见

---

## 📋 七、记事本待办消歧协议（第十一阶段）

### 7.1 设计原则

用户「事后复报」（「吃过药了」「快递拿回来了」）由 **云端判断 + 端侧盲执行** 完成，禁止 App 本地猜测待办 ID。

### 7.2 协议字段

| 字段 | 取值 | 说明 |
|------|------|------|
| `intent_route` | `NOTEBOOK` / `MEMO` | 记事本模块 |
| `action` | `QUERY_TODO` / `UPDATE_TODO_STATUS` | 待办专用 action |
| `payload.query_keyword` | String | QUERY 时模糊检索关键字 |
| `payload.todo_id` | Long | UPDATE 时 Room 主键（优先） |
| `payload.todo_keyword` | String | UPDATE 时按摘要匹配 |
| `payload.status` | `COMPLETED` | 标记 `done=true` |
| `operations` | `[]` | 待办状态变更禁止走 insert/update/delete |

### 7.3 三阶段闭环（App 端）

```
用户复报 → AI: QUERY_TODO(keyword)
  → MemoryDao.searchIncompleteTodos (type=todo, done=0)
  → 0 条：TTS「没找到…」
  → 1 条：静默二次 AI → UPDATE_TODO_STATUS
  → 多条：TTS 追问 + FollowUpContext.TODO_DISAMBIGUATION + 注入 [{id,title}] JSON
用户补充 → AI: UPDATE_TODO_STATUS(todo_id) → Room done=true → Dashboard 删除线
```

**v2.0 快捷路径（提醒上下文）：** 用户从通知进入且 2h 内说完成类口语 → 跳过消歧，直接勾选 `pendingReminderTodoId` 对应待办。

### 7.4 关键文件

- `domain/todo/TodoProtocol.kt`
- `data/todo/TodoContextBuilder.kt`
- `MainViewModel.handleQueryTodoAction` / `handleUpdateTodoStatusAction`
- `DeepSeekService.SECRETARY_SYSTEM_PROMPT`【待办消歧铁律】

### 7.5 验收

- `./gradlew testDebugUnitTest` 全绿（含 `TodoProtocolParserTest`）
- 真机：记两条「吃药」待办 → 「吃过药了」→ 追问 → 「早上的那个」→ 对应卡片划删除线
- 真机：点早 8 点吃药提醒进 App → 「已经吃完了」→ 对应待办直接勾选，不误列两条消歧

---

## 🚀 九、 v2.0 发布里程碑（2026-06）

### 9.1 版本定义

| 字段 | v1.0 | v2.0 |
|------|------|------|
| `versionName` | 1.0 | **2.0** |
| `versionCode` | 1 | **2** |
| 核心范围 | 语音记事 + 提醒 + 待办勾选 | + 高德地图全模块 + 协议清洗 + 提醒待办联动 |

### 9.2 v2.0 交付清单

1. **地图模块**：`VIEW_LOCATION` / `MAP_CONTROL` / `NAVIGATE`；`MapPoiRelevance`；镜头 `LOCATION_TYPE_SHOW` 跟拍修复
2. **交互**：第十二阶段「聊天优先」；附近推荐蓝卡；`MAP_VIEW_HINT` 手动开地图
3. **待办**：第十一阶段三阶段消歧 + **第十四阶段提醒 `recordId` 绑定**（`TodoCompletionHelper`）
4. **文案**：中性通用默认回复；禁止人群限定称呼（§8.4）
5. **工程**：`app/build/` gitignore；28 套件 / 128 单元测试

### 9.3 提醒待办联动（第十四阶段）

```
通知点击 → MainActivity 传入 EXTRA_RECORD_ID
  → MainViewModel.bindPendingReminderTodo(recordId)
  → 用户说「吃完了」等（TodoCompletionHelper）
  → completeTodoRecord(done=true) + 记事本 Flow 刷新
```

| 文件 | 职责 |
|------|------|
| `MainActivity.handleReminderIntent` | 传递 `recordId` + `message` |
| `MainViewModel` | `pendingReminderTodoId`、2h 窗口、`tryCompleteActiveReminderTodo` |
| `data/todo/TodoCompletionHelper.kt` | 完成类口语正则 |
| `DeepSeekService` | 提醒上下文下禁止 QUERY_TODO 消歧 |

详见 [`README.zh.md`](../README.zh.md) [v2.0 版本摘要](../README.zh.md#v20-版本摘要) 与第十四阶段章节。
