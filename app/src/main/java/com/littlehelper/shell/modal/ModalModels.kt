package com.littlehelper.shell.modal

import com.google.gson.JsonObject

enum class ModalAction {
    OPEN,
    UPDATE,
    CLOSE,
    NOOP;

    companion object {
        fun fromWire(value: String?): ModalAction = when (value?.lowercase()) {
            "open" -> OPEN
            "update" -> UPDATE
            "close" -> CLOSE
            "noop" -> NOOP
            else -> NOOP
        }
    }
}

/** Agent Canvas 白板中的一个 block（协议 §5）。 */
data class ModalBlock(
    val id: String,
    val type: String,
    val title: String? = null,
    val data: JsonObject
)

data class ModalState(
    val isOpen: Boolean = false,
    val blocks: List<ModalBlock> = emptyList(),
    /** 每次 OPEN/UPDATE 递增，用于 WebView 在同 URL 内容变更时强制 reload。 */
    val loadRevision: Long = 0L
)

object ModalStateReducer {

    fun apply(
        current: ModalState,
        action: ModalAction,
        incoming: List<ModalBlock>
    ): ModalState {
        return when (action) {
            ModalAction.NOOP -> current
            ModalAction.CLOSE -> ModalState(
                isOpen = false,
                blocks = emptyList(),
                loadRevision = current.loadRevision
            )
            ModalAction.OPEN -> ModalState(
                isOpen = true,
                blocks = incoming,
                loadRevision = current.loadRevision + 1L
            )
            ModalAction.UPDATE -> {
                if (!current.isOpen) return current
                if (incoming.isEmpty()) return current
                ModalState(
                    isOpen = true,
                    blocks = mergeBlocks(current.blocks, incoming),
                    loadRevision = current.loadRevision + 1L
                )
            }
        }
    }

    private fun mergeBlocks(
        existing: List<ModalBlock>,
        incoming: List<ModalBlock>
    ): List<ModalBlock> {
        if (incoming.isEmpty()) return existing
        val byId = existing.associateBy { it.id }.toMutableMap()
        incoming.forEach { byId[it.id] = it }
        return byId.values.toList()
    }
}
