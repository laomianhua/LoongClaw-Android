package com.littlehelper.shell.transport

import android.content.Context
import com.littlehelper.settings.GatewayAuthMode

/** Gateway Canvas WebView HTTP 鉴权（静态 Canvas 用共享 token/password，不用 deviceToken）。 */
object GatewayCanvasAuth {

    fun resolveCanvasHttpToken(): String {
        val config = GatewayRuntime.configOrNull() ?: return ""
        if (!config.isConnectable) return ""
        return when (config.authMode) {
            GatewayAuthMode.TOKEN -> config.gatewayToken
            GatewayAuthMode.PASSWORD -> config.password
            GatewayAuthMode.NONE -> ""
        }
    }

    fun resolveBearerToken(context: Context): String = resolveCanvasHttpToken()

    fun authorizationHeader(context: Context): Map<String, String> {
        val token = resolveCanvasHttpToken()
        return if (token.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $token")
    }

    /** Canvas 静态路径只需 Bearer header；#token= 对 HTTP 静态路由无效。 */
    fun prepareCanvasLoadUrl(url: String): String = url

    /** 子资源与主文档：仅对 Gateway 同源请求注入 Bearer。 */
    fun shouldInjectAuth(url: String, gatewayBaseUrl: String, authHeaders: Map<String, String>): Boolean {
        if (authHeaders["Authorization"].isNullOrBlank()) return false
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }
        return isGatewayUrl(url, gatewayBaseUrl)
    }

    fun isGatewayUrl(url: String, gatewayBaseUrl: String): Boolean {
        if (url.isBlank() || gatewayBaseUrl.isBlank()) return false
        return url.startsWith(gatewayBaseUrl.trimEnd('/'), ignoreCase = true)
    }
}
