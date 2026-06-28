package com.littlehelper.shell.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayCanvasUrlNormalizerTest {

    @Test
    fun normalizeCanvasPath_fixesMissingUnderscoresAndMapAlias() {
        assertEquals(
            GatewayCanvasUrlNormalizer.CANONICAL_MAP_CANVAS,
            GatewayCanvasUrlNormalizer.normalizeCanvasPath("/openclaw/canvas/map.html")
        )
    }

    @Test
    fun normalizeCanvasPath_mapHtml_redirectsToProtectedCopy() {
        assertEquals(
            GatewayCanvasUrlNormalizer.CANONICAL_MAP_CANVAS,
            GatewayCanvasUrlNormalizer.normalizeCanvasPath("/__openclaw__/canvas/map.html")
        )
    }

    @Test
    fun normalizeCanvasPath_leavesNonMapPathUnchanged() {
        val path = "/__openclaw__/canvas/chart.html"
        assertEquals(path, GatewayCanvasUrlNormalizer.normalizeCanvasPath(path))
    }

    @Test
    fun normalizeCanvasUrl_fixesAbsoluteGatewayUrl() {
        assertEquals(
            "http://100.112.96.116:18789${GatewayCanvasUrlNormalizer.CANONICAL_MAP_CANVAS}",
            GatewayCanvasUrlNormalizer.normalizeCanvasUrl(
                "http://100.112.96.116:18789/openclaw/canvas/map.html"
            )
        )
    }

    @Test
    fun redirectMapAliases_preservesQueryString() {
        assertEquals(
            "${GatewayCanvasUrlNormalizer.CANONICAL_MAP_CANVAS}?zoom=15",
            GatewayCanvasUrlNormalizer.redirectMapAliases("/__openclaw__/canvas/map.html?zoom=15")
        )
    }
}
