package com.littlehelper.shell.modules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmapCanvasInjectorTest {

    @Test
    fun shouldInject_mapCanvasPages() {
        assertTrue(
            AmapCanvasInjector.shouldInject(
                "http://192.168.1.55:18789/__openclaw__/canvas/map.littlehelper.html"
            )
        )
        assertTrue(
            AmapCanvasInjector.shouldInject(
                "http://192.168.1.55:18789/__openclaw__/canvas/route.html"
            )
        )
    }

    @Test
    fun shouldInject_anyCanvasExceptSpecPages() {
        assertTrue(
            AmapCanvasInjector.shouldInject(
                "http://192.168.1.55:18789/__openclaw__/canvas/huating-to-tiananmen.html"
            )
        )
        assertFalse(
            AmapCanvasInjector.shouldInject(
                "http://192.168.1.55:18789/__openclaw__/canvas/t1_static.html"
            )
        )
        assertFalse(
            AmapCanvasInjector.shouldInject(
                "http://192.168.1.55:18789/__openclaw__/canvas/webview_spec_test.html"
            )
        )
    }
}
