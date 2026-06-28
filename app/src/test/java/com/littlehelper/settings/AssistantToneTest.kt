package com.littlehelper.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantToneTest {

    @Test
    fun friendTone_mentionsOrdinaryUserNotEngineer() {
        val text = AssistantTone.FRIEND.systemText()
        assertTrue(text.contains("普通"))
        assertFalse(text.isBlank())
    }

    @Test
    fun fromWire_defaultsToFriend() {
        assertEquals(AssistantTone.FRIEND, AssistantTone.fromWire(null))
        assertEquals(AssistantTone.CONCISE, AssistantTone.fromWire("concise"))
    }
}
