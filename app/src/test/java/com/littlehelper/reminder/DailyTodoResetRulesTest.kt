package com.littlehelper.reminder

import com.littlehelper.MemoryCategory
import com.littlehelper.RecordType
import com.littlehelper.data.MemoryRecord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyTodoResetRulesTest {

    @Test
    fun isDailyRecurringTodo_matchesDailyMedicationTodo() {
        val record = MemoryRecord(
            rawText = "每天晚上九点半吃降脂药",
            summary = "每天晚上九点半吃降脂药",
            category = MemoryCategory.SCHEDULE.value,
            type = RecordType.TODO.value,
            eventTime = "21:30",
            isRecurring = true,
            done = true
        )
        assertTrue(DailyTodoResetRules.isDailyRecurringTodo(record))
    }

    @Test
    fun isDailyRecurringTodo_rejectsOneTimeTodo() {
        val record = MemoryRecord(
            rawText = "明天买菜",
            summary = "明天买菜",
            category = MemoryCategory.SCHEDULE.value,
            type = RecordType.TODO.value,
            isRecurring = false
        )
        assertFalse(DailyTodoResetRules.isDailyRecurringTodo(record))
    }

    @Test
    fun isDailyRecurringTodo_rejectsBirthdayRecurring() {
        val record = MemoryRecord(
            rawText = "王刚生日",
            summary = "王刚生日",
            category = MemoryCategory.BIRTHDAY.value,
            type = RecordType.TODO.value,
            eventTime = "08:00",
            isRecurring = true
        )
        assertFalse(DailyTodoResetRules.isDailyRecurringTodo(record))
    }

    @Test
    fun isDailyRecurringTodo_rejectsNonTodo() {
        val record = MemoryRecord(
            rawText = "车停在华亭嘉园",
            summary = "车停在华亭嘉园",
            category = MemoryCategory.PARKING.value,
            type = RecordType.NOTE.value,
            eventTime = "21:30",
            isRecurring = true
        )
        assertFalse(DailyTodoResetRules.isDailyRecurringTodo(record))
    }
}
