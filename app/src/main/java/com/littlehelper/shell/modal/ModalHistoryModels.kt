package com.littlehelper.shell.modal

/** 白板历史中的一帧快照（OPEN / UPDATE 后压入）。 */
data class ModalHistoryEntry(
    val id: String,
    val blocks: List<ModalBlock>,
    val loadRevision: Long
)

/** 最多 [ModalHistoryReducer.MAX_ENTRIES] 条，按时间从旧到新；[currentIndex] 指向当前展示项。 */
data class ModalHistoryState(
    val entries: List<ModalHistoryEntry> = emptyList(),
    val currentIndex: Int = 0
) {
    val size: Int get() = entries.size

    fun canGoOlder(): Boolean = normalized().let { it.currentIndex > 0 }

    fun canGoNewer(): Boolean = normalized().let {
        it.entries.isNotEmpty() && it.currentIndex < it.entries.lastIndex
    }

    /** 保证 [currentIndex] 始终落在有效范围内，避免持久化/裁剪后索引越界导致无法翻页。 */
    fun normalized(): ModalHistoryState {
        if (entries.isEmpty()) return ModalHistoryState()
        val safeIndex = currentIndex.coerceIn(0, entries.lastIndex)
        return if (safeIndex == currentIndex) this else copy(currentIndex = safeIndex)
    }
}
