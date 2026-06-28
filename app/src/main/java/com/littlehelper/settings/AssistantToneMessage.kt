package com.littlehelper.settings

object AssistantToneMessage {

    /** 从 Gateway 回显的用户消息里去掉嵌入的语气前缀，只展示用户原话。 */
    fun stripEmbeddedTonePrefix(text: String): String {
        if (!text.startsWith(AssistantToneStore.TONE_PREFIX)) return text
        val userPart = text.substringAfter("\n\n", "").trim()
        return userPart.ifBlank { text }
    }
}
