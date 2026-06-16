package com.littlehelper.reminder

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 从记录字段解析提醒触发时刻（纯逻辑，便于单元测试）。 */
object ReminderTimeParser {
    private val clockPattern = Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?$""")
    private val chineseMeridiemPattern =
        Regex("""(上午|下午|中午|晚上)?(\d{1,2})点(?:(\d{1,2})分)?""")
    private val chineseMonthDayPattern = Regex("""(\d{1,2})月(\d{1,2})[日号]?""")

    fun resolveTriggerMillis(
        formattedDateForAlarm: String?,
        eventDate: String?,
        eventTime: String?,
        defaultYear: Int = LocalDate.now().year
    ): Long? {
        val resolvedDatePair = resolveDate(formattedDateForAlarm, eventDate, defaultYear)

        if (resolvedDatePair != null) {
            val (date, dateTimeFromIso) = resolvedDatePair
            val time = parseEventTime(eventTime) ?: dateTimeFromIso ?: LocalTime.of(8, 0)
            val dateTime = LocalDateTime.of(date, time)
            return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        // 没有明确日期，但有 event_time（例如「每天晚上九点半」）→ 锚定今天。
        // ReminderScheduler 会在发现时刻已过时自动推到明天。
        val time = parseEventTime(eventTime) ?: return null
        val dateTime = LocalDateTime.of(LocalDate.now(), time)
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun parseEventTime(text: String?): LocalTime? {
        val normalized = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        clockPattern.matchEntire(normalized)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            return safeLocalTime(hour, minute)
        }

        chineseMeridiemPattern.find(normalized)?.let { match ->
            val meridiem = match.groupValues[1]
            val hour = match.groupValues[2].toIntOrNull() ?: return null
            val minute = match.groupValues[3].toIntOrNull() ?: 0
            val hour24 = to24Hour(hour, meridiem)
            return safeLocalTime(hour24, minute)
        }

        return null
    }

    private fun resolveDate(
        formattedDateForAlarm: String?,
        eventDate: String?,
        defaultYear: Int
    ): Pair<LocalDate, LocalTime?>? {
        formattedDateForAlarm?.trim()?.takeIf { it.isNotEmpty() }?.let { iso ->
            parseIsoDateTime(iso)?.let { return it }
            parseIsoDate(iso)?.let { return it to null }
        }
        eventDate?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            parseIsoDate(raw)?.let { return it to null }
            parseChineseDate(raw, defaultYear)?.let { return it to null }
        }
        return null
    }

    private fun parseIsoDateTime(value: String): Pair<LocalDate, LocalTime>? {
        return try {
            val dateTime = LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            dateTime.toLocalDate() to dateTime.toLocalTime()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseIsoDate(value: String): LocalDate? {
        return try {
            LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseChineseDate(text: String, defaultYear: Int): LocalDate? {
        val match = chineseMonthDayPattern.find(text) ?: return null
        val month = match.groupValues[1].toIntOrNull() ?: return null
        val day = match.groupValues[2].toIntOrNull() ?: return null
        return try {
            LocalDate.of(defaultYear, month, day)
        } catch (_: Exception) {
            null
        }
    }

    private fun to24Hour(hour: Int, meridiem: String): Int {
        return when {
            meridiem.contains("下午") && hour in 1..11 -> hour + 12
            meridiem.contains("晚上") && hour in 1..11 -> hour + 12
            meridiem.contains("中午") && hour == 12 -> 12
            meridiem.contains("中午") && hour in 1..11 -> hour + 12
            meridiem.contains("上午") && hour == 12 -> 0
            else -> hour
        }
    }

    private fun safeLocalTime(hour: Int, minute: Int): LocalTime? {
        return try {
            LocalTime.of(hour, minute)
        } catch (_: Exception) {
            null
        }
    }
}
