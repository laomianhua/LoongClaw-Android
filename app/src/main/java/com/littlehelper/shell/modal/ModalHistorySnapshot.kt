package com.littlehelper.shell.modal

import com.google.gson.JsonObject

data class ModalHistorySnapshot(
    val version: Int = VERSION,
    val currentIndex: Int = 0,
    val entries: List<StoredModalHistoryEntry> = emptyList()
) {
    companion object {
        const val VERSION = 1
    }
}

data class StoredModalHistoryEntry(
    val id: String,
    val loadRevision: Long,
    val blocks: List<StoredModalBlock>
) {
    fun toEntry(): ModalHistoryEntry = ModalHistoryEntry(
        id = id,
        loadRevision = loadRevision,
        blocks = blocks.map { it.toBlock() }
    )

    companion object {
        fun from(entry: ModalHistoryEntry): StoredModalHistoryEntry = StoredModalHistoryEntry(
            id = entry.id,
            loadRevision = entry.loadRevision,
            blocks = entry.blocks.map(StoredModalBlock::from)
        )
    }
}

data class StoredModalBlock(
    val id: String,
    val type: String,
    val title: String? = null,
    val data: JsonObject
) {
    fun toBlock(): ModalBlock = ModalBlock(
        id = id,
        type = type,
        title = title,
        data = data
    )

    companion object {
        fun from(block: ModalBlock): StoredModalBlock = StoredModalBlock(
            id = block.id,
            type = block.type,
            title = block.title,
            data = block.data
        )
    }
}
