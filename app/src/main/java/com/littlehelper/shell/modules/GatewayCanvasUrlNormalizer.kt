package com.littlehelper.shell.modules

/**
 * Canvas HTTP 路由 canonical 前缀是 `/__openclaw__/canvas/`（Gateway 实测无 alias）。
 * Agent 若误发 `/openclaw/canvas/...` 会 404；此处仅作 App 侧兜底映射，规范 URL 仍应由 Gateway 下发。
 *
 * `map.html` 常被 user-session Agent 覆写为街区示意图；真机地图固定走受保护副本
 * [CANONICAL_MAP_CANVAS]（Gateway dev 会话维护的 Canvas 高德瓦片版）。
 */
object GatewayCanvasUrlNormalizer {

    private val OPENCLAW_SLUG = Regex("(?<![_/])/openclaw/(?![_/])")

    /** Gateway 上由 Cursor/dev 维护、禁止 user Agent 覆写的地图页。 */
    const val CANONICAL_MAP_CANVAS = "/__openclaw__/canvas/map.littlehelper.html"

    private val MAP_CANVAS_ALIASES = setOf(
        "/__openclaw__/canvas/map.html",
        "/__openclaw__/canvas/map.htm",
        "/canvas/map.html",
        "/canvas/map.htm"
    )

    fun normalizeCanvasPath(path: String): String {
        val slugFixed = path.replace(OPENCLAW_SLUG, "/__openclaw__/")
        return redirectMapAliases(slugFixed) ?: slugFixed
    }

    fun normalizeCanvasUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return trimmed

        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd < 0) {
            return normalizeCanvasPath(trimmed)
        }

        val pathStart = trimmed.indexOf('/', schemeEnd + 3)
        if (pathStart < 0) return trimmed

        val path = trimmed.substring(pathStart)
        val normalizedPath = normalizeCanvasPath(path)
        return if (path == normalizedPath) {
            trimmed
        } else {
            trimmed.substring(0, pathStart) + normalizedPath
        }
    }

    /** 将 Agent 常发的 map.html 等别名重定向到受保护副本；保留 query 参数。 */
    internal fun redirectMapAliases(path: String): String? {
        val queryStart = path.indexOf('?')
        val pathOnly = if (queryStart >= 0) path.substring(0, queryStart) else path
        val query = if (queryStart >= 0) path.substring(queryStart) else ""

        if (pathOnly.equals(CANONICAL_MAP_CANVAS, ignoreCase = true)) return null

        val normalizedPath = if (pathOnly.startsWith('/')) pathOnly else "/$pathOnly"
        val aliasMatch = MAP_CANVAS_ALIASES.any { normalizedPath.equals(it, ignoreCase = true) }
        if (!aliasMatch) return null

        return CANONICAL_MAP_CANVAS + query
    }
}
