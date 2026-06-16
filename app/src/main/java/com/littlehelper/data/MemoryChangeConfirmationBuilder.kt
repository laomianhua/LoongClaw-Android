package com.littlehelper.data

/** 写库成功后由 App 统一生成口语确认，不依赖 AI 口头承诺措辞。 */
object MemoryChangeConfirmationBuilder {
    fun build(
        result: MemoryOperationExecutor.ExecutionResult,
        recordsById: Map<Long, MemoryRecord>
    ): String? {
        if (result.pendingClearAll) return null
        if (result.applied == 0 && result.errors.isEmpty()) return null

        val parts = mutableListOf<String>()

        if (result.insertedIds.isNotEmpty()) {
            val summaries = result.insertedIds.mapNotNull { id ->
                recordsById[id]?.summary?.takeIf { it.isNotBlank() }
            }
            parts += when {
                summaries.isEmpty() -> "好的，已经帮您记好了。"
                summaries.size == 1 -> "好的，已经记下：${summaries.first()}。"
                else -> "好的，已经记下 ${summaries.size} 条：${summaries.joinToString("；")}。"
            }
        }

        if (result.updatedIds.isNotEmpty()) {
            val summaries = result.updatedIds.mapNotNull { id ->
                recordsById[id]?.summary?.takeIf { it.isNotBlank() }
            }
            parts += when {
                summaries.size == 1 && summaries.first().isNotBlank() ->
                    "好的，已经更新：${summaries.first()}。"
                result.updatedIds.size == 1 -> "好的，已经更新这条记录。"
                else -> "好的，已经更新 ${result.updatedIds.size} 条记录。"
            }
        }

        if (result.deletedIds.isNotEmpty()) {
            parts += when (result.deletedIds.size) {
                1 -> "好的，已经删除这条记录。"
                else -> "好的，已经删除 ${result.deletedIds.size} 条记录。"
            }
        }

        result.errors.firstOrNull()?.let { error ->
            parts += "有些操作没成功：$error"
        }

        return parts.joinToString("").takeIf { it.isNotBlank() }
    }
}
