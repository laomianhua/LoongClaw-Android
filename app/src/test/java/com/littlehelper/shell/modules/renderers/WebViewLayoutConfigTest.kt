package com.littlehelper.shell.modules.renderers

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewLayoutConfigTest {

    @Test
    fun default_longPage_fillsAvailableHeight() {
        val config = parseWebViewLayoutConfig(
            JsonObject().apply { addProperty("url", "http://example.com/page.html") }
        )
        assertTrue(config.scrollable)
        assertTrue(config.fillAvailableHeight)
        assertEquals(null, config.fixedHeight)
    }

    @Test
    fun heightDp_fixedViewport_stillScrollableInside() {
        val config = parseWebViewLayoutConfig(
            JsonObject().apply {
                addProperty("url", "http://example.com/chart.html")
                addProperty("heightDp", 420.0)
            }
        )
        assertTrue(config.scrollable)
        assertFalse(config.fillAvailableHeight)
        assertEquals(420f, config.fixedHeight?.value)
    }

    @Test
    fun scrollableFalse_forCharts() {
        val config = parseWebViewLayoutConfig(
            JsonObject().apply {
                addProperty("url", "http://example.com/chart.html")
                addProperty("heightDp", 420.0)
                addProperty("scrollable", false)
            }
        )
        assertFalse(config.scrollable)
        assertFalse(config.fillAvailableHeight)
    }

    @Test
    fun heightAlias_isTreatedAsHeightDp() {
        val config = parseWebViewLayoutConfig(
            JsonObject().apply {
                addProperty("url", "http://example.com/map.html")
                addProperty("height", 360.0)
                addProperty("scrollable", false)
            }
        )
        assertEquals(360f, config.fixedHeight?.value)
        assertFalse(config.fillAvailableHeight)
    }
}
