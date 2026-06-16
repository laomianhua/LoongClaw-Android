# 数据库 Schema 定义

本应用使用 Room 数据库存储本地笔记数据，数据库名称为 `little_helper.db`，表名为 `memories`，实体类为 `MemoryRecord`。

## 字段列表

| # | Kotlin 属性 | SQLite 列名 | 类型 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | `id` | `id` | Long | 主键，自增 |
| 2 | `createdAt` | `created_at` | Long | 插入时间戳 (ms)，写入时由 Repository 自动填充 |
| 3 | `rawText` | `raw_text` | String | 语音转文字的原始内容 |
| 4 | `summary` | `summary` | String | AI 提炼的口语化摘要，用于界面展示 |
| 5 | `category` | `category` | String | 分类：`schedule` / `birthday` / `parking` / `item_place` / `mood` / `general` |
| 6 | `eventDate` | `event_date` | String? | 口语相对日期（如「后天」「6月8号」） |
| 7 | `formattedDateForAlarm` | `formatted_date_for_alarm` | String? | 标准 ISO 日期 `YYYY-MM-DD`，闹钟调度只认此字段 |
| 8 | `eventTime` | `event_time` | String? | 精确时刻，24 小时制 `HH:mm`（如 `14:30`） |
| 9 | `isRecurring` | `is_recurring` | Boolean | 是否每年循环（生日类一般为 `true`） |
| 10 | `person` | `person` | String? | 人物姓名（汉字），用于消歧 |
| 11 | `personPinyin` | `person_pinyin` | String? | 人名全拼（小写无空格），写入前由 App 根据 `person` 自动计算 |
| 12 | `importanceLevel` | `importance_level` | String | 重要等级：`normal` / `important` / `critical`，默认 `normal` |
| 13 | `type` | `type` | String | 记录类型：`todo` / `event` / `note` / `birthday` / `general`，默认 `general` |
| 14 | `done` | `done` | Boolean | 待办完成标记，仅 `type=todo` 时有意义，默认 `false`（SQLite 存 `0`/`1`） |

## 补充说明

- **数据库版本**：v6（`AppDatabase`）
- **迁移策略**：`MIGRATION_4_5` + `MIGRATION_5_6`（均使用 `ALTER TABLE ADD COLUMN` 平滑迁移，不丢数据）；未覆盖的版本跳跃仍走 `fallbackToDestructiveMigration`
- **未落库字段**：AI 协议中的 `tags` 仅用于 LLM 交互，不写入 SQLite；`location`、`placePinyin` 等已在重构中移除
- **自动填充**：`createdAt` 和 `personPinyin` 由 `MemoryRepository.normalizeFields()` 在 insert/update 前自动处理

---

## 规划：重要等级字段（v4 → v5）

### 设计原则

与现有 `category` 字段对齐：**SQLite 存字符串枚举**，Kotlin 侧用 `ImportanceLevel` 枚举 + `normalize()` 归一化，AI 协议 `MemoryRecordPayload` 增加可选字段 `importance_level`。

适老化场景下等级不宜过多，建议 **三档**，口语好理解、排序也简单。

### v4→v5 新增字段

| # | Kotlin 属性 | SQLite 列名 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 12 | `importanceLevel` | `importance_level` | String | `normal` | 重要等级，见下表 |

### v5→v6 新增字段

| # | Kotlin 属性 | SQLite 列名 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 13 | `type` | `type` | String | `general` | 记录类型：`todo` / `event` / `note` / `birthday` / `general` |
| 14 | `done` | `done` | Boolean | `false`（`0`） | 待办完成标记，仅 `type=todo` 时有意义 |

### RecordType 枚举取值

| 值 | 含义 |
| :--- | :--- |
| `todo` | 待办事项（可配合 `done` 标记完成状态） |
| `event` | 日程/活动 |
| `note` | 随手笔记 |
| `birthday` | 生日纪念日 |
| `general` | 通用/其他（默认） |

### ImportanceLevel 枚举取值及 AI 判定逻辑

| 值 | 含义 | AI 判定逻辑（Prompt 约束） |
| :--- | :--- | :--- |
| `normal` | 普通（默认） | 日常琐碎、普通聊天、流水账、无强时效性的记录 |
| `important` | 重要 | 家人生日、涉及金额较大的财务、长期的重要维护，或用户在语气中明确强调了“很重要/千万别忘”的逻辑 |
| `critical` | 紧急/特别重要 | 仅用于健康医疗（吃药/看病）、涉及具体时间的绝对核心截止事务、或不立刻做会有严重后果的事 |

非法或空值入库时归一为 `normal`（与 `category` → `general` 的处理方式一致）。

### Kotlin 侧草案

```kotlin
enum class ImportanceLevel(val value: String) {
    NORMAL("normal"),
    IMPORTANT("important"),
    CRITICAL("critical");

    companion object {
        fun fromValue(value: String?): ImportanceLevel =
            entries.firstOrNull { it.value == value?.trim() } ?: NORMAL

        fun normalize(value: String?): String = fromValue(value).value
    }
}
```

`MemoryRecord` 新增：

```kotlin
@ColumnInfo(name = "importance_level", defaultValue = "normal")
val importanceLevel: String = ImportanceLevel.NORMAL.value
```

`MemoryRepository.normalizeFields()` 中追加：

```kotlin
importanceLevel = ImportanceLevel.normalize(importanceLevel)
```

### AI 协议（DB_OPS）扩展

`MemoryRecordPayload` / `fields` 中增加可选字段：

```json
"importance_level": "important"
```

System Prompt 补充：用户明确强调重要性时填写；未提及则省略（App 落库为 `normal`）。**禁止**自造 `urgent`、`high` 等值。

### SQL 迁移（v4 → v5）

SQLite 支持 `ALTER TABLE ... ADD COLUMN`，旧行自动获得默认值：

```sql
ALTER TABLE memories
ADD COLUMN importance_level TEXT NOT NULL DEFAULT 'normal';
```

可选：为按重要等级检索/排序加索引（记录量不大时可省略）：

```sql
CREATE INDEX IF NOT EXISTS index_memories_importance_level
ON memories (importance_level);
```

### Room Migration 写法

将 `AppDatabase` 版本升至 **5**，并**替换**当前的 `fallbackToDestructiveMigration`，改为显式迁移以保留用户数据：

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE memories
            ADD COLUMN importance_level TEXT NOT NULL DEFAULT 'normal'
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_memories_importance_level
            ON memories (importance_level)
            """.trimIndent()
        )
    }
}

// Room.databaseBuilder(...)
//     .addMigrations(MIGRATION_4_5)
//     .build()
```

### 迁移验证清单

1. 升级前插入若干条记录，覆盖有/无 `person`、多种 `category`。
2. 安装新版本后确认 `SELECT id, summary, importance_level FROM memories` 全部为 `normal`。
3. 新 insert / update 带 `importance_level: "important"` 能正确落库。
4. 降级场景：若曾发布 v5，**不要**回退到 v4（Room 不支持自动降级）；测试用清数据或重装。

### 后续可联动（非本次 Schema 范围）

- 提醒调度：`critical` 可优先占用通知渠道或重复提醒（需改 `ReminderScheduler`）。
- 列表展示：气泡角标或排序「紧急 → 重要 → 普通」。
- 查询：`ORDER BY CASE importance_level WHEN 'critical' THEN 0 WHEN 'important' THEN 1 ELSE 2 END`。
