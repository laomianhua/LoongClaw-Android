package com.littlehelper.shell.session

import com.littlehelper.ChatMessage
import com.littlehelper.ChatRole

/** 本地乐观插入用户气泡：每次发送独立一条，仅合并语音草稿 partial。 */
internal object OpenClawUserMessageCommitter {

    fun appendUserMessage(messages: List<ChatMessage>, text: String): List<ChatMessage> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return messages
        val mutable = messages.toMutableList()
        val partialIndex = mutable.indexOfLast { it.isPartial && it.role == ChatRole.USER }
        if (partialIndex >= 0) {
            mutable[partialIndex] = mutable[partialIndex].copy(
                text = trimmed,
                isPartial = false,
                id = "user-${System.nanoTime()}",
            )
            return mutable
        }
        return mutable + ChatMessage.user(trimmed, isPartial = false)
    }
}
