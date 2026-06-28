package com.littlehelper.shell.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModalCanvasUrlResolverTest {

    @Test
    fun resolve_absoluteCanvasUrl_rewritesToGatewayBase() {
        val url = "http://192.168.1.55:18789/__openclaw__/canvas/documents/cv_001/index.html"
        assertEquals(
            "http://10.0.0.1:9999/__openclaw__/canvas/documents/cv_001/index.html",
            ModalCanvasUrlResolver.resolve(url, "http://10.0.0.1:9999")
        )
    }

    @Test
    fun resolve_absoluteNonCanvasUrl_isUnchanged() {
        val url = "http://192.168.1.55:18789/other/page.html"
        assertEquals(url, ModalCanvasUrlResolver.resolve(url, "http://10.0.0.1:9999"))
    }

    @Test
    fun resolve_relativePath_joinsGatewayBase() {
        val base = "http://192.168.1.55:18789"
        val path = "/__openclaw__/canvas/documents/cv_001/index.html"
        assertEquals(
            "http://192.168.1.55:18789/__openclaw__/canvas/documents/cv_001/index.html",
            ModalCanvasUrlResolver.resolve(path, base)
        )
    }

    @Test
    fun resolve_relativeWithoutLeadingSlash_joinsGatewayBase() {
        assertEquals(
            "http://192.168.1.55:18789/canvas/demo.html",
            ModalCanvasUrlResolver.resolve("canvas/demo.html", "http://192.168.1.55:18789/")
        )
    }

    @Test
    fun resolve_blank_returnsNull() {
        assertNull(ModalCanvasUrlResolver.resolve("", "http://192.168.1.55:18789"))
        assertNull(ModalCanvasUrlResolver.resolve(null, "http://192.168.1.55:18789"))
    }

    @Test
    fun appendLoadRevision_addsQueryParam() {
        val url = "http://192.168.1.55:18789/__openclaw__/canvas/xinyisheng-kline.html"
        assertEquals(
            "$url?__lh_rev=3",
            ModalCanvasUrlResolver.appendLoadRevision(url, 3L)
        )
    }

    @Test
    fun appendLoadRevision_zero_isNoOp() {
        val url = "http://example.com/chart.html"
        assertEquals(url, ModalCanvasUrlResolver.appendLoadRevision(url, 0L))
    }

    @Test
    fun resolve_openclawSlug_isNormalizedToProtectedMap() {
        assertEquals(
            "http://192.168.1.55:18789${GatewayCanvasUrlNormalizer.CANONICAL_MAP_CANVAS}",
            ModalCanvasUrlResolver.resolve("/openclaw/canvas/map.html", "http://192.168.1.55:18789")
        )
    }

    @Test
    fun resolve_webViewSpecTest_joinsGatewayBase() {
        assertEquals(
            "http://100.112.96.116:18789/__openclaw__/canvas/webview_spec_test.html",
            ModalCanvasUrlResolver.resolve(
                "/__openclaw__/canvas/webview_spec_test.html",
                "http://100.112.96.116:18789"
            )
        )
    }

    @Test
    fun rewriteAbsoluteCanvasUrl_usesGatewayBase() {
        assertEquals(
            "http://192.168.1.55:18789/__openclaw__/canvas/webview_spec_test.html",
            ModalCanvasUrlResolver.rewriteAbsoluteCanvasUrl(
                "http://100.112.96.116:18789/__openclaw__/canvas/webview_spec_test.html",
                "http://192.168.1.55:18789"
            )
        )
    }
}
