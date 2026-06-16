package com.littlehelper.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderTimeParserTest {

    @Test
    fun parseEventTime_supports24HourClock() {
        assertEquals(LocalTime.of(14, 10), ReminderTimeParser.parseEventTime("14:10"))
    }

    @Test
    fun parseEventTime_supportsChineseMeridiem() {
        assertEquals(LocalTime.of(14, 10), ReminderTimeParser.parseEventTime("下午2点10分"))
    }

    @Test
    fun resolveTriggerMillis_usesEventTimeOnSameDay() {
        val millis = ReminderTimeParser.resolveTriggerMillis(
            formattedDateForAlarm = "2026-06-14",
            eventDate = null,
            eventTime = "14:10",
            defaultYear = 2026
        )
        assertNotNull(millis)
        val parsed = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(millis!!),
            ZoneId.systemDefault()
        )
        assertEquals(LocalDate.of(2026, 6, 14), parsed.toLocalDate())
        assertEquals(LocalTime.of(14, 10), parsed.toLocalTime())
    }

    @Test
    fun resolveTriggerMillis_defaultsToEightAmWithoutEventTime() {
        val millis = ReminderTimeParser.resolveTriggerMillis(
            formattedDateForAlarm = "2026-06-14",
            eventDate = null,
            eventTime = null,
            defaultYear = 2026
        )
        assertNotNull(millis)
        val parsed = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(millis!!),
            ZoneId.systemDefault()
        )
        assertEquals(LocalTime.of(8, 0), parsed.toLocalTime())
    }

    @Test
    fun resolveTriggerMillis_parsesIsoDateTimeInFormattedField() {
        val millis = ReminderTimeParser.resolveTriggerMillis(
            formattedDateForAlarm = "2026-06-14T14:44:00",
            eventDate = null,
            eventTime = null,
            defaultYear = 2026
        )
        assertNotNull(millis)
        val parsed = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(millis!!),
            ZoneId.systemDefault()
        )
        assertEquals(LocalTime.of(14, 44), parsed.toLocalTime())
    }

    @Test
    fun parseEventTime_returnsNullForBlank() {
        assertNull(ReminderTimeParser.parseEventTime(null))
        assertNull(ReminderTimeParser.parseEventTime("  "))
    }

    @Test
    fun resolveTriggerMillis_returnsNullWhenNeitherDateNorTime() {
        // 既无日期也无时刻 → null（无法调度）
        assertNull(
            ReminderTimeParser.resolveTriggerMillis(
                formattedDateForAlarm = null,
                eventDate = null,
                eventTime = null
            )
        )
    }

    @Test
    fun resolveTriggerMillis_usesTodayWhenOnlyEventTimePresent() {
        // 无日期 + 有 event_time → 锚定今天（用于「每天晚上九点半」等每日循环）
        val millis = ReminderTimeParser.resolveTriggerMillis(
            formattedDateForAlarm = null,
            eventDate = null,
            eventTime = "21:30"
        )
        assertNotNull(millis)
        val parsed = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(millis!!),
            ZoneId.systemDefault()
        )
        assertEquals(LocalDate.now(), parsed.toLocalDate())
        assertEquals(LocalTime.of(21, 30), parsed.toLocalTime())
    }

    @Test
    fun resolveTriggerMillis_returnsNullWhenOnlyInvalidTime() {
        // 无法解析的时刻 → null
        assertNull(
            ReminderTimeParser.resolveTriggerMillis(
                formattedDateForAlarm = null,
                eventDate = null,
                eventTime = "不是时间"
            )
        )
    }
}
