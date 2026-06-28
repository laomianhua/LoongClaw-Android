package com.littlehelper.shell.transport

/**
 * Gateway device-auth 签名载荷（OpenClaw gateway-client device-auth v2）。
 */
object OpenClawDeviceAuth {

    private val OPERATOR_SCOPES = listOf("operator.read", "operator.write")

    fun resolveAuthToken(config: GatewayConfig, storedDeviceToken: String?): String {
        if (!storedDeviceToken.isNullOrBlank()) return storedDeviceToken
        return config.gatewayToken.ifBlank { config.password }
    }

    fun buildSignedDevice(
        identity: OpenClawDeviceIdentity,
        config: GatewayConfig,
        challenge: OpenClawConnectHandshake.Challenge,
        authToken: String,
        identityStore: OpenClawDeviceIdentityStore
    ): SignedDeviceConnect {
        val signedAtMs = System.currentTimeMillis()
        val payload = buildPayloadV2(
            deviceId = identity.deviceId,
            config = config,
            signedAtMs = signedAtMs,
            authToken = authToken,
            nonce = challenge.nonce
        )
        val signature = identityStore.signPayload(identity, payload)
        return SignedDeviceConnect(
            id = identity.deviceId,
            publicKey = identity.publicKeyBase64Url,
            signature = signature,
            signedAt = signedAtMs,
            nonce = challenge.nonce
        )
    }

    fun operatorScopes(): List<String> = OPERATOR_SCOPES

    internal fun buildPayloadV2ForTest(
        deviceId: String,
        config: GatewayConfig,
        signedAtMs: Long,
        authToken: String,
        nonce: String
    ): String = buildPayloadV2(deviceId, config, signedAtMs, authToken, nonce)

    private fun buildPayloadV2(
        deviceId: String,
        config: GatewayConfig,
        signedAtMs: Long,
        authToken: String,
        nonce: String
    ): String {
        val scopes = OPERATOR_SCOPES.joinToString(",")
        return listOf(
            "v2",
            deviceId,
            config.clientId,
            config.clientMode,
            config.connectRole,
            scopes,
            signedAtMs.toString(),
            authToken,
            nonce
        ).joinToString("|")
    }
}

data class SignedDeviceConnect(
    val id: String,
    val publicKey: String,
    val signature: String,
    val signedAt: Long,
    val nonce: String
)
