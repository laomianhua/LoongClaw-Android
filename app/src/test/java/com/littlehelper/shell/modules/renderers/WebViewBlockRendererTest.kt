package com.littlehelper.shell.modules.renderers

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WebViewBlockRendererTest {

    @Test
    fun buildWebViewLoadHeaders_injectsBearerWhenMissing() {
        val headers = buildWebViewLoadHeaders(JsonParser.parseString("{}").asJsonObject, "clawbot-test-2024")
        assertEquals("Bearer clawbot-test-2024", headers["Authorization"])
    }

    @Test
    fun buildWebViewLoadHeaders_preservesExistingAuthorization() {
        val data = JsonParser.parseString(
            """{"headers":{"Authorization":"Bearer custom-token"}}"""
        ).asJsonObject
        val headers = buildWebViewLoadHeaders(data, "clawbot-test-2024")
        assertEquals("Bearer custom-token", headers["Authorization"])
    }

    @Test
    fun buildWebViewLoadHeaders_skipsBlankToken() {
        val headers = buildWebViewLoadHeaders(JsonParser.parseString("{}").asJsonObject, "")
        assertFalse(headers.containsKey("Authorization"))
    }

    @Test
    fun mergeHeaders_preservesExistingAuthorization() {
        val merged = GatewayCanvasResourceLoader.mergeHeaders(
            mapOf("Authorization" to "Bearer from-page"),
            mapOf("Authorization" to "Bearer gateway")
        )
        assertEquals("Bearer from-page", merged["Authorization"])
    }
}
