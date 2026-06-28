package com.littlehelper.shell.modules.renderers

import android.webkit.WebResourceResponse
import com.littlehelper.shell.transport.GatewayCanvasAuth
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 为 WebView 子资源（link/script/img 等）注入 Gateway Bearer，避免 401。
 */
internal class GatewayCanvasResourceLoader(
    private val gatewayBaseUrl: String,
    authToken: String,
    extraHeaders: Map<String, String> = emptyMap()
) {
    private val authHeaders = buildAuthHeaders(authToken, extraHeaders)

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun load(url: String, requestHeaders: Map<String, String>): WebResourceResponse? {
        if (!GatewayCanvasAuth.shouldInjectAuth(url, gatewayBaseUrl, authHeaders)) return null
        return runCatching {
            val requestBuilder = Request.Builder().url(url)
            mergeHeaders(requestHeaders, authHeaders).forEach { (key, value) ->
                requestBuilder.header(key, value)
            }
            val response = client.newCall(requestBuilder.build()).execute()
            val body = response.body ?: return null
            val rawContentType = response.header("Content-Type").orEmpty()
            val mimeType = rawContentType.substringBefore(';').trim().ifBlank {
                guessMimeType(url)
            }
            val encoding = response.header("Content-Encoding")?.takeIf { it.isNotBlank() } ?: "UTF-8"
            val responseHeaders = response.headers.toMultimap().mapValues { (_, values) ->
                values.firstOrNull().orEmpty()
            }
            WebResourceResponse(
                mimeType,
                encoding,
                response.code,
                response.message.ifBlank { "OK" },
                responseHeaders,
                body.byteStream()
            )
        }.getOrNull()
    }

    companion object {
        internal fun buildAuthHeaders(
            authToken: String,
            extraHeaders: Map<String, String>
        ): Map<String, String> {
            val merged = extraHeaders.toMutableMap()
            if (authToken.isNotBlank() && !merged.containsKey("Authorization")) {
                merged["Authorization"] = "Bearer $authToken"
            }
            return merged
        }

        internal fun mergeHeaders(
            requestHeaders: Map<String, String>,
            authHeaders: Map<String, String>
        ): Map<String, String> {
            val merged = LinkedHashMap<String, String>()
            requestHeaders.forEach { (key, value) ->
                if (value.isNotBlank()) merged[key] = value
            }
            authHeaders.forEach { (key, value) ->
                if (value.isNotBlank() && !merged.containsKey(key)) {
                    merged[key] = value
                }
            }
            return merged
        }

        private fun guessMimeType(url: String): String {
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            return when {
                path.endsWith(".css") -> "text/css"
                path.endsWith(".js") -> "application/javascript"
                path.endsWith(".json") -> "application/json"
                path.endsWith(".svg") -> "image/svg+xml"
                path.endsWith(".png") -> "image/png"
                path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
                path.endsWith(".webp") -> "image/webp"
                path.endsWith(".woff2") -> "font/woff2"
                path.endsWith(".woff") -> "font/woff"
                path.endsWith(".html") || path.endsWith(".htm") -> "text/html"
                else -> "application/octet-stream"
            }
        }
    }
}
