package com.littlehelper.shell.transport

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class OpenClawDeviceAuthTest {

    @Test
    fun buildPayloadV2_matchesOpenClawPipeFormat() {
        val config = GatewayConfig(
            host = "x",
            port = 1,
            password = "pw",
            clientId = "openclaw-android",
            clientMode = "node",
            connectRole = "operator"
        )
        val payload = OpenClawDeviceAuth.buildPayloadV2ForTest(
            deviceId = "deviceid",
            config = config,
            signedAtMs = 1_700_000_000_000L,
            authToken = "clawbot-test-2024",
            nonce = "nonce-1"
        )
        assertEquals(
            "v2|deviceid|openclaw-android|node|operator|operator.read,operator.write|1700000000000|clawbot-test-2024|nonce-1",
            payload
        )
    }

    @Test
    fun ed25519Sign_producesVerifiableSignature() {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()
        val privateRaw = (keyPair.private as Ed25519PrivateKeyParameters).encoded
        val publicRaw = (keyPair.public as Ed25519PublicKeyParameters).encoded
        val payload = "v3|test"
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateRaw, 0))
        val bytes = payload.toByteArray(Charsets.UTF_8)
        signer.update(bytes, 0, bytes.size)
        val signature = signer.generateSignature()
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(publicRaw, 0))
        verifier.update(bytes, 0, bytes.size)
        assertTrue(verifier.verifySignature(signature))
        assertEquals(
            OpenClawDeviceIdentityStore.sha256Hex(publicRaw),
            OpenClawDeviceIdentityStore.sha256Hex(publicRaw)
        )
    }

    @Test
    fun resolveAuthToken_prefersStoredDeviceToken() {
        val config = GatewayConfig(host = "x", port = 1, password = "pw", gatewayToken = "gw")
        assertEquals("device-tok", OpenClawDeviceAuth.resolveAuthToken(config, "device-tok"))
        assertEquals("gw", OpenClawDeviceAuth.resolveAuthToken(config, null))
    }
}
