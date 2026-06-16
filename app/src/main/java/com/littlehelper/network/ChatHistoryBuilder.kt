package com.littlehelper.network

import com.littlehelper.ChatMessage as UiChatMessage
import com.littlehelper.ChatRole
import com.littlehelper.FollowUpContext

object ChatHistoryBuilder {
    private val statusPrefixes = listOf(
        "正在整理要点",
        "正在查找记录",
        "正在回答",
        "正在更正记录",
        "正在思考"
    )

    fun toApiMessages(
        messages: List<UiChatMessage>,
        followUpContext: FollowUpContext = FollowUpContext.NONE
    ): List<ChatMessage> {
        val effectiveMessages = SaveConfirmationHelper.stitchSaveConfirmation(messages, followUpContext)
        return effectiveMessages
            .filter { message ->
                !message.isPartial &&
                    !message.isError &&
                    message.text.isNotBlank() &&
                    !(message.role == ChatRole.ASSISTANT && isStatusMessage(message.text))
            }
            .map { message ->
                ChatMessage(
                    role = when (message.role) {
                        ChatRole.USER -> "user"
                        ChatRole.ASSISTANT -> "assistant"
                    },
                    content = message.text.trim()
                )
            }
    }

    fun isStatusMessage(text: String): Boolean {
        return statusPrefixes.any { prefix -> text.startsWith(prefix) }
    }
}
