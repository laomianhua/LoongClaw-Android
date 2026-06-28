package com.littlehelper.chat

import com.littlehelper.ChatMessage
import com.littlehelper.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryStoreTest {

    @Test
    fun isPersistable_rejectsPartialAndPlaceholders() {
        assertFalse(ChatHistoryStore.isPersistable(ChatMessage.user("hello", isPartial = true)))
        assertFalse(
            ChatHistoryStore.isPersistable(
                ChatMessage(id = ChatMessage.PARTIAL_USER_ID, role = ChatRole.USER, text = "x")
            )
        )
        assertFalse(
            ChatHistoryStore.isPersistable(
                ChatMessage.user(ChatMessage.RECORDING_PLACEHOLDER)
            )
        )
        assertFalse(
            ChatHistoryStore.isPersistable(
                ChatMessage.user(ChatMessage.TRANSCRIBING_PLACEHOLDER)
            )
        )
        assertTrue(ChatHistoryStore.isPersistable(ChatMessage.user("你好")))
        assertTrue(ChatHistoryStore.isPersistable(ChatMessage.assistant("回复")))
    }

    @Test
    fun prepareForPersistence_keepsLatestMessagesWithinCountLimit() {
        val messages = (1..600).map { index ->
            ChatMessage.user("message-$index")
        }
        val prepared = ChatHistoryStore.prepareForPersistence(
            messages,
            maxMessages = 500,
            maxTotalBytes = Int.MAX_VALUE
        )
        assertEquals(500, prepared.size)
        assertEquals("message-101", prepared.first().text)
        assertEquals("message-600", prepared.last().text)
    }

    @Test
    fun prepareForPersistence_trimsOldestWhenTotalBytesExceeded() {
        val largeText = "x".repeat(8_000)
        val messages = List(300) { index ->
            ChatMessage.user("$index-$largeText")
        }
        val prepared = ChatHistoryStore.prepareForPersistence(
            messages,
            maxMessages = 500,
            maxTotalBytes = 64_000
        )
        assertTrue(prepared.size < messages.size)
        assertTrue(ChatHistoryStore.estimateTotalBytes(prepared) <= 64_000)
        assertEquals("299-$largeText", prepared.last().text)
    }

    @Test
    fun prepareForPersistence_capsSingleMessageText() {
        val huge = "字".repeat(20_000)
        val prepared = ChatHistoryStore.prepareForPersistence(
            listOf(ChatMessage.user(huge)),
            maxMessageBytes = 32
        )
        assertEquals(1, prepared.size)
        assertTrue(prepared.single().text.toByteArray(Charsets.UTF_8).size <= 32)
    }
}
