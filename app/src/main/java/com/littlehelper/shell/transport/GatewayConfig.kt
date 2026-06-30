package com.littlehelper.shell.transport

import com.littlehelper.BuildConfig

data class GatewayConfig(
    val host: String,
    val port: Int,
    val password: String,
    val mainSessionKey: String = "agent:main:main",
    val talkMode: String = "ptt",
    val clientId: String = "openclaw-android",
    /** 带界面 operator 客户端；device-auth 使用 v3 签名。 */
    val clientMode: String = "ui",
    val connectRole: String = "operator",
    val platform: String = "android",
    val deviceFamily: String = "android",
    val clientVersion: String = "1.0.0",
    val gatewayToken: String = "",
    val protocolVersion: Int = 4
) {
    fun wsUrl(): String = "ws://$host:$port"

    /** Gateway HTTP 基址，用于 MODAL webview 相对路径拼接。 */
    fun httpBaseUrl(): String = "http://$host:$port"

    companion object {
        fun fromBuildConfig(): GatewayConfig = GatewayConfig(
            host = BuildConfig.OPENCLAW_GATEWAY_HOST,
            port = BuildConfig.OPENCLAW_GATEWAY_PORT,
            password = BuildConfig.OPENCLAW_GATEWAY_PASSWORD,
            gatewayToken = BuildConfig.OPENCLAW_GATEWAY_TOKEN
        )
    }
}
