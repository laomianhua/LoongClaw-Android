package com.littlehelper.chat

import android.content.Context
import com.google.gson.Gson
import com.littlehelper.ChatMessage
import com.littlehelper.ChatRole
import java.io.File
import java.nio.charset.StandardCharsets

class ChatHistoryStore(context: Context) {

    private val gson = Gson()
    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    fun load(): List<ChatMessage> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val snapshot = gson.fromJson(file.readText(), ChatHistorySnapshot::class.java)
                ?: return emptyList()
            if (snapshot.version != ChatHistorySnapshot.VERSION) return emptyList()
            snapshot.messages.map { it.toChatMessage() }
        }.getOrElse { emptyList() }
    }

    fun save(messages: List<ChatMessage>) {
        val prepared = prepareForPersistence(messages)
        val snapshot = ChatHistorySnapshot(
            messages = prepared.map(StoredChatMessage::from)
        )
        file.writeText(gson.toJson(snapshot))
    }

    companion object {
        const val MAX_MESSAGES = 500
        const val MAX_TOTAL_BYTES = 2 * 1024 * 1024
        const val MAX_MESSAGE_BYTES = 32 * 1024
        const val SAVE_DEBOUNCE_MS = 300L

        private const val FILE_NAME = "chat_history.json"
        private const val PER_MESSAGE_OVERHEAD_BYTES = 96

        fun isPersistable(message: ChatMessage): Boolean {
            if (message.isPartial) return false
            if (message.id == ChatMessage.PARTIAL_USER_ID) return false
            if (message.role == ChatRole.USER &&
                ChatMessage.isVoiceDraftPlaceholder(message.text)
            ) {
                return false
            }
            if (message.role == ChatRole.USER &&
                ChatMessage.isTranscribingPlaceholder(message.text)
            ) {
                return false
            }
            return true
        }

        fun prepareForPersistence(
            messages: List<ChatMessage>,
            maxMessages: Int = MAX_MESSAGES,
            maxTotalBytes: Int = MAX_TOTAL_BYTES,
            maxMessageBytes: Int = MAX_MESSAGE_BYTES
        ): List<ChatMessage> {
            val filtered = messages
                .filter(::isPersistable)
                .map { capMessageText(it, maxMessageBytes) }
            val byCount = if (filtered.size > maxMessages) {
                filtered.takeLast(maxMessages)
            } else {
                filtered
            }
            return trimByTotalBytes(byCount, maxTotalBytes)
        }

        private fun capMessageText(message: ChatMessage, maxMessageBytes: Int): ChatMessage {
            val textBytes = message.text.toByteArray(StandardCharsets.UTF_8)
            if (textBytes.size <= maxMessageBytes) return message
            val capped = truncateUtf8(message.text, maxMessageBytes)
            return message.copy(text = capped)
        }

        private fun trimByTotalBytes(
            messages: List<ChatMessage>,
            maxTotalBytes: Int
        ): List<ChatMessage> {
            if (messages.isEmpty()) return messages
            val mutable = messages.toMutableList()
            while (mutable.isNotEmpty() && estimateTotalBytes(mutable) > maxTotalBytes) {
                mutable.removeAt(0)
            }
            return mutable
        }

        fun estimateTotalBytes(messages: List<ChatMessage>): Int =
            messages.sumOf { message ->
                message.id.toByteArray(StandardCharsets.UTF_8).size +
                    message.text.toByteArray(StandardCharsets.UTF_8).size +
                    PER_MESSAGE_OVERHEAD_BYTES
            }

        private fun truncateUtf8(text: String, maxBytes: Int): String {
            if (maxBytes <= 0) return ""
            var end = text.length
            while (end > 0 && text.substring(0, end).toByteArray(StandardCharsets.UTF_8).size > maxBytes) {
                end--
            }
            return text.substring(0, end)
        }
    }
}
