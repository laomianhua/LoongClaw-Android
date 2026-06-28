package com.littlehelper.shell.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayCanvasAuthTest {

    @Test
    fun prepareCanvasLoadUrl_doesNotAppendTokenFragment() {
        val url = "http://192.168.1.55:18789/__openclaw__/canvas/xinyisheng-kline.html"
        assertEquals(url, GatewayCanvasAuth.prepareCanvasLoadUrl(url))
    }

    @Test
    fun shouldInjectAuth_sameGatewayHost() {
        val base = "http://192.168.1.55:18789"
        val headers = mapOf("Authorization" to "Bearer token")
        assertTrue(
            GatewayCanvasAuth.shouldInjectAuth(
                "$base/__openclaw__/canvas/leaflet.js",
                base,
                headers
            )
        )
    }

    @Test
    fun shouldInjectAuth_skipsExternalTileHost() {
        val base = "http://192.168.1.55:18789"
        val headers = mapOf("Authorization" to "Bearer token")
        assertFalse(
            GatewayCanvasAuth.shouldInjectAuth(
                "https://webrd01.is.autonavi.com/appmaptile?x=1&y=2&z=3",
                base,
                headers
            )
        )
    }

    @Test
    fun shouldInjectAuth_skipsWhenAuthorizationMissing() {
        assertFalse(
            GatewayCanvasAuth.shouldInjectAuth(
                "http://192.168.1.55:18789/__openclaw__/canvas/map.html",
                "http://192.168.1.55:18789",
                emptyMap()
            )
        )
    }
}
