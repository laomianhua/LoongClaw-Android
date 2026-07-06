package com.littlehelper.shell.modal

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModalSlotReducerTest {

    private val gatewayBase = "http://192.168.1.55:18789"

    private fun webviewBlock(
        id: String,
        url: String = "/__openclaw__/canvas/test.html",
        title: String? = null,
    ) = ModalBlock(
        id = id,
        type = "webview",
        title = title,
        data = JsonObject().apply { addProperty("url", url) }
    )

    @Test
    fun openOrUpdate_createsNewTab() {
        val next = ModalSlotReducer.openOrUpdate(
            slots = ModalSlotState(),
            blocks = listOf(webviewBlock("skill-weather", title = "天气")),
            loadRevision = 1L,
            gatewayBaseUrl = gatewayBase
        )
        assertEquals(1, next.slotMap.size)
        assertEquals("skill-weather", next.activeId)
        assertEquals(listOf("skill-weather"), next.mruList)
        assertTrue(next.slotMap["skill-weather"]!!.url.contains("__lh_rev=1"))
    }

    @Test
    fun openOrUpdate_sameIdRefreshesWithoutNewTab() {
        val first = ModalSlotReducer.openOrUpdate(
            ModalSlotState(),
            listOf(webviewBlock("skill-weather")),
            loadRevision = 1L,
            gatewayBaseUrl = gatewayBase
        )
        val second = ModalSlotReducer.openOrUpdate(
            first,
            listOf(webviewBlock("skill-weather")),
            loadRevision = 2L,
            gatewayBaseUrl = gatewayBase
        )
        assertEquals(1, second.slotMap.size)
        assertEquals(listOf("skill-weather"), second.mruList)
        assertTrue(second.slotMap["skill-weather"]!!.url.contains("__lh_rev=2"))
    }

    @Test
    fun openOrUpdate_secondIdMovesToMruFront() {
        val first = ModalSlotReducer.openOrUpdate(
            ModalSlotState(),
            listOf(webviewBlock("a")),
            loadRevision = 1L,
            gatewayBaseUrl = gatewayBase
        )
        val second = ModalSlotReducer.openOrUpdate(
            first,
            listOf(webviewBlock("b")),
            loadRevision = 2L,
            gatewayBaseUrl = gatewayBase
        )
        assertEquals(2, second.slotMap.size)
        assertEquals(listOf("b", "a"), second.mruList)
        assertEquals("b", second.activeId)
    }

    @Test
    fun visibleAndOverflowTabs() {
        var slots = ModalSlotState()
        repeat(7) { index ->
            slots = ModalSlotReducer.openOrUpdate(
                slots,
                listOf(webviewBlock("tab-$index")),
                loadRevision = index.toLong() + 1,
                gatewayBaseUrl = gatewayBase
            )
        }
        assertEquals(6, slots.visibleTabIds.size)
        assertEquals(1, slots.overflowTabIds.size)
        assertEquals("tab-6", slots.visibleTabIds.first())
        assertEquals("tab-0", slots.overflowTabIds.single())
    }

    @Test
    fun selectTab_promotesMru() {
        val slots = ModalSlotReducer.openOrUpdate(
            ModalSlotReducer.openOrUpdate(
                ModalSlotState(),
                listOf(webviewBlock("a")),
                1L,
                gatewayBase
            ),
            listOf(webviewBlock("b")),
            2L,
            gatewayBase
        )
        val selected = ModalSlotReducer.selectTab(slots, "a")
        assertEquals("a", selected.activeId)
        assertEquals(listOf("a", "b"), selected.mruList)
    }

    @Test
    fun closeTab_byId() {
        val slots = ModalSlotReducer.openOrUpdate(
            ModalSlotReducer.openOrUpdate(
                ModalSlotState(),
                listOf(webviewBlock("a")),
                1L,
                gatewayBase
            ),
            listOf(webviewBlock("b")),
            2L,
            gatewayBase
        )
        val closed = ModalSlotReducer.closeTab(slots, "a")
        assertEquals("b", closed.activeId)
        assertEquals(listOf("b"), closed.mruList)
        assertTrue(closed.slotMap.containsKey("b"))
    }

    @Test
    fun closeTab_withoutId_closesActive() {
        val slots = ModalSlotReducer.openOrUpdate(
            ModalSlotState(),
            listOf(webviewBlock("a")),
            1L,
            gatewayBase
        )
        val closed = ModalSlotReducer.closeTab(slots, null)
        assertTrue(closed.isEmpty)
        assertNull(closed.activeId)
    }

    @Test
    fun isNativeOnlyModal_trueForChartWithoutWebview() {
        val blocks = listOf(
            ModalBlock(
                id = "t4-line",
                type = "chart/line",
                data = JsonObject()
            )
        )
        assertTrue(ModalSlotReducer.isNativeOnlyModal(blocks))
    }

    @Test
    fun isNativeOnlyModal_falseWhenWebviewPresent() {
        assertFalse(
            ModalSlotReducer.isNativeOnlyModal(listOf(webviewBlock("t1")))
        )
    }

    @Test
    fun findPrimaryWebViewBlock_ignoresBlankId() {
        val block = ModalSlotReducer.findPrimaryWebViewBlock(
            listOf(webviewBlock(""))
        )
        assertNull(block)
    }
}
