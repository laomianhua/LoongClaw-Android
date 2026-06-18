package com.littlehelper.network

/**
 * 检测助手上一轮回复是否处于「追问/消歧」协议状态，供 ViewModel 设置 [FollowUpContext]。
 * 仅匹配 App 自身生成的固定追问模板，不解析用户 ASR 意图。
 */
object AssistantFollowUpDetector {

    private val saveFollowUpMarkers = listOf(
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

    fun invitesSaveFollowUp(assistantMessage: String): Boolean =
        saveFollowUpMarkers.any { assistantMessage.contains(it) }

    fun invitesQueryFollowUp(assistantMessage: String): Boolean =
        asksDisambiguationChoice(assistantMessage) ||
            queryFollowUpMarkers.any { assistantMessage.contains(it) }

    private val todoDisambiguationMarkers = listOf(
        "相关的待办任务",
        "哪一条待办",
        "说的是哪一条",
        "请问您说的是"
    )

    fun invitesTodoDisambiguation(assistantMessage: String): Boolean =
        todoDisambiguationMarkers.any { assistantMessage.contains(it) }

    fun invitesFollowUp(assistantMessage: String): Boolean =
        invitesSaveFollowUp(assistantMessage) ||
            invitesQueryFollowUp(assistantMessage) ||
            invitesTodoDisambiguation(assistantMessage)

    fun asksDisambiguationChoice(assistantMessage: String): Boolean =
        assistantMessage.contains("请问是第几个")

    fun asksDeleteDisambiguationChoice(assistantMessage: String): Boolean =
        assistantMessage.contains("请问要删第几个") ||
            assistantMessage.contains("想删除哪") ||
            assistantMessage.contains("要删哪") ||
            assistantMessage.contains("删哪条") ||
            assistantMessage.contains("删哪几条") ||
            assistantMessage.contains("要删哪个") ||
            assistantMessage.contains("还没删成功")
}
