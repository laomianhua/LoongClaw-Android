package com.littlehelper.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayConnectionSettingsTest {

    @Test
    fun isConfigured_requiresHostAndPort() {
        assertFalse(GatewayConnectionSettings().isConfigured)
        assertTrue(
            GatewayConnectionSettings(host = "192.168.1.1", port = 18789).isConfigured
        )
    }

    @Test
    fun toGatewayConfig_mapsAuthModeFields() {
        val settings = GatewayConnectionSettings(
            host = "10.0.0.1",
            port = 18789,
            authMode = GatewayAuthMode.TOKEN,
            plainToken = "tok",
            plainPassword = "pw",
        )
        val config = settings.toGatewayConfig()
        assertEquals("tok", config.gatewayToken)
        assertEquals("", config.password)
        assertEquals(GatewayAuthMode.TOKEN, config.authMode)
        assertEquals("agent:main:main", config.mainSessionKey)
    }

    @Test
    fun toGatewayConfig_alwaysUsesProductAgent() {
        val settings = GatewayConnectionSettings(
            host = "10.0.0.1",
            port = 18789,
            agentName = "laoxia",
        )
        assertEquals(AgentSessionPolicy.productSessionKey(), settings.toGatewayConfig().mainSessionKey)
    }

    @Test
    fun hasSameConnectionParamsAs_includesAgentName() {
        val left = GatewayConnectionSettings(host = "10.0.0.1", port = 18789, agentName = "laoxia")
        val right = left.copy(agentName = "erzi")
        assertFalse(left.hasSameConnectionParamsAs(right))
    }

    @Test
    fun hasSameAuthCredentialsAs_ignoresHostAndPort() {
        val left = GatewayConnectionSettings(
            host = "10.0.0.1",
            port = 18789,
            authMode = GatewayAuthMode.TOKEN,
            plainToken = "tok",
        )
        val right = left.copy(host = "192.168.1.1", port = 9999)
        assertTrue(left.hasSameAuthCredentialsAs(right))
        assertFalse(left.hasSameAuthCredentialsAs(right.copy(plainToken = "other")))
    }

    @Test
    fun formDefaults_hasPortAndTokenMode() {
        val defaults = GatewayConnectionSettings.formDefaults()
        assertEquals(18789, defaults.port)
        assertEquals(GatewayAuthMode.TOKEN, defaults.authMode)
        assertFalse(defaults.isConfigured)
    }
}
