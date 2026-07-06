package com.littlehelper.shell.transport

import com.littlehelper.settings.GatewayAuthMode

/**
 * Gateway device-auth 签名载荷（OpenClaw gateway-client device-auth v3）。
 */
object OpenClawDeviceAuth {

    private val OPERATOR_SCOPES = listOf("operator.read", "operator.write")

    fun resolveAuthCredential(config: GatewayConfig, storedDeviceToken: String?): String {
        if (!storedDeviceToken.isNullOrBlank()) return storedDeviceToken
        return resolveSharedCredential(config)
    }

    fun resolveSharedCredential(config: GatewayConfig): String =
        when (config.authMode) {
            GatewayAuthMode.TOKEN -> config.gatewayToken
            GatewayAuthMode.PASSWORD -> config.password
            GatewayAuthMode.NONE -> ""
        }

    @Deprecated("Use resolveAuthCredential", ReplaceWith("resolveAuthCredential(config, storedDeviceToken)"))
    fun resolveAuthToken(config: GatewayConfig, storedDeviceToken: String?): String =
        resolveAuthCredential(config, storedDeviceToken)

    fun buildSignedDevice(
        identity: OpenClawDeviceIdentity,
        config: GatewayConfig,
        challenge: OpenClawConnectHandshake.Challenge,
        authToken: String,
        identityStore: OpenClawDeviceIdentityStore
    ): SignedDeviceConnect {
        val signedAtMs = System.currentTimeMillis()
        val payload = buildPayloadV3(
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

    /** 与 Gateway `normalizeDeviceMetadataForAuth` 一致：ASCII 大写转小写。 */
    internal fun normalizeMetadataForAuth(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        return buildString(trimmed.length) {
            for (ch in trimmed) {
                append(if (ch in 'A'..'Z') (ch.code + 32).toChar() else ch)
            }
        }
    }

    internal fun buildPayloadV3ForTest(
        deviceId: String,
        config: GatewayConfig,
        signedAtMs: Long,
        authToken: String,
        nonce: String
    ): String = buildPayloadV3(deviceId, config, signedAtMs, authToken, nonce)

    private fun buildPayloadV3(
        deviceId: String,
        config: GatewayConfig,
        signedAtMs: Long,
        authToken: String,
        nonce: String
    ): String {
        val scopes = OPERATOR_SCOPES.joinToString(",")
        return listOf(
            "v3",
            deviceId,
            config.clientId,
            config.clientMode,
            config.connectRole,
            scopes,
            signedAtMs.toString(),
            authToken,
            nonce,
            normalizeMetadataForAuth(config.platform),
            normalizeMetadataForAuth(config.deviceFamily)
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
