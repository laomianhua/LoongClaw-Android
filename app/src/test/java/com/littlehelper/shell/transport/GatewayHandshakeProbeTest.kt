package com.littlehelper.shell.transport

import com.littlehelper.settings.GatewayAuthMode
import com.littlehelper.settings.GatewayConnectionSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GatewayHandshakeProbeTest {

    @Test
    fun validateCredentials_tokenModeRequiresToken() {
        val config = GatewayConnectionSettings(
            host = "127.0.0.1",
            port = 18789,
            authMode = GatewayAuthMode.TOKEN,
            plainToken = "",
        ).toGatewayConfig()

        assertEquals("请填写 Token", GatewayHandshakeProbe.validateCredentials(config, storedDeviceToken = null))
    }

    @Test
    fun validateCredentials_passwordModeRequiresPassword() {
        val config = GatewayConnectionSettings(
            host = "127.0.0.1",
            port = 18789,
            authMode = GatewayAuthMode.PASSWORD,
            plainPassword = "",
        ).toGatewayConfig()

        assertEquals("请填写密码", GatewayHandshakeProbe.validateCredentials(config, storedDeviceToken = null))
    }

    @Test
    fun validateCredentials_skipsWhenDeviceTokenPresent() {
        val config = GatewayConnectionSettings(
            host = "127.0.0.1",
            port = 18789,
            authMode = GatewayAuthMode.TOKEN,
            plainToken = "",
        ).toGatewayConfig()

        assertNull(GatewayHandshakeProbe.validateCredentials(config, storedDeviceToken = "stored-token"))
    }
}
