package com.littlehelper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonCorrectionHelperTest {

    @Test
    fun extractTargetPersonName_fromReply() {
        val reply = "明白了，您说的是「王纲」，不是「王刚」。我重新记一下。"
        assertEquals("王纲", PersonCorrectionHelper.extractTargetPersonName(reply))
    }

    @Test
    fun extractRejectedPersonName_fromReply() {
        val reply = "您说的是「王纲」，不是「王刚」。"
        assertEquals("王刚", PersonCorrectionHelper.extractRejectedPersonName(reply))
    }

    @Test
    fun buildPersonUpdateFields_replacesNameInSummary() {
        val record = MemoryRecord(
            rawText = "王刚生日1月1日",
            summary = "王刚的生日是1月1日",
            category = "birthday",
            person = "王刚",
            eventDate = "1月1日"
        )
        val fields = PersonCorrectionHelper.buildPersonUpdateFields(record, "王纲")
        assertEquals("王纲", fields.person)
        // personPinyin is no longer set in the payload — MemoryRepository.normalizeFields
        // recomputes it from person unconditionally on every insert/update.
        assertTrue(fields.summary!!.contains("王纲"))
        assertTrue(!fields.summary!!.contains("王刚"))
    }

    @Test
    fun buildPersonUpdateFields_pinyinComputedByRepositoryNormalization() {
        val record = MemoryRecord(
            rawText = "王刚生日1月1日",
            summary = "王刚的生日是1月1日",
            category = "birthday",
            person = "王刚",
            eventDate = "1月1日"
        )
        val fields = PersonCorrectionHelper.buildPersonUpdateFields(record, "王纲")
        // Simulate what MemoryRepository.prepareForUpdate does: normalizeFields recomputes pinyin.
        val mergedRecord = record.copy(
            person = fields.person,
            summary = fields.summary ?: record.summary,
            rawText = fields.rawText ?: record.rawText
        )
        val normalized = with(MemoryRepository) { mergedRecord.prepareForUpdate() }
        assertEquals("wanggang", normalized.personPinyin)
    }
}
