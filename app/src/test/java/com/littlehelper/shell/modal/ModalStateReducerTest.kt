package com.littlehelper.shell.modal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.google.gson.JsonObject

class ModalStateReducerTest {

    private fun block(id: String, type: String = "table") = ModalBlock(
        id = id,
        type = type,
        data = JsonObject()
    )

    @Test
    fun open_replacesBlocksAndOpens() {
        val next = ModalStateReducer.apply(
            current = ModalState(),
            action = ModalAction.OPEN,
            incoming = listOf(block("a"))
        )
        assertTrue(next.isOpen)
        assertEquals(1, next.blocks.size)
    }

    @Test
    fun update_mergesByIdWhenOpen() {
        val current = ModalState(isOpen = true, blocks = listOf(block("a"), block("b")))
        val next = ModalStateReducer.apply(
            current = current,
            action = ModalAction.UPDATE,
            incoming = listOf(block("b", "markdown"))
        )
        assertTrue(next.isOpen)
        assertEquals(2, next.blocks.size)
        assertEquals("markdown", next.blocks.first { it.id == "b" }.type)
    }

    @Test
    fun update_ignoredWhenClosed() {
        val current = ModalState(isOpen = false, blocks = emptyList())
        val next = ModalStateReducer.apply(
            current = current,
            action = ModalAction.UPDATE,
            incoming = listOf(block("a"))
        )
        assertFalse(next.isOpen)
        assertTrue(next.blocks.isEmpty())
    }

    @Test
    fun close_clearsState() {
        val current = ModalState(isOpen = true, blocks = listOf(block("a")), loadRevision = 3L)
        val next = ModalStateReducer.apply(
            current = current,
            action = ModalAction.CLOSE,
            incoming = emptyList()
        )
        assertFalse(next.isOpen)
        assertTrue(next.blocks.isEmpty())
        assertEquals(3L, next.loadRevision)
    }

    @Test
    fun open_incrementsLoadRevision() {
        val next = ModalStateReducer.apply(
            current = ModalState(loadRevision = 2L),
            action = ModalAction.OPEN,
            incoming = listOf(block("a"))
        )
        assertEquals(3L, next.loadRevision)
    }

    @Test
    fun update_incrementsLoadRevisionWhenOpen() {
        val current = ModalState(isOpen = true, blocks = listOf(block("a")), loadRevision = 5L)
        val next = ModalStateReducer.apply(
            current = current,
            action = ModalAction.UPDATE,
            incoming = listOf(block("a", "webview"))
        )
        assertEquals(6L, next.loadRevision)
    }
}
