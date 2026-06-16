package com.littlehelper.reminder

import com.littlehelper.MemoryCategory
import com.littlehelper.RecordType
import com.littlehelper.data.MemoryRecord

/** 每日 0 点待办打勾重置的适用范围判定。 */
object DailyTodoResetRules {
    /**
     * 同时满足：
     * - type = todo（待办）
     * - is_recurring = true（循环提醒）
     * - 有 event_time（每日固定时刻，非生日类年度循环）
     */
    fun isDailyRecurringTodo(record: MemoryRecord): Boolean =
        record.type == RecordType.TODO.value &&
            record.isRecurring &&
            !record.eventTime.isNullOrBlank() &&
            record.category != MemoryCategory.BIRTHDAY.value
}
