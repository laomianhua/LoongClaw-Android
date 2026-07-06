package com.littlehelper.shell.session

import com.littlehelper.ChatMessage
import com.littlehelper.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OpenClawUserMessageCommitterTest {

    @Test
    fun appendUserMessage_alwaysAddsNewBubble() {
        val initial = listOf(ChatMessage.user("打开"))
        val next = OpenClawUserMessageCommitter.appendUserMessage(initial, "打开文件管理")
        assertEquals(2, next.size)
        assertEquals("打开", next[0].text)
        assertEquals("打开文件管理", next[1].text)
    }

    @Test
    fun appendUserMessage_allowsRepeatedText() {
        val initial = listOf(ChatMessage.user("好的"))
        val next = OpenClawUserMessageCommitter.appendUserMessage(initial, "好的")
        assertEquals(2, next.size)
        assertEquals("好的", next[0].text)
        assertEquals("好的", next[1].text)
        assertFalse(next[0].id == next[1].id)
    }

    @Test
    fun appendUserMessage_finalizesVoicePartial() {
        val initial = listOf(
            ChatMessage(
                id = ChatMessage.PARTIAL_USER_ID,
                role = ChatRole.USER,
                text = "正在识别",
                isPartial = true,
            )
        )
        val next = OpenClawUserMessageCommitter.appendUserMessage(initial, "你好")
        assertEquals(1, next.size)
        assertEquals("你好", next.single().text)
        assertFalse(next.single().isPartial)
        assertFalse(next.single().id == ChatMessage.PARTIAL_USER_ID)
    }
}
