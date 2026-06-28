package com.littlehelper.shell.modal

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModalHistoryReducerTest {

    private fun block(id: String) = ModalBlock(id = id, type = "table", data = JsonObject())

    private fun openState(vararg ids: String, revision: Long = 1L) = ModalState(
        isOpen = true,
        blocks = ids.map { block(it) },
        loadRevision = revision
    )

    @Test
    fun pushSnapshot_appendsAndMovesToLatest() {
        val h1 = ModalHistoryReducer.pushSnapshot(ModalHistoryState(), openState("a", revision = 1L))
        assertEquals(1, h1.size)
        assertEquals(0, h1.currentIndex)

        val h2 = ModalHistoryReducer.pushSnapshot(h1, openState("b", revision = 2L))
        assertEquals(2, h2.size)
        assertEquals(1, h2.currentIndex)
    }

    @Test
    fun pushSnapshot_capsAtMaxEntries() {
        var history = ModalHistoryState()
        repeat(12) { index ->
            history = ModalHistoryReducer.pushSnapshot(
                history,
                openState("b$index", revision = index.toLong())
            )
        }
        assertEquals(ModalHistoryReducer.MAX_ENTRIES, history.size)
        assertEquals(ModalHistoryReducer.MAX_ENTRIES - 1, history.currentIndex)
    }

    @Test
    fun navigate_withMaxEntries_canMoveFromEveryIndex() {
        var history = ModalHistoryState()
        repeat(ModalHistoryReducer.MAX_ENTRIES) { index ->
            history = ModalHistoryReducer.pushSnapshot(
                history,
                openState("b$index", revision = index.toLong())
            )
        }
        assertEquals(ModalHistoryReducer.MAX_ENTRIES, history.size)
        assertEquals(ModalHistoryReducer.MAX_ENTRIES - 1, history.currentIndex)

        repeat(history.entries.lastIndex) { step ->
            val (nextHistory, modal) = ModalHistoryReducer.navigate(history, -1)
            assertEquals("step=$step", history.currentIndex - 1, nextHistory.currentIndex)
            assertEquals("b${history.currentIndex - 1}", modal?.blocks?.single()?.id)
            history = nextHistory
        }
        assertEquals(0, history.currentIndex)

        repeat(history.entries.lastIndex) { step ->
            val (nextHistory, modal) = ModalHistoryReducer.navigate(history, +1)
            assertEquals("step=$step", history.currentIndex + 1, nextHistory.currentIndex)
            assertEquals("b${history.currentIndex + 1}", modal?.blocks?.single()?.id)
            history = nextHistory
        }
        assertEquals(ModalHistoryReducer.MAX_ENTRIES - 1, history.currentIndex)
    }

    @Test
    fun navigate_normalizesOutOfRangeCurrentIndex() {
        val history = ModalHistoryState(
            entries = listOf(
                ModalHistoryEntry(id = "a", loadRevision = 1L, blocks = listOf(block("a"))),
                ModalHistoryEntry(id = "b", loadRevision = 2L, blocks = listOf(block("b")))
            ),
            currentIndex = 99
        )
        val (nextHistory, modal) = ModalHistoryReducer.navigate(history, -1)
        assertEquals(0, nextHistory.currentIndex)
        assertEquals("a", modal?.blocks?.single()?.id)
    }

    @Test
    fun navigate_movesIndexAndReplaysModal() {
        var history = ModalHistoryState()
        history = ModalHistoryReducer.pushSnapshot(history, openState("a", revision = 1L))
        history = ModalHistoryReducer.pushSnapshot(history, openState("b", revision = 2L))

        val (hOlder, modalOlder) = ModalHistoryReducer.navigate(history, -1)
        assertEquals(0, hOlder.currentIndex)
        assertEquals("a", modalOlder?.blocks?.single()?.id)
        assertEquals(1L, modalOlder?.loadRevision)

        val (hNewer, modalNewer) = ModalHistoryReducer.navigate(hOlder, +1)
        assertEquals(1, hNewer.currentIndex)
        assertEquals("b", modalNewer?.blocks?.single()?.id)
    }

    @Test
    fun deleteAt_removesCurrentAndShowsNeighbor() {
        var history = ModalHistoryState()
        history = ModalHistoryReducer.pushSnapshot(history, openState("a", revision = 1L))
        history = ModalHistoryReducer.pushSnapshot(history, openState("b", revision = 2L))

        val (hAfter, modal) = ModalHistoryReducer.deleteAt(
            history,
            index = 1,
            currentModal = openState("b", revision = 2L)
        )
        assertEquals(1, hAfter.size)
        assertEquals("a", modal.blocks.single().id)
        assertTrue(modal.isOpen)
    }

    @Test
    fun deleteLastEntry_closesModal() {
        val history = ModalHistoryReducer.pushSnapshot(ModalHistoryState(), openState("a"))
        val (hAfter, modal) = ModalHistoryReducer.deleteAt(
            history,
            index = 0,
            currentModal = openState("a")
        )
        assertTrue(hAfter.entries.isEmpty())
        assertFalse(modal.isOpen)
    }
}
