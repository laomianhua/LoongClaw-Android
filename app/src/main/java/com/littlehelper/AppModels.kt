package com.littlehelper

enum class MemoryCategory(val value: String) {
    SCHEDULE("schedule"),
    BIRTHDAY("birthday"),
    PARKING("parking"),
    ITEM_PLACE("item_place"),
    MOOD("mood"),
    GENERAL("general");

    companion object {
        fun fromValue(value: String?): MemoryCategory {
            return entries.firstOrNull { it.value == value } ?: GENERAL
        }

        /** 将任意字符串归一化为合法 category 值（六选一）。 */
        fun normalize(value: String?): String = fromValue(value?.trim()).value
    }
}

enum class ImportanceLevel(val value: String) {
    NORMAL("normal"),
    IMPORTANT("important"),
    CRITICAL("critical");

    companion object {
        fun fromValue(value: String?): ImportanceLevel =
            entries.firstOrNull { it.value == value?.trim() } ?: NORMAL

        /** 将任意字符串归一化为合法 importance_level 值（三选一）。 */
        fun normalize(value: String?): String = fromValue(value).value
    }
}

enum class RecordType(val value: String) {
    TODO("todo"),
    EVENT("event"),
    NOTE("note"),
    BIRTHDAY("birthday"),
    GENERAL("general");

    companion object {
        fun fromValue(value: String?): RecordType =
            entries.firstOrNull { it.value == value?.trim() } ?: GENERAL

        /** 将任意字符串归一化为合法 type 值（五选一）。 */
        fun normalize(value: String?): String = fromValue(value).value
    }
}

enum class VoiceAction {
    SAVE,
    QUERY
}

/** 多轮追问时的会话上下文：决定下一句短答应走记下还是查询。 */
enum class FollowUpContext {
    NONE,
    SAVE,
    QUERY,
    DELETE
}

enum class AppPhase {
    IDLE,
    LISTENING,           // 保留：旧 SpeechRecognizer 路径（备用）
    RECORDING,           // MediaRecorder 录音中（按住）
    SENDING,             // 正在上传 ASR 转文字
    PROCESSING,
    ANSWERING,
    ERROR
}

enum class ChatRole {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val isPartial: Boolean = false,
    val isError: Boolean = false
) {
    companion object {
        fun assistant(text: String, isError: Boolean = false): ChatMessage {
            return ChatMessage(
                id = "assistant-${System.nanoTime()}",
                role = ChatRole.ASSISTANT,
                text = text,
                isError = isError
            )
        }

        fun user(text: String, isPartial: Boolean = false): ChatMessage {
            return ChatMessage(
                id = if (isPartial) PARTIAL_USER_ID else "user-${System.nanoTime()}",
                role = ChatRole.USER,
                text = text,
                isPartial = isPartial
            )
        }

        const val PARTIAL_USER_ID = "partial-user"
    }
}
