package com.littlehelper.data

import com.littlehelper.MemoryCategory

object RecordInsertHelper {
    private val newRecordMarkers = listOf("新增", "再加", "添加一条", "再记一条", "新建", "增加一条", "多记一条")

    fun userRequestsNewRecord(text: String): Boolean {
        return newRecordMarkers.any { marker -> text.contains(marker) }
    }

    fun buildInsertOperation(userText: String): MemoryOperation? {
        val person = NameMatcher.extractPersonNames(userText)
            .map { stripPersonPrefix(it) }
            .firstOrNull { it.length >= 2 }
            ?: return null
        if (!userText.contains("生日") && !userRequestsNewRecord(userText)) return null

        val eventDate = extractEventDate(userText)
        val summary = when {
            userText.contains("生日") && eventDate != null -> "${person}的生日是$eventDate"
            userText.contains("生日") -> "${person}的生日"
            else -> userText.trim().take(80)
        }

        return MemoryOperation(
            op = "insert",
            record = MemoryRecordPayload(
                summary = summary,
                rawText = userText,
                person = person,
                category = MemoryCategory.BIRTHDAY.value,
                eventDate = eventDate,
                tags = listOf(person, "生日"),
                isRecurring = true
            )
        )
    }

    fun isExactDuplicate(records: List<MemoryRecord>, person: String, eventDate: String?): Boolean {
        return records.any { record ->
            val storedPerson = record.person?.takeIf { it.isNotBlank() }
                ?: NameMatcher.extractPersonNames(record.summary).firstOrNull()
            storedPerson == person && (
                eventDate.isNullOrBlank() ||
                    record.eventDate == eventDate ||
                    normalizeDate(record.eventDate) == normalizeDate(eventDate)
                )
        }
    }

    private fun stripPersonPrefix(name: String): String {
        return name.replace(Regex("^(一条|一个|一位|这个|那位|这名)"), "")
    }

    private fun extractEventDate(text: String): String? {
        return Regex("""(\d{1,2}月\d{1,2}[日号]?)""").find(text)?.groupValues?.get(1)
            ?: Regex("""(\d{1,2}号)""").find(text)?.groupValues?.get(1)
    }

    private fun normalizeDate(date: String?): String? {
        return date?.replace("日", "号")?.trim()
    }
}
