package com.littlehelper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisambiguationHelperTest {

    private fun birthdayRecord(id: Long, person: String, date: String): MemoryRecord {
        return MemoryRecord(
            id = id,
            rawText = "$person 生日 $date",
            summary = "$person 生日 $date",
            category = "birthday",
            person = person,
            personPinyin = PinyinHelper.toPinyinKey(person),
            eventDate = date
        )
    }

    @Test
    fun buildAllRecordsAnswer_listsNumberedRecords() {
        val records = listOf(
            birthdayRecord(1, "王刚", "6月8号"),
            MemoryRecord(
                id = 2,
                rawText = "明天上午10点与八达通联系",
                summary = "明天上午10点与八达通联系",
                category = "schedule"
            )
        )
        val answer = DisambiguationHelper.buildAllRecordsAnswer(records)
        assertTrue(answer.contains("您的记事本里共有 2 条记录"))
        assertTrue(answer.contains("1. 王刚，生日6月8号"))
        assertTrue(answer.contains("2. 明天上午10点与八达通联系"))
    }

    @Test
    fun buildAllRecordsAnswer_empty() {
        assertTrue(DisambiguationHelper.buildAllRecordsAnswer(emptyList()).contains("目前还没有任何记录"))
    }

    @Test
    fun buildChoicePrompt_listsNumberedOptions() {
        val records = listOf(
            birthdayRecord(1, "夏子杭", "6月8号"),
            birthdayRecord(2, "夏子航", "3月15号")
        )
        val prompt = DisambiguationHelper.buildChoicePrompt(records, "夏子航的生日是哪天")
        assertTrue(prompt.contains("请问是第几个"))
        assertTrue(prompt.contains("1. 夏子杭，生日6月8号"))
        assertTrue(prompt.contains("2. 夏子航，生日3月15号"))
    }

    @Test
    fun buildChoicePrompt_supportsSingleHomophoneRecord() {
        val records = listOf(birthdayRecord(1, "王纲", "1月1日"))
        val prompt = DisambiguationHelper.buildChoicePrompt(records, "王刚的生日是几号")
        assertTrue(prompt.contains("1. 王纲，生日1月1日"))
        assertTrue(prompt.contains("是这位吗"))
    }

    @Test
    fun parseChoiceIndex_acceptsDigitsAndOrdinals() {
        assertEquals(1, DisambiguationHelper.parseChoiceIndex("2", 3))
        assertEquals(1, DisambiguationHelper.parseChoiceIndex("第二个", 3))
        assertEquals(0, DisambiguationHelper.parseChoiceIndex("第一个", 3))
        assertEquals(0, DisambiguationHelper.parseChoiceIndex("是", 1))
        assertNull(DisambiguationHelper.parseChoiceIndex("第四个", 3))
    }

    @Test
    fun answerForRecord_usesStoredPersonName() {
        val record = birthdayRecord(1, "夏子杭", "6月8号")
        val answer = DisambiguationHelper.answerForRecord(record, "夏子航的生日是哪天")
        assertEquals("夏子杭的生日是6月8号。", answer)
    }
}
