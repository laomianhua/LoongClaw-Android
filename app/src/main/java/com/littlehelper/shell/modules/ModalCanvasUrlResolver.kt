package com.littlehelper.shell.modules

/**
 * 将 Agent 下发的 Canvas URL 解析为 WebView 可加载的绝对地址。
 * Gateway 推荐相对路径 `/__openclaw__/canvas/<file>`；绝对 URL 也支持。
 * 误发 `/openclaw/...` 时由 [GatewayCanvasUrlNormalizer] 兜底纠正。
 */
object ModalCanvasUrlResolver {

    fun resolve(rawUrl: String?, gatewayBaseUrl: String): String? {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return rewriteAbsoluteCanvasUrl(trimmed, gatewayBaseUrl)
        }
        val base = gatewayBaseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return null
        val path = GatewayCanvasUrlNormalizer.normalizeCanvasPath(
            if (trimmed.startsWith('/')) trimmed else "/$trimmed"
        )
        return base + path
    }

    /**
     * Agent 常下发 `http://100.x:18789/__openclaw__/canvas/...`；手机实际 gatewayBaseUrl 可能不同。
     * 同路径 Canvas 统一改写到当前 Gateway 基址，避免 Tailscale/LAN 混用导致 WebView 加载失败。
     */
    internal fun rewriteAbsoluteCanvasUrl(absoluteUrl: String, gatewayBaseUrl: String): String {
        val normalized = GatewayCanvasUrlNormalizer.normalizeCanvasUrl(absoluteUrl)
        val schemeEnd = normalized.indexOf("://")
        if (schemeEnd < 0) return normalized
        val pathStart = normalized.indexOf('/', schemeEnd + 3)
        if (pathStart < 0) return normalized
        val path = GatewayCanvasUrlNormalizer.normalizeCanvasPath(normalized.substring(pathStart))
        if (!path.contains("/__openclaw__/canvas/")) return normalized
        val base = gatewayBaseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return normalized
        return base + path
    }

    /** 绕过 WebView / HTTP 对同一路径静态文件的缓存（Gateway 原地更新 HTML 时必需）。 */
    fun appendLoadRevision(url: String, loadRevision: Long): String {
        if (loadRevision <= 0L) return url
        val marker = "__lh_rev="
        if (url.contains(marker)) return url
        val separator = if (url.contains('?')) '&' else '?'
        return "$url$separator$marker$loadRevision"
    }
}
