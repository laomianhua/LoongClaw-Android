package com.littlehelper.shell.transport

import com.littlehelper.settings.GatewayConnectionSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayConfigTest {

    @Test
    fun unconfigured_isNotConnectable() {
        val config = GatewayConfig.unconfigured("agent:main:test")
        assertFalse(config.isConnectable)
        assertEquals("", config.host)
        assertEquals(GatewayConnectionSettings.DEFAULT_PORT, config.port)
        assertEquals("", config.httpBaseUrl())
    }

    @Test
    fun configuredHost_isConnectable() {
        val config = GatewayConfig(
            host = "10.0.0.1",
            port = 18789,
            password = "",
            mainSessionKey = "agent:main:abc",
        )
        assertTrue(config.isConnectable)
        assertEquals("http://10.0.0.1:18789", config.httpBaseUrl())
    }
}
