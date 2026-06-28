package com.littlehelper.shell.modal

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID

object ModalHistoryReducer {

    const val MAX_ENTRIES = 10

    fun pushSnapshot(history: ModalHistoryState, modalState: ModalState): ModalHistoryState {
        if (!modalState.isOpen || modalState.blocks.isEmpty()) return history
        val entry = ModalHistoryEntry(
            id = UUID.randomUUID().toString(),
            blocks = snapshotBlocks(modalState.blocks),
            loadRevision = modalState.loadRevision
        )
        val entries = (history.entries + entry).takeLast(MAX_ENTRIES)
        return ModalHistoryState(
            entries = entries,
            currentIndex = entries.lastIndex
        ).normalized()
    }

    fun navigate(history: ModalHistoryState, delta: Int): Pair<ModalHistoryState, ModalState?> {
        val safeHistory = history.normalized()
        if (safeHistory.entries.isEmpty() || delta == 0) return safeHistory to null
        val target = (safeHistory.currentIndex + delta).coerceIn(0, safeHistory.entries.lastIndex)
        if (target == safeHistory.currentIndex) return safeHistory to null
        return selectIndex(safeHistory, target)
    }

    fun selectIndex(history: ModalHistoryState, index: Int): Pair<ModalHistoryState, ModalState?> {
        val safeHistory = history.normalized()
        if (safeHistory.entries.isEmpty()) return safeHistory to null
        val target = index.coerceIn(0, safeHistory.entries.lastIndex)
        val entry = safeHistory.entries[target]
        return safeHistory.copy(currentIndex = target) to ModalState(
            isOpen = true,
            blocks = entry.blocks,
            loadRevision = entry.loadRevision
        )
    }

    fun deleteAt(
        history: ModalHistoryState,
        index: Int,
        currentModal: ModalState
    ): Pair<ModalHistoryState, ModalState> {
        val safeHistory = history.normalized()
        if (safeHistory.entries.isEmpty()) return safeHistory to currentModal
        val safeIndex = index.coerceIn(0, safeHistory.entries.lastIndex)
        val remaining = safeHistory.entries.toMutableList().apply { removeAt(safeIndex) }
        if (remaining.isEmpty()) {
            return ModalHistoryState() to ModalState(
                isOpen = false,
                blocks = emptyList(),
                loadRevision = currentModal.loadRevision
            )
        }
        val newIndex = when {
            safeIndex >= remaining.size -> remaining.lastIndex
            else -> safeIndex
        }
        val entry = remaining[newIndex]
        return ModalHistoryState(entries = remaining, currentIndex = newIndex).normalized() to ModalState(
            isOpen = true,
            blocks = entry.blocks,
            loadRevision = entry.loadRevision
        )
    }

    private fun snapshotBlocks(blocks: List<ModalBlock>): List<ModalBlock> {
        return blocks.map { block ->
            ModalBlock(
                id = block.id,
                type = block.type,
                title = block.title,
                data = deepCopyJson(block.data)
            )
        }
    }

    private fun deepCopyJson(source: JsonObject): JsonObject {
        return JsonParser.parseString(source.toString()).asJsonObject
    }
}
