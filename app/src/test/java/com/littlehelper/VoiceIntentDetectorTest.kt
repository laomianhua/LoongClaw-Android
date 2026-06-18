package com.littlehelper

import com.littlehelper.network.AssistantFollowUpDetector
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceIntentDetectorTest {

    @Test
    fun detect_defaultsToSave_withoutFollowUpContext() {
        assertEquals(VoiceAction.SAVE, VoiceIntentDetector.detect())
        assertEquals(VoiceAction.SAVE, VoiceIntentDetector.detect(FollowUpContext.NONE))
    }

    @Test
    fun detect_queryOnlyWhenFollowUpContextQuery() {
        assertEquals(VoiceAction.QUERY, VoiceIntentDetector.detect(FollowUpContext.QUERY))
    }

    @Test
    fun detect_saveForDeleteAndSaveFollowUpContext() {
        assertEquals(VoiceAction.SAVE, VoiceIntentDetector.detect(FollowUpContext.DELETE))
        assertEquals(VoiceAction.SAVE, VoiceIntentDetector.detect(FollowUpContext.SAVE))
        assertEquals(VoiceAction.SAVE, VoiceIntentDetector.detect(FollowUpContext.TODO_DISAMBIGUATION))
    }

    @Test
    fun assistantFollowUpDetector_saveInvitation() {
        val lastAssistant = "您还没有记录过夏子涵的生日。需要我现在帮您记下吗？"
        assertEquals(VoiceAction.SAVE, VoiceIntentDetector.detect(FollowUpContext.SAVE))
        assert(AssistantFollowUpDetector.invitesSaveFollowUp(lastAssistant))
    }

    @Test
    fun assistantFollowUpDetector_queryDisambiguation() {
        val prompt = "我找到2条读音相近的记录，请问是第几个？\n1. 夏子杭，生日6月8号"
        assert(AssistantFollowUpDetector.asksDisambiguationChoice(prompt))
        assert(AssistantFollowUpDetector.invitesQueryFollowUp(prompt))
    }
}
