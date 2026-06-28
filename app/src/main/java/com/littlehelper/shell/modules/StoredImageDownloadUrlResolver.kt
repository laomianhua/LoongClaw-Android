package com.littlehelper.shell.modules

import com.littlehelper.upload.FileUploadManager
import com.littlehelper.BuildConfig

/**
 * 将 Agent 下发的 `downloadUrl` 解析为 App 可 GET 的绝对地址。
 * 优先 Gateway 主端口 `/__openclaw__/file/download/...`；`/files/{fileId}` 走 upload_server 备选。
 * 误将 `/__openclaw__/canvas/{file}` 写入 downloadUrl 时，改写到 upload 端口 18889 原图下载。
 */
object StoredImageDownloadUrlResolver {

    fun resolve(rawDownloadUrl: String, gatewayBaseUrl: String): String {
        val trimmed = normalizeHostPlaceholder(rawDownloadUrl.trim())
        require(trimmed.isNotEmpty()) { "downloadUrl 为空" }

        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return rewriteAbsoluteDownloadUrl(trimmed, gatewayBaseUrl)
        }

        if (trimmed.startsWith("/files/")) {
            return "http://${BuildConfig.OPENCLAW_GATEWAY_HOST}:${FileUploadManager.UPLOAD_PORT}$trimmed"
        }

        rewriteCanvasPathToUploadDownload(trimmed)?.let { return it }

        val base = gatewayBaseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "Gateway 基址未配置" }
        val path = if (trimmed.startsWith('/')) trimmed else "/$trimmed"
        return base + path
    }

    private fun rewriteAbsoluteDownloadUrl(absoluteUrl: String, gatewayBaseUrl: String): String {
        val schemeEnd = absoluteUrl.indexOf("://")
        if (schemeEnd < 0) return absoluteUrl
        val pathStart = absoluteUrl.indexOf('/', schemeEnd + 3)
        if (pathStart < 0) return absoluteUrl
        val path = absoluteUrl.substring(pathStart)
        if (path.startsWith("/files/")) {
            return "http://${BuildConfig.OPENCLAW_GATEWAY_HOST}:${FileUploadManager.UPLOAD_PORT}$path"
        }
        rewriteCanvasPathToUploadDownload(path)?.let { return it }
        val base = gatewayBaseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return absoluteUrl
        if (path.contains("/__openclaw__/")) {
            return base + path
        }
        return absoluteUrl
    }

    /** Gateway 误把 gallery downloadUrl 指到 canvas 静态路径时，改走 18889 原图下载。 */
    internal fun rewriteCanvasPathToUploadDownload(path: String): String? {
        val normalized = path.substringBefore('?').substringBefore('#')
        if (!normalized.contains("/__openclaw__/canvas/")) return null
        val fileName = normalized.substringAfterLast('/').trim()
        if (fileName.isEmpty() || !fileName.contains('.')) return null
        val encoded = java.net.URLEncoder.encode(fileName, Charsets.UTF_8.name())
            .replace("+", "%20")
        return "http://${BuildConfig.OPENCLAW_GATEWAY_HOST}:${FileUploadManager.UPLOAD_PORT}/file/download/$encoded"
    }

    /** Gateway gallery 模板在 JS 替换 __HOST__ 前 App 可能已读到元数据。 */
    internal fun normalizeHostPlaceholder(url: String): String {
        if (!url.contains("__HOST__", ignoreCase = true)) return url
        return url.replace("__HOST__", BuildConfig.OPENCLAW_GATEWAY_HOST, ignoreCase = true)
    }
}
