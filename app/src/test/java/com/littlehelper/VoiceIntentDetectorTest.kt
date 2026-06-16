package com.littlehelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceIntentDetectorTest {

    @Test
    fun detect_saveForBirthdayStatement() {
        val action = VoiceIntentDetector.detect("夏子涵的生日是6月8号")
        assertEquals(VoiceAction.SAVE, action)
    }

    @Test
    fun detect_saveForCorrectionUtterance() {
        val action = VoiceIntentDetector.detect("是杭州的杭不是涵养的涵")
        assertEquals(VoiceAction.SAVE, action)
    }

    @Test
    fun detect_saveForShortCharacterCorrection() {
        val action = VoiceIntentDetector.detect("是杭州的杭")
        assertEquals(VoiceAction.SAVE, action)
    }

    @Test
    fun detect_saveForCorrectionEvenWithQuestionMarker() {
        val action = VoiceIntentDetector.detect("是杭州的杭吗")
        assertEquals(VoiceAction.SAVE, action)
    }

    @Test
    fun detect_saveAfterQueryInvitation() {
        val lastAssistant = "您还没有记录过夏子涵的生日。需要我现在帮您记下吗？"
        val action = VoiceIntentDetector.detect("是杭州的杭", lastAssistant)
        assertEquals(VoiceAction.SAVE, action)
    }

    @Test
    fun detect_saveForAffirmationAfterQueryInvitation() {
        val lastAssistant = "您还没有记录过夏子涵的生日。需要我现在帮您记下吗？"
        val action = VoiceIntentDetector.detect("好的", lastAssistant)
        assertEquals(VoiceAction.SAVE, action)
    }

    @Test
    fun detect_queryForQuestion() {
        val action = VoiceIntentDetector.detect("夏子杭生日是几号")
        assertEquals(VoiceAction.QUERY, action)
    }

    @Test
    fun detect_queryForBirthdayWithNaTian() {
        val action = VoiceIntentDetector.detect("夏子涵的生日是哪天")
        assertEquals(VoiceAction.QUERY, action)
    }

    @Test
    fun detect_queryForParkingQuestion() {
        val action = VoiceIntentDetector.detect("车停在哪里")
        assertEquals(VoiceAction.QUERY, action)
    }

    @Test
    fun detect_saveForDeleteRequest() {
        val action = VoiceIntentDetector.detect("删除夏子涵生日信息")
        assertEquals(VoiceAction.SAVE, action)
        assertTrue(VoiceIntentDetector.isDeleteRequest("删除夏子涵生日信息"))
    }

    @Test
    fun detect_saveAfterCoffeeTimeFollowUp() {
        val lastAssistant = "您和李总的咖啡时间，我这边还没有记下来。您要不要现在告诉我几点，我帮您记上？"
        assertTrue(VoiceIntentDetector.invitesFollowUp(lastAssistant))
        val action = VoiceIntentDetector.detect("下午2点", lastAssistant)
        assertEquals(VoiceAction.SAVE, action)
    }

    @Test
    fun isCorrection_detectsCharacterClarification() {
        assertTrue(VoiceIntentDetector.isCorrection("是杭州的杭"))
        assertTrue(VoiceIntentDetector.isCorrection("夏子涵的名字中的杭是杭州的杭"))
        assertTrue(VoiceIntentDetector.isCorrection("不是涵养的涵"))
    }

    @Test
    fun asksDisambiguationChoice_detectsNumberPrompt() {
        val prompt = "我找到2条读音相近的记录，请问是第几个？\n1. 夏子杭，生日6月8号"
        assertTrue(VoiceIntentDetector.asksDisambiguationChoice(prompt))
        assertTrue(VoiceIntentDetector.invitesFollowUp(prompt))
        assertTrue(VoiceIntentDetector.invitesQueryFollowUp(prompt))
    }

    @Test
    fun detect_queryContext_keepsQueryForShortCharacterAnswer() {
        val action = VoiceIntentDetector.detect(
            text = "是大纲的纲",
            lastAssistantMessage = "您还没有记录过王纲的生日。需要我现在帮您记下吗？",
            followUpContext = FollowUpContext.QUERY
        )
        assertEquals(VoiceAction.QUERY, action)
    }

    @Test
    fun detect_queryContext_keepsQueryForChoiceNumber() {
        val action = VoiceIntentDetector.detect(
            text = "2",
            followUpContext = FollowUpContext.QUERY
        )
        assertEquals(VoiceAction.QUERY, action)
    }

    @Test
    fun detect_saveContext_keepsSaveForTimeFollowUp() {
        val lastAssistant = "您和李总的咖啡时间，我这边还没有记下来。您要不要现在告诉我几点，我帮您记上？"
        val action = VoiceIntentDetector.detect(
            text = "下午2点",
            lastAssistantMessage = lastAssistant,
            followUpContext = FollowUpContext.SAVE
        )
        assertEquals(VoiceAction.SAVE, action)
    }

    @Test
    fun invitesQueryFollowUp_detectsClarificationPrompt() {
        val reply = "我还没找到相关记录，您说的是哪一位呢？"
        assertTrue(VoiceIntentDetector.invitesQueryFollowUp(reply))
    }

    @Test
    fun asksDeleteDisambiguationChoice_detectsDeletePrompt() {
        val prompt = "您目前有2条记录：\n1. 王刚，生日6月8号\n请问要删第几个？"
        assertTrue(VoiceIntentDetector.asksDeleteDisambiguationChoice(prompt))
    }

    @Test
    fun detect_deleteContext_keepsSaveForChoiceNumber() {
        val action = VoiceIntentDetector.detect(
            text = "2",
            followUpContext = FollowUpContext.DELETE
        )
        assertEquals(VoiceAction.SAVE, action)
    }

    @Test
    fun detect_deleteContext_keepsSaveForFirstOrdinal() {
        val action = VoiceIntentDetector.detect(
            text = "第一个",
            followUpContext = FollowUpContext.DELETE
        )
        assertEquals(VoiceAction.SAVE, action)
    }

    @Test
    fun detect_saveAfterDeleteDisambiguationPrompt() {
        val lastAssistant = "您目前有2条记录：\n1. 王刚，生日6月8号\n请问要删第几个？"
        val action = VoiceIntentDetector.detect("2", lastAssistant)
        assertEquals(VoiceAction.SAVE, action)
    }

    @Test
    fun asksDeleteDisambiguationChoice_detectsAiDeletePrompt() {
        val prompt = "请问您想删除哪一条记录？请告诉我具体内容，比如「王刚的生日」。"
        assertTrue(VoiceIntentDetector.asksDeleteDisambiguationChoice(prompt))
    }

    @Test
    fun isAwaitingDeleteChoice_whenFailedDeleteRetry() {
        assertTrue(
            VoiceIntentDetector.isAwaitingDeleteChoice(
                FollowUpContext.NONE,
                "抱歉，还没删成功，请再说一遍要删哪几条。"
            )
        )
    }
}
