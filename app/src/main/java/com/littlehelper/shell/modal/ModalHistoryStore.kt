package com.littlehelper.shell.modal

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.nio.charset.StandardCharsets

class ModalHistoryStore(context: Context) {

    private val gson = Gson()
    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    fun load(): ModalHistoryState? {
        if (!file.exists()) return null
        return runCatching {
            val snapshot = gson.fromJson(file.readText(), ModalHistorySnapshot::class.java)
                ?: return null
            if (snapshot.version != ModalHistorySnapshot.VERSION) return null
            val entries = snapshot.entries.map { it.toEntry() }
                .filter { it.blocks.isNotEmpty() }
            if (entries.isEmpty()) return null
            val currentIndex = snapshot.currentIndex.coerceIn(0, entries.lastIndex)
            ModalHistoryState(entries = entries, currentIndex = currentIndex).normalized()
        }.getOrNull()
    }

    fun save(history: ModalHistoryState) {
        val prepared = prepareForPersistence(history)
        if (prepared.entries.isEmpty()) {
            if (file.exists()) file.delete()
            return
        }
        val snapshot = ModalHistorySnapshot(
            currentIndex = prepared.currentIndex.coerceIn(0, prepared.entries.lastIndex),
            entries = prepared.entries.map(StoredModalHistoryEntry::from)
        )
        file.writeText(gson.toJson(snapshot))
    }

    companion object {
        const val SAVE_DEBOUNCE_MS = 300L
        const val MAX_TOTAL_BYTES = 2 * 1024 * 1024
        const val MAX_BLOCK_DATA_BYTES = 256 * 1024
        private const val FILE_NAME = "modal_history.json"
        private const val PER_ENTRY_OVERHEAD_BYTES = 128

        fun prepareForPersistence(
            history: ModalHistoryState,
            maxEntries: Int = ModalHistoryReducer.MAX_ENTRIES,
            maxTotalBytes: Int = MAX_TOTAL_BYTES,
            maxBlockDataBytes: Int = MAX_BLOCK_DATA_BYTES
        ): ModalHistoryState {
            val entries = history.entries
                .filter { it.blocks.isNotEmpty() }
                .map { capEntryBlockData(it, maxBlockDataBytes) }
                .takeLast(maxEntries)
            if (entries.isEmpty()) return ModalHistoryState()
            val currentIndex = history.currentIndex.coerceIn(0, entries.lastIndex)
            return trimByTotalBytes(
                ModalHistoryState(entries = entries, currentIndex = currentIndex).normalized(),
                maxTotalBytes
            )
        }

        private fun capEntryBlockData(
            entry: ModalHistoryEntry,
            maxBlockDataBytes: Int
        ): ModalHistoryEntry {
            val blocks = entry.blocks.filter { block ->
                block.data.toString().toByteArray(StandardCharsets.UTF_8).size <= maxBlockDataBytes
            }
            return entry.copy(blocks = blocks)
        }

        private fun trimByTotalBytes(
            history: ModalHistoryState,
            maxTotalBytes: Int
        ): ModalHistoryState {
            val mutable = history.entries.toMutableList()
            var currentIndex = history.currentIndex.coerceIn(0, mutable.lastIndex.coerceAtLeast(0))
            while (mutable.isNotEmpty() && estimateTotalBytes(mutable) > maxTotalBytes) {
                mutable.removeAt(0)
                currentIndex = (currentIndex - 1).coerceAtLeast(0)
            }
            if (mutable.isEmpty()) return ModalHistoryState()
            currentIndex = currentIndex.coerceIn(0, mutable.lastIndex)
            return ModalHistoryState(entries = mutable, currentIndex = currentIndex).normalized()
        }

        fun estimateTotalBytes(entries: List<ModalHistoryEntry>): Int =
            entries.sumOf { entry ->
                PER_ENTRY_OVERHEAD_BYTES +
                    entry.blocks.sumOf { block ->
                        block.id.toByteArray(StandardCharsets.UTF_8).size +
                            block.type.toByteArray(StandardCharsets.UTF_8).size +
                            (block.title?.toByteArray(StandardCharsets.UTF_8)?.size ?: 0) +
                            block.data.toString().toByteArray(StandardCharsets.UTF_8).size
                    }
            }
    }
}
