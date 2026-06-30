package com.littlehelper.shell.transport

import com.google.gson.JsonObject

/**
 * 构建 OpenClaw Gateway `connect` 请求帧（协议 v4）。
 *
 * - `client.mode` = `ui`，`role` = `operator`
 * - `auth.token`：已配对用 deviceToken，否则用 gateway 共享 token
 * - `device`：Ed25519 签 v2 payload（含 challenge nonce）
 */
object OpenClawConnectHandshake {

    data class Challenge(
        val nonce: String,
        val ts: Long
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
                addProperty("platform", config.platform)
                addProperty("mode", config.clientMode)
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
}
