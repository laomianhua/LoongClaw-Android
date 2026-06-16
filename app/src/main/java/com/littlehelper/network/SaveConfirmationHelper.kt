package com.littlehelper.network

import com.littlehelper.ChatMessage as UiChatMessage
import com.littlehelper.ChatRole
import com.littlehelper.FollowUpContext
import com.littlehelper.VoiceIntentDetector

object SaveConfirmationHelper {
    private val affirmationPhrases = listOf(
        "是的",
        "是啊",
        "对的",
        "对",
        "好的",
        "好吧",
        "好",
        "嗯",
        "嗯嗯",
        "要",
        "记上吧",
        "记上",
        "帮我记",
        "记下来",
        "可以",
        "行",
        "没问题"
    )

    fun isShortAffirmation(text: String): Boolean {
        val normalized = text.trim()
            .trimEnd('。', '！', '!', '？', '?', '，', ',')
        if (normalized.isEmpty() || normalized.length > 10) return false
        return affirmationPhrases.any { phrase ->
            normalized == phrase || normalized.startsWith(phrase)
        }
    }

    /**
     * Finds the user utterance before the last assistant message that invited a save follow-up.
     */
    fun findPriorUserIntent(messages: List<UiChatMessage>): String? {
        val filtered = messages.filter { message ->
            !message.isPartial &&
                !message.isError &&
                message.text.isNotBlank() &&
                !(message.role == ChatRole.ASSISTANT && ChatHistoryBuilder.isStatusMessage(message.text))
        }

        val saveFollowUpAssistantIndex = filtered.indices.lastOrNull { index ->
            filtered[index].role == ChatRole.ASSISTANT &&
                VoiceIntentDetector.invitesSaveFollowUp(filtered[index].text)
        } ?: return null

        if (saveFollowUpAssistantIndex <= 0) return null

        return filtered.subList(0, saveFollowUpAssistantIndex)
            .lastOrNull { it.role == ChatRole.USER }
            ?.text
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun stitchSaveConfirmation(
        messages: List<UiChatMessage>,
        followUpContext: FollowUpContext = FollowUpContext.NONE
    ): List<UiChatMessage> {
        if (followUpContext != FollowUpContext.SAVE) return messages

        val lastUser = messages.lastOrNull { !it.isPartial && !it.isError && it.role == ChatRole.USER }
            ?: return messages
        if (!isShortAffirmation(lastUser.text)) return messages

        val priorIntent = findPriorUserIntent(messages) ?: return messages
        val stitchedText = buildString {
            append("[系统强制指令]：用户已明确对你的上一次询问点击了确认。请不要有任何废话和寒暄，立刻根据上文内容（如『我后天上午...』）生成包含 1~N 个 insert 的 `DB_OPS` 结构化 JSON 块执行写库！禁止回复『没有任何记录』或『目前还没有内容被记录』！")
            append("\n是的，请帮我记下上文提到的：$priorIntent")
        }

        return messages.map { message ->
            if (message.id == lastUser.id) message.copy(text = stitchedText) else message
        }
    }
}
