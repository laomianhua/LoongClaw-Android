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
    DELETE,
    /** 待办完成消歧跟进（App 本地 QUERY_TODO 多条结果后）。 */
    TODO_DISAMBIGUATION
}

/** 多功能抽屉展开状态：驱动聊天区与面板区的权值/高度联动。 */
enum class PanelState {
    COLLAPSED,
    EXPANDED
}

/** 底部输入区模式：语音按住说话 vs 系统软键盘文本输入（互斥，不共用草稿）。 */
enum class InputMode {
    VOICE,
    KEYBOARD
}

/** + 号附件面板展开状态（Phase 1 仅占位，不接入 Picker）。 */
enum class AttachmentPanelState {
    CLOSED,
    OPEN
}

data class InputComposerUiState(
    val mode: InputMode = InputMode.VOICE,
    val attachmentPanel: AttachmentPanelState = AttachmentPanelState.CLOSED,
    val draftText: String = "",
    /** Phase 1：已选附件元信息（字节存于 ViewModel，待 Phase 2 上传）。 */
    val pendingAttachment: PendingAttachmentUi? = null
)

/** 已选附件的 UI 元信息（不含完整 bytes；图片可带缩略图）。 */
data class PendingAttachmentUi(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Int,
    val thumbnailBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PendingAttachmentUi
        if (fileName != other.fileName) return false
        if (mimeType != other.mimeType) return false
        if (sizeBytes != other.sizeBytes) return false
        if (thumbnailBytes != null) {
            if (other.thumbnailBytes == null) return false
            if (!thumbnailBytes.contentEquals(other.thumbnailBytes)) return false
        } else if (other.thumbnailBytes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + sizeBytes
        result = 31 * result + (thumbnailBytes?.contentHashCode() ?: 0)
        return result
    }
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
        const val RECORDING_PLACEHOLDER = "🎙 录音中…"
        const val TRANSCRIBING_PLACEHOLDER = "转写中…"

        /** 录音占位/空草稿：不应出现在聊天气泡里（录音中占位在 RECORDING 阶段由 Projector 控制显示）。 */
        fun isVoiceDraftPlaceholder(text: String): Boolean {
            val trimmed = text.trim()
            return trimmed.isEmpty() ||
                trimmed == RECORDING_PLACEHOLDER ||
                trimmed == "录音中" ||
                trimmed == "录音中…" ||
                trimmed == "…"
        }

        fun isTranscribingPlaceholder(text: String): Boolean {
            val trimmed = text.trim()
            return trimmed == TRANSCRIBING_PLACEHOLDER || trimmed == "转写中"
        }
    }
}
