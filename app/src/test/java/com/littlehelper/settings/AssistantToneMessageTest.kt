package com.littlehelper.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantToneMessageTest {

    @Test
    fun stripEmbeddedTonePrefix_keepsUserPartOnly() {
        val raw = "${AssistantToneStore.TONE_PREFIX} 请像朋友说话。\n\n你好我又来了"
        assertEquals("你好我又来了", AssistantToneMessage.stripEmbeddedTonePrefix(raw))
    }

    @Test
    fun stripEmbeddedTonePrefix_leavesPlainMessageUntouched() {
        assertEquals("普通消息", AssistantToneMessage.stripEmbeddedTonePrefix("普通消息"))
    }

    @Test
    fun friendToneSystemText_isNotBlank() {
        assertTrue(AssistantTone.FRIEND.systemText().isNotBlank())
    }
}
