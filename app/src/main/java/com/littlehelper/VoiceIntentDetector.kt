package com.littlehelper

object VoiceIntentDetector {
    private val questionMarkers = listOf(
        "什么", "哪里", "哪儿", "哪个", "哪些", "几号", "几点", "哪天", "几时", "多少", "谁",
        "几条", "有几条", "多少条",
        "怎么", "怎样", "如何", "有没有", "能不能", "是不是", "吗", "？", "?"
    )

    private val correctionMarkers = listOf(
        "不是", "更正", "说错了", "搞错了", "纠正", "改一下", "写错了", "听错了", "记错了"
    )

    private val deleteMarkers = listOf(
        "删除", "删掉", "去掉", "不要这条", "不要了", "取消记录", "删了"
    )

    private val secretaryFollowUpMarkers = listOf(
        "需要我现在帮您记下",
        "需要现在记下",
        "还没记录过",
        "还没有记录过",
        "还没有记下来",
        "我这边还没有记下",
        "要我帮您记下",
        "帮您记下吗",
        "我帮您记上",
        "帮您记上",
        "告诉我几点",
        "告诉我具体",
        "具体几点",
        "要不要现在告诉"
    )

    private val queryFollowUpMarkers = listOf(
        "还没找到",
        "还没有相关",
        "还没有记下",
        "您指的是",
        "您说的是",
        "是哪一位",
        "能再说一下",
        "能再说明",
        "请问您问的是",
        "不太确定您问的是"
    )

    private val characterClarificationPattern = Regex("是.+的[\\u4e00-\\u9fff]")

    fun detect(
        text: String,
        lastAssistantMessage: String? = null,
        followUpContext: FollowUpContext = FollowUpContext.NONE
    ): VoiceAction {
        val normalized = text.trim()
        if (normalized.isEmpty()) return VoiceAction.SAVE

        if (followUpContext == FollowUpContext.DELETE) {
            return VoiceAction.SAVE
        }

        if (followUpContext == FollowUpContext.QUERY) {
            if (isDeleteRequest(normalized)) return VoiceAction.SAVE
            return VoiceAction.QUERY
        }

        if (isDeleteRequest(normalized)) return VoiceAction.SAVE
        if (isCorrection(normalized)) return VoiceAction.SAVE

        if (followUpContext == FollowUpContext.SAVE) {
            return VoiceAction.SAVE
        }

        if (lastAssistantMessage != null && invitesSaveFollowUp(lastAssistantMessage)) {
            return VoiceAction.SAVE
        }

        if (lastAssistantMessage != null && asksDeleteDisambiguationChoice(lastAssistantMessage)) {
            return VoiceAction.SAVE
        }

        if (isQuery(normalized)) return VoiceAction.QUERY
        return VoiceAction.SAVE
    }

    fun isCorrection(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isEmpty()) return false
        if (correctionMarkers.any { normalized.contains(it) }) return true
        if (characterClarificationPattern.containsMatchIn(normalized)) return true
        return false
    }

    fun isDeleteRequest(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isEmpty()) return false
        return deleteMarkers.any { normalized.contains(it) }
    }

    fun invitesSaveFollowUp(assistantMessage: String): Boolean {
        return secretaryFollowUpMarkers.any { assistantMessage.contains(it) }
    }

    /** @deprecated 使用 [invitesSaveFollowUp] */
    fun invitesSecretaryFollowUp(assistantMessage: String): Boolean = invitesSaveFollowUp(assistantMessage)

    fun invitesQueryFollowUp(assistantMessage: String): Boolean {
        return asksDisambiguationChoice(assistantMessage) ||
            queryFollowUpMarkers.any { assistantMessage.contains(it) }
    }

    /** 助手是否在邀请用户补充信息（供 ViewModel 判断跟进状态）。 */
    fun invitesFollowUp(assistantMessage: String): Boolean =
        invitesSaveFollowUp(assistantMessage) || invitesQueryFollowUp(assistantMessage)

    fun asksDisambiguationChoice(assistantMessage: String): Boolean {
        return assistantMessage.contains("请问是第几个")
    }

    fun asksDeleteDisambiguationChoice(assistantMessage: String): Boolean {
        return assistantMessage.contains("请问要删第几个") ||
            assistantMessage.contains("想删除哪") ||
            assistantMessage.contains("要删哪") ||
            assistantMessage.contains("删哪条") ||
            assistantMessage.contains("删哪几条") ||
            assistantMessage.contains("要删哪个") ||
            assistantMessage.contains("还没删成功")
    }

    fun isAwaitingDeleteChoice(
        followUpContext: FollowUpContext,
        lastAssistantMessage: String?
    ): Boolean {
        if (followUpContext == FollowUpContext.DELETE) return true
        val last = lastAssistantMessage.orEmpty()
        return asksDeleteDisambiguationChoice(last)
    }

    private fun isQuery(text: String): Boolean {
        return questionMarkers.any { marker -> text.contains(marker) }
    }
}
