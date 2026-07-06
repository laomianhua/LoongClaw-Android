package com.littlehelper.shell.transport

import com.littlehelper.BuildConfig
import com.littlehelper.settings.GatewayAuthMode
import com.littlehelper.settings.GatewayConnectionSettings

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
    /** connect 帧 client.version / userAgent；与 APK [BuildConfig.VERSION_NAME] 一致。 */
    val clientVersion: String = BuildConfig.VERSION_NAME,
    val gatewayToken: String = "",
    val authMode: GatewayAuthMode = GatewayAuthMode.TOKEN,
    val protocolVersion: Int = 4
) {
    val isConnectable: Boolean
        get() = host.isNotBlank() && port in 1..65535

    fun wsUrl(): String = "ws://$host:$port"

    /** Gateway HTTP 基址，用于 MODAL webview 相对路径拼接。 */
    fun httpBaseUrl(): String = if (isConnectable) "http://$host:$port" else ""

    fun uploadBaseUrl(uploadPort: Int = UPLOAD_PORT): String =
        if (isConnectable) "http://$host:$uploadPort" else ""

    companion object {
        const val UPLOAD_PORT = 18889

        /** App 内尚未保存 Gateway 设置时的占位配置（不可连接）。 */
        fun unconfigured(mainSessionKey: String): GatewayConfig = GatewayConfig(
            host = "",
            port = GatewayConnectionSettings.DEFAULT_PORT,
            password = "",
            gatewayToken = "",
            authMode = GatewayAuthMode.TOKEN,
            mainSessionKey = mainSessionKey,
            clientVersion = BuildConfig.VERSION_NAME,
        )
    }
}
