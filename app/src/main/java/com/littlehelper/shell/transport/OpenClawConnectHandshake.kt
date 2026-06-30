package com.littlehelper.shell.transport

import com.google.gson.JsonObject

/**
 * 构建 OpenClaw Gateway `connect` 请求帧（协议 v4）。
 *
 * - `client.mode` = `ui`，`role` = `operator`
 * - `auth.token`：已配对用 deviceToken，否则用 gateway 共享 token
 * - `device`：Ed25519 签 v3 payload（含 platform、deviceFamily 与 challenge nonce）
 */
object OpenClawConnectHandshake {

    data class Challenge(
        val nonce: String,
        val ts: Long
    )

    /** hello-ok.policy；未提供字段时使用客户端默认值。 */
    data class GatewayHelloPolicy(
        val tickIntervalMs: Long = DEFAULT_TICK_INTERVAL_MS,
        val maxPayload: Long? = null,
        val maxBufferedBytes: Long? = null
    ) {
        companion object {
            const val DEFAULT_TICK_INTERVAL_MS = 15_000L
        }
    }

    /** Gateway connect 返回 UNAVAILABLE 时的重试提示（如 sidecar 启动中）。 */
    data class ConnectRetryHint(
        val retryAfterMs: Long,
        val reason: String?
    )

    fun parseChallenge(payload: JsonObject?): Challenge? {
        if (payload == null) return null
        val nonce = payload.get("nonce")?.asString ?: return null
        val ts = payload.get("ts")?.asLong ?: System.currentTimeMillis()
        return Challenge(nonce = nonce, ts = ts)
    }

    fun buildConnectRequest(
        requestId: String,
        config: GatewayConfig,
        challenge: Challenge,
        signedDevice: SignedDeviceConnect,
        authToken: String
    ): JsonObject {
        val params = JsonObject().apply {
            addProperty("minProtocol", config.protocolVersion)
            addProperty("maxProtocol", config.protocolVersion)
            add("client", JsonObject().apply {
                addProperty("id", config.clientId)
                addProperty("version", config.clientVersion)
                addProperty("platform", OpenClawDeviceAuth.normalizeMetadataForAuth(config.platform))
                addProperty("mode", config.clientMode)
                val deviceFamily = OpenClawDeviceAuth.normalizeMetadataForAuth(config.deviceFamily)
                if (deviceFamily.isNotEmpty()) {
                    addProperty("deviceFamily", deviceFamily)
                }
            })
            addProperty("role", config.connectRole)
            add("scopes", com.google.gson.JsonArray().apply {
                OpenClawDeviceAuth.operatorScopes().forEach { add(it) }
            })
            add("caps", com.google.gson.JsonArray())
            add("commands", com.google.gson.JsonArray())
            add("permissions", JsonObject())
            add("auth", JsonObject().apply {
                addProperty("token", authToken)
                val sharedSecret = config.gatewayToken.ifBlank { config.password }
                if (sharedSecret.isNotBlank()) {
                    addProperty("password", config.password.ifBlank { sharedSecret })
                }
            })
            addProperty("locale", "zh-CN")
            addProperty("userAgent", "${config.clientId}/${config.clientVersion}")
            add("device", JsonObject().apply {
                addProperty("id", signedDevice.id)
                addProperty("publicKey", signedDevice.publicKey)
                addProperty("signature", signedDevice.signature)
                addProperty("signedAt", signedDevice.signedAt)
                addProperty("nonce", signedDevice.nonce)
            })
        }

        return JsonObject().apply {
            addProperty("type", "req")
            addProperty("id", requestId)
            addProperty("method", "connect")
            add("params", params)
        }
    }

    fun isHelloOk(responsePayload: JsonObject?): Boolean {
        return responsePayload?.get("type")?.asString == "hello-ok"
    }

    fun extractConnId(responsePayload: JsonObject?): String? {
        return responsePayload?.getAsJsonObject("server")?.get("connId")?.asString
    }

    fun extractPolicy(responsePayload: JsonObject?): GatewayHelloPolicy {
        val policy = responsePayload?.getAsJsonObject("policy") ?: return GatewayHelloPolicy()
        return GatewayHelloPolicy(
            tickIntervalMs = policy.get("tickIntervalMs")?.asLong
                ?: GatewayHelloPolicy.DEFAULT_TICK_INTERVAL_MS,
            maxPayload = policy.get("maxPayload")?.asLong,
            maxBufferedBytes = policy.get("maxBufferedBytes")?.asLong
        )
    }

    /**
     * 解析可重试的 connect UNAVAILABLE（如 `details.reason=startup-sidecars`）。
     * 返回 null 表示不应在握手阶段自动重试。
     */
    fun parseConnectRetry(error: JsonObject?): ConnectRetryHint? {
        if (error == null) return null
        if (isPairingError(error)) return null
        val details = error.getAsJsonObject("details")
        val reason = details?.get("reason")?.asString
        val message = error.get("message")?.asString.orEmpty()
        val code = details?.get("code")?.asString ?: error.get("code")?.asString
        val unavailable = message.contains("UNAVAILABLE", ignoreCase = true) ||
            code?.contains("UNAVAILABLE", ignoreCase = true) == true ||
            reason == "startup-sidecars"
        if (!unavailable) return null
        val retryAfterMs = details?.get("retryAfterMs")?.asLong
            ?: error.get("retryAfterMs")?.asLong
            ?: DEFAULT_CONNECT_RETRY_AFTER_MS
        return ConnectRetryHint(retryAfterMs = retryAfterMs.coerceAtLeast(0L), reason = reason)
    }

    fun extractDeviceToken(responsePayload: JsonObject?, role: String): String? {
        val auth = responsePayload?.getAsJsonObject("auth") ?: return null
        auth.get("deviceToken")?.asString?.takeIf { it.isNotBlank() }?.let { return it }
        auth.getAsJsonArray("deviceTokens")?.forEach { element ->
            val item = element.asJsonObject
            if (item.get("role")?.asString == role) {
                item.get("deviceToken")?.asString?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return null
    }

    fun isPairingError(error: JsonObject?): Boolean {
        if (error == null) return false
        val message = error.get("message")?.asString
        val code = error.getAsJsonObject("details")?.get("code")?.asString
            ?: error.get("code")?.asString
        return code?.contains("PAIR", ignoreCase = true) == true ||
            code == "NOT_PAIRED" ||
            message?.contains("pairing", ignoreCase = true) == true ||
            message?.contains("not approved", ignoreCase = true) == true ||
            message?.contains("NOT_PAIRED", ignoreCase = true) == true
    }

    fun formatConnectError(error: JsonObject?, deviceId: String? = null): String {
        if (error == null) return "connect rejected"
        val message = error.get("message")?.asString
        if (isPairingError(error)) {
            val idLine = deviceId?.takeIf { it.isNotBlank() }?.let { "\n设备 ID：$it" }.orEmpty()
            return "设备待配对：请在 Gateway Control UI 批准此设备$idLine"
        }
        return message ?: error.toString()
    }

    private const val DEFAULT_CONNECT_RETRY_AFTER_MS = 500L
}
