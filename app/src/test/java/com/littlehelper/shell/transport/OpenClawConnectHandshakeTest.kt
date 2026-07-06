package com.littlehelper.shell.transport

import com.google.gson.JsonParser
import com.littlehelper.BuildConfig
import com.littlehelper.settings.GatewayAuthMode
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
        assertEquals(BuildConfig.VERSION_NAME, client.get("version").asString)
        assertEquals(
            "openclaw-android/${BuildConfig.VERSION_NAME}",
            params.get("userAgent").asString
        )
        assertEquals("ui", client.get("mode").asString)
        assertEquals("android", client.get("deviceFamily").asString)
        assertEquals("operator", params.get("role").asString)
        val auth = params.getAsJsonObject("auth")
        assertEquals("clawbot-test-2024", auth.get("token").asString)
        assertTrue(!auth.has("password"))
        val device = params.getAsJsonObject("device")
        assertEquals("device-abc", device.get("id").asString)
        assertEquals("pub", device.get("publicKey").asString)
        assertEquals("sig", device.get("signature").asString)
        assertEquals("nonce-abc", device.get("nonce").asString)
    }

    @Test
    fun buildConnectRequest_passwordMode_omitsToken() {
        val config = GatewayConfig(
            host = "192.168.1.55",
            port = 18789,
            password = "secret",
            authMode = GatewayAuthMode.PASSWORD,
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
            authToken = "secret"
        )
        val auth = frame.getAsJsonObject("params").getAsJsonObject("auth")
        assertEquals("secret", auth.get("password").asString)
        assertTrue(!auth.has("token"))
    }

    @Test
    fun extractPolicy_readsHelloOkPolicyFields() {
        val payload = JsonParser.parseString(
            """
            {
              "type":"hello-ok",
              "policy":{
                "tickIntervalMs":15000,
                "maxPayload":26214400,
                "maxBufferedBytes":52428800
              }
            }
            """
        ).asJsonObject
        val policy = OpenClawConnectHandshake.extractPolicy(payload)
        assertEquals(15_000L, policy.tickIntervalMs)
        assertEquals(26_214_400L, policy.maxPayload)
        assertEquals(52_428_800L, policy.maxBufferedBytes)
    }

    @Test
    fun extractPolicy_usesDefaultTickWhenMissing() {
        val payload = JsonParser.parseString("""{"type":"hello-ok"}""").asJsonObject
        val policy = OpenClawConnectHandshake.extractPolicy(payload)
        assertEquals(
            OpenClawConnectHandshake.GatewayHelloPolicy.DEFAULT_TICK_INTERVAL_MS,
            policy.tickIntervalMs
        )
    }

    @Test
    fun parseConnectRetry_startupSidecars() {
        val error = JsonParser.parseString(
            """
            {
              "message":"UNAVAILABLE",
              "details":{"reason":"startup-sidecars","retryAfterMs":750}
            }
            """
        ).asJsonObject
        val hint = OpenClawConnectHandshake.parseConnectRetry(error)
        assertEquals(750L, hint?.retryAfterMs)
        assertEquals("startup-sidecars", hint?.reason)
    }

    @Test
    fun parseConnectRetry_returnsNullForPairing() {
        val error = JsonParser.parseString(
            """{"message":"pairing required","details":{"code":"PAIRING_REQUIRED"}}"""
        ).asJsonObject
        assertEquals(null, OpenClawConnectHandshake.parseConnectRetry(error))
    }

    @Test
    fun parseConnectRetry_returnsNullForNonRetryable() {
        val error = JsonParser.parseString(
            """{"message":"device signature invalid"}"""
        ).asJsonObject
        assertEquals(null, OpenClawConnectHandshake.parseConnectRetry(error))
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
        assertTrue(msg.contains("待配对"))
        assertTrue(msg.contains("abc123device"))
    }
}
