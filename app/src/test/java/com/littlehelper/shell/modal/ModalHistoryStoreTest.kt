package com.littlehelper.shell.modal

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModalHistoryStoreTest {

    private fun entry(id: String, payload: String) = ModalHistoryEntry(
        id = id,
        loadRevision = 1L,
        blocks = listOf(
            ModalBlock(
                id = "b1",
                type = "table",
                title = "标题",
                data = JsonObject().apply { addProperty("text", payload) }
            )
        )
    )

    @Test
    fun prepareForPersistence_keepsLatestEntriesWithinCountLimit() {
        val entries = (1..12).map { index ->
            entry("e$index", "page-$index")
        }
        val prepared = ModalHistoryStore.prepareForPersistence(
            ModalHistoryState(entries = entries, currentIndex = 11)
        )
        assertEquals(ModalHistoryReducer.MAX_ENTRIES, prepared.size)
        assertEquals("e3", prepared.entries.first().id)
        assertEquals("e12", prepared.entries.last().id)
        assertEquals(9, prepared.currentIndex)
    }

    @Test
    fun prepareForPersistence_trimsOldestWhenTotalBytesExceeded() {
        val large = "x".repeat(8_000)
        val entries = List(40) { index -> entry("e$index", "$index-$large") }
        val prepared = ModalHistoryStore.prepareForPersistence(
            ModalHistoryState(entries = entries, currentIndex = 39),
            maxTotalBytes = 64_000
        )
        assertTrue(prepared.size < entries.size)
        assertTrue(ModalHistoryStore.estimateTotalBytes(prepared.entries) <= 64_000)
    }

    @Test
    fun prepareForPersistence_dropsEmptyEntries() {
        val prepared = ModalHistoryStore.prepareForPersistence(
            ModalHistoryState(
                entries = listOf(
                    ModalHistoryEntry(id = "empty", loadRevision = 1L, blocks = emptyList()),
                    entry("ok", "data")
                ),
                currentIndex = 1
            )
        )
        assertEquals(1, prepared.size)
        assertEquals("ok", prepared.entries.single().id)
    }
}
