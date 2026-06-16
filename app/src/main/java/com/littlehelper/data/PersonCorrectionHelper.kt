package com.littlehelper.data

/** 从对话中提取姓名更正信息，供本地兜底 update 使用。 */
object PersonCorrectionHelper {

    fun extractTargetPersonName(reply: String): String? {
        Regex("您说的是[「\"']?([\\u4e00-\\u9fff]{2,4})[」\"']?").find(reply)?.groupValues?.get(1)?.let { return it }
        Regex("[「\"']([\\u4e00-\\u9fff]{2,4})[」\"'][，,]?不是").find(reply)?.groupValues?.get(1)?.let { return it }
        if (reply.contains("重新记") || reply.contains("更正") || reply.contains("改")) {
            val notIndex = reply.indexOf("不是").takeIf { it >= 0 } ?: Int.MAX_VALUE
            NameMatcher.extractPersonNames(reply)
                .firstOrNull { name -> reply.indexOf(name) < notIndex }
                ?.let { return it }
        }
        return null
    }

    fun extractRejectedPersonName(reply: String): String? {
        return Regex("不是[「\"']?([\\u4e00-\\u9fff]{2,4})[」\"']?").find(reply)?.groupValues?.get(1)
    }

    fun buildPersonUpdateFields(record: MemoryRecord, newPerson: String): MemoryRecordPayload {
        val oldPerson = record.person?.takeIf { it.isNotBlank() }
            ?: NameMatcher.extractPersonNames(record.summary).firstOrNull().orEmpty()
        val summary = if (oldPerson.isNotEmpty()) record.summary.replace(oldPerson, newPerson) else record.summary
        val rawText = if (oldPerson.isNotEmpty()) record.rawText.replace(oldPerson, newPerson) else record.rawText
        return MemoryRecordPayload(
            person = newPerson,
            summary = summary,
            rawText = rawText
        )
    }

    fun replyPromisesRecordChange(reply: String): Boolean {
        return listOf("重新记", "记下了", "帮您记", "已经记", "更新", "改一下", "更正").any { reply.contains(it) }
    }
}
