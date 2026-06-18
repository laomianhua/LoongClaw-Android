package com.littlehelper.data

object DeleteRequestHelper {

    private val deleteMarkers = listOf(
        "删除", "删掉", "去掉", "不要这条", "不要了", "取消记录", "删了"
    )

    fun isDeleteRequest(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isEmpty()) return false
        return deleteMarkers.any { normalized.contains(it) }
    }

    private val bareDeleteUtterances = setOf(
        "删除", "删掉", "去掉", "删了", "不要了", "取消记录"
    )

    private val vagueDeletePhrases = listOf(
        "删除记录", "删掉记录", "删除内容", "删掉内容"
    )

    private val specificContentMarkers = listOf(
        "生日", "停车", "车位", "咖啡", "万象城", "提醒", "刚才"
    )

    /** 仅说「删除」等、未指明删哪条时返回 true。 */
    fun isVagueDeleteRequest(text: String): Boolean {
        if (!isDeleteRequest(text)) return false

        val normalized = text.trim().replace(Regex("[，。！？、\\s]"), "")
        if (normalized.isEmpty()) return false

        if (Regex("第[一二两三四五六七八九十\\d]+个?").containsMatchIn(normalized)) return false
        if (Regex("第[一二两三四五六七八九十\\d]+条").containsMatchIn(normalized)) return false

        if (normalized in bareDeleteUtterances) return true
        if (vagueDeletePhrases.any { normalized == it }) return true

        if (NameMatcher.extractPersonNames(normalized).isNotEmpty()) return false
        if (specificContentMarkers.any { normalized.contains(it) }) return false

        val remaining = normalized.replace(
            Regex("(帮我|请|把|将|删除|删掉|去掉|取消|不要|这条|那条|记录|信息|内容|一下|掉|了)"),
            ""
        )
        return remaining.length < 2
    }

    fun buildDeleteChoicePrompt(records: List<MemoryRecord>): String {
        val listPart = DisambiguationHelper.buildAllRecordsAnswer(records)
        return "$listPart\n请问要删第几个？"
    }

    fun buildAugmentedDeleteIntent(index: Int, record: MemoryRecord): String {
        val summary = DisambiguationHelper.formatRecordLine(record, question = "")
        return "删除第${index + 1}条：$summary"
    }

    private val deleteCancellationPhrases = listOf(
        "不用删除", "不用删", "先不用", "不删了", "不要删", "别删", "不想删",
        "暂时不删", "先不删", "取消删除", "不删除了", "不用删了"
    )

    /** 用户在删除追问中说不想删了（如「先不用删除」「算了」）。 */
    fun isDeleteCancellation(text: String): Boolean {
        val normalized = text.trim().replace(Regex("[，。！？、\\s]"), "")
        if (normalized.isEmpty()) return false
        if (deleteCancellationPhrases.any { normalized.contains(it) }) return true
        return normalized in setOf("不用了", "算了", "取消", "不用")
    }

    fun buildDeleteCancellationReply(): String = "好的，那就不删了。"
}
