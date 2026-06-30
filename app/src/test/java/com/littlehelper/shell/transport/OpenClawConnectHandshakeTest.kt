package com.littlehelper.shell.transport

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenClawConnectHandshakeTest {

    @Test
    fun buildConnectRequest_usesUiModeOperatorRoleAndSignedDevice() {
        val config = GatewayConfig(
            host = "192.168.1.55",
            port = 18789,
            password = "clawbot-test-2024",
            gatewayToken = "clawbot-test-2024"
        )
        val signedDevice = SignedDeviceConnect(
            id = "device-abc",
            publicKey = "pub",
            signature = "sig",
            signedAt = 1_781_874_000_000L,
            nonce = "nonce-abc"
        )
        val frame = OpenClawConnectHandshake.buildConnectRequest(
            requestId = "conn_1",
            config = config,
            challenge = OpenClawConnectHandshake.Challenge("nonce-abc", 1_781_874_000_000L),
            signedDevice = signedDevice,
            authToken = "clawbot-test-2024"
        )

        val params = frame.getAsJsonObject("params")
        val client = params.getAsJsonObject("client")
        assertEquals("openclaw-android", client.get("id").asString)
        assertEquals("ui", client.get("mode").asString)
        assertEquals("operator", params.get("role").asString)
        assertEquals("clawbot-test-2024", params.getAsJsonObject("auth").get("token").asString)
        val device = params.getAsJsonObject("device")
        assertEquals("device-abc", device.get("id").asString)
        assertEquals("pub", device.get("publicKey").asString)
        assertEquals("sig", device.get("signature").asString)
        assertEquals("nonce-abc", device.get("nonce").asString)
    }

    @Test
    fun extractDeviceToken_readsPrimaryAuthField() {
        val payload = JsonParser.parseString(
            """{"type":"hello-ok","auth":{"deviceToken":"dt-1","role":"operator"}}"""
        ).asJsonObject
        assertEquals("dt-1", OpenClawConnectHandshake.extractDeviceToken(payload, "operator"))
    }

    @Test
    fun formatConnectError_pairingHint() {
        val error = JsonParser.parseString(
            """{"message":"pairing required","details":{"code":"PAIRING_REQUIRED"}}"""
        ).asJsonObject
        val msg = OpenClawConnectHandshake.formatConnectError(error, "abc123device")
        assertTrue(msg.contains("配对"))
        assertTrue(msg.contains("abc123device"))
    }
}
