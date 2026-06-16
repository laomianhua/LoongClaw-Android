package com.littlehelper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteRequestHelperTest {

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
    fun isVagueDeleteRequest_detectsBareDelete() {
        assertTrue(DeleteRequestHelper.isVagueDeleteRequest("删除"))
        assertTrue(DeleteRequestHelper.isVagueDeleteRequest("删掉"))
        assertTrue(DeleteRequestHelper.isVagueDeleteRequest("删除记录"))
        assertTrue(DeleteRequestHelper.isVagueDeleteRequest("  删掉  "))
    }

    @Test
    fun isVagueDeleteRequest_rejectsSpecificTarget() {
        assertFalse(DeleteRequestHelper.isVagueDeleteRequest("删除夏子涵生日信息"))
        assertFalse(DeleteRequestHelper.isVagueDeleteRequest("删掉咖啡那条"))
        assertFalse(DeleteRequestHelper.isVagueDeleteRequest("删除第2条"))
        assertFalse(DeleteRequestHelper.isVagueDeleteRequest("删除王刚的记录"))
    }

    @Test
    fun isVagueDeleteRequest_rejectsNonDelete() {
        assertFalse(DeleteRequestHelper.isVagueDeleteRequest("夏子涵生日是几号"))
        assertFalse(DeleteRequestHelper.isVagueDeleteRequest("记一下明天开会"))
    }

    @Test
    fun buildDeleteChoicePrompt_listsRecordsAndAsksWhichToDelete() {
        val records = listOf(
            birthdayRecord(1, "王刚", "6月8号"),
            MemoryRecord(
                id = 2,
                rawText = "明天上午10点与八达通联系",
                summary = "明天上午10点与八达通联系",
                category = "schedule"
            )
        )
        val prompt = DeleteRequestHelper.buildDeleteChoicePrompt(records)
        assertTrue(prompt.contains("您的记事本里共有 2 条记录"))
        assertTrue(prompt.contains("1. 王刚，生日6月8号"))
        assertTrue(prompt.contains("2. 明天上午10点与八达通联系"))
        assertTrue(prompt.contains("请问要删第几个"))
    }

    @Test
    fun buildAugmentedDeleteIntent_includesIndexAndSummary() {
        val record = birthdayRecord(1, "王刚", "6月8号")
        assertEquals("删除第1条：王刚，生日6月8号", DeleteRequestHelper.buildAugmentedDeleteIntent(0, record))
    }

    @Test
    fun isDeleteCancellation_detectsUserAbort() {
        assertTrue(DeleteRequestHelper.isDeleteCancellation("先不用删除"))
        assertTrue(DeleteRequestHelper.isDeleteCancellation("不用删除"))
        assertTrue(DeleteRequestHelper.isDeleteCancellation("算了"))
        assertTrue(DeleteRequestHelper.isDeleteCancellation("不删了"))
    }

    @Test
    fun isDeleteCancellation_rejectsActualDeleteChoice() {
        assertFalse(DeleteRequestHelper.isDeleteCancellation("第一个"))
        assertFalse(DeleteRequestHelper.isDeleteCancellation("删除王刚生日"))
    }
}
