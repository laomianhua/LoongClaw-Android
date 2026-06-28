package com.littlehelper.shell.transport

import android.content.Context

/** Gateway Canvas WebView HTTP 鉴权（与 WS deviceToken 不同，静态 Canvas 认 shared token）。 */
object GatewayCanvasAuth {

    /** Canvas 静态文件路由用 BuildConfig 共享 token，不用 pairing 后的 deviceToken。 */
    fun resolveCanvasHttpToken(): String {
        val config = GatewayConfig.fromBuildConfig()
        return config.gatewayToken.ifBlank { config.password }
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
