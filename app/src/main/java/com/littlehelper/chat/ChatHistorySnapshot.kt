package com.littlehelper.chat

import com.littlehelper.ChatMessage
import com.littlehelper.ChatRole

data class ChatHistorySnapshot(
    val version: Int = VERSION,
    val messages: List<StoredChatMessage> = emptyList()
) {
    companion object {
        const val VERSION = 1
    }
}

data class StoredChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val isError: Boolean = false
) {
    fun toChatMessage(): ChatMessage = ChatMessage(
        id = id,
        role = role,
        text = text,
        isPartial = false,
        isError = isError
    )

    companion object {
        fun from(message: ChatMessage): StoredChatMessage = StoredChatMessage(
            id = message.id,
            role = message.role,
            text = message.text,
            isError = message.isError
        )
    }
}
