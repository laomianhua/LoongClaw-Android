package com.littlehelper.data



import androidx.room.ColumnInfo

import androidx.room.Entity

import androidx.room.PrimaryKey

import com.littlehelper.ImportanceLevel
import com.littlehelper.MemoryCategory
import com.littlehelper.RecordType



/**

 * Room 本地记忆实体，与 AI 结构化输出字段一一对应。

 *

 * ## category 强约束

 * AI 必须且只能在以下 6 个值中选择 [category]，严禁自造：

 * - [MemoryCategory.SCHEDULE] — schedule（日程/提醒）

 * - [MemoryCategory.BIRTHDAY] — birthday（生日，[isRecurring] 应为 true）

 * - [MemoryCategory.PARKING] — parking（停车位置）

 * - [MemoryCategory.ITEM_PLACE] — item_place（物品存放）

 * - [MemoryCategory.MOOD] — mood（心情/状态）

 * - [MemoryCategory.GENERAL] — general（其他）

 */

@Entity(tableName = "memories")

data class MemoryRecord(

    /** 主键，自增。 */

    @PrimaryKey(autoGenerate = true)

    val id: Long = 0,

    /** 插入时间戳（毫秒）；由 Repository 在写入前自动填充。 */

    @ColumnInfo(name = "created_at")

    val createdAt: Long = 0,

    /** 真人语音原始文本留底。 */

    @ColumnInfo(name = "raw_text")

    val rawText: String,

    /** AI 提炼的口语化摘要，用于聊天气泡展示。 */

    val summary: String,

    /**

     * 分类枚举字符串。取值见类 Kdoc；非法值入库时归一为 general。

     */

    val category: String,

    /** 长辈口中的相对日期（如「后天」「6月8号」）。 */

    @ColumnInfo(name = "event_date")

    val eventDate: String? = null,

    /**

     * AI 心算的标准 ISO 日期 YYYY-MM-DD；[com.littlehelper.reminder.ReminderScheduler] 只认此字段。

     */

    @ColumnInfo(name = "formatted_date_for_alarm")

    val formattedDateForAlarm: String? = null,

    /** 精确时刻（24 小时制 HH:mm，如 14:30）。 */

    @ColumnInfo(name = "event_time")

    val eventTime: String? = null,

    /** 是否每年循环；birthday 类记录应为 true。 */

    @ColumnInfo(name = "is_recurring")

    val isRecurring: Boolean = false,

    /** 专项消歧：人物姓名（汉字）。 */

    val person: String? = null,

    /** 专项消歧：人名全拼（无空格小写）；写入前由 App 根据 [person] 自动填充。 */

    @ColumnInfo(name = "person_pinyin")

    val personPinyin: String? = null,

    /** 重要等级：normal / important / critical；非法值入库时归一为 normal。 */

    @ColumnInfo(name = "importance_level", defaultValue = "normal")

    val importanceLevel: String = ImportanceLevel.NORMAL.value,

    /**
     * 记录类型：todo / event / note / birthday / general；非法值入库时归一为 general。
     * - todo：待办事项（可配合 [done] 标记完成状态）
     * - event：日程/活动
     * - note：随手笔记
     * - birthday：生日纪念日
     * - general：其他通用
     */

    @ColumnInfo(name = "type", defaultValue = "general")

    val type: String = RecordType.GENERAL.value,

    /** 待办完成标记；仅对 type=todo 记录有意义，其他类型默认 false。 */

    @ColumnInfo(name = "done", defaultValue = "0")

    val done: Boolean = false

)


