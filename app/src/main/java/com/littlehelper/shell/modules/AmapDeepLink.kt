package com.littlehelper.shell.modules

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 白板 WebView 内高德原生 URI 拦截与唤起。
 *
 * Canvas 页通过 `location.href = "androidamap://..."` / `amapuri://route/plan/...` 触发；
 * 已装高德则打开 App，否则在同 WebView 加载 uri.amap.com H5 降级页。
 */
object AmapDeepLink {

    const val AMAP_PACKAGE = "com.autonavi.minimap"

    private val SCHEME_PREFIXES = listOf(
        "androidamap://",
        "amapuri://"
    )

    private const val TAG = "AmapDeepLink"

    fun isDeepLink(url: String): Boolean {
        val trimmed = url.trim()
        return SCHEME_PREFIXES.any { trimmed.startsWith(it, ignoreCase = true) }
    }

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(AMAP_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * @return true 表示已处理（唤起 App 或 WebView 降级），WebView 不应再加载原 URL
     */
    fun handle(webView: WebView, url: String): Boolean {
        if (!isDeepLink(url)) return false
        Log.i(TAG, "Intercept Amap deep link: $url")

        val context = webView.context.applicationContext
        if (isInstalled(context)) {
            if (launchNative(context, url)) {
                return true
            }
            Log.w(TAG, "Amap installed but launch failed, falling back to H5: $url")
        }

        val fallback = toWebFallbackUrl(url)
        if (fallback != null) {
            webView.loadUrl(fallback)
            return true
        }

        Log.w(TAG, "No H5 fallback for: $url")
        return false
    }

    fun launchNative(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setPackage(AMAP_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "launchNative failed: $url", e)
            false
        }
    }

    /** 将原生 scheme URL 转为高德 H5 降级页（uri.amap.com）。 */
    fun toWebFallbackUrl(url: String): String? {
        val trimmed = url.trim()
        val (host, path) = parseHostAndPath(trimmed)
        val query = parseQuery(trimmed)

        return when {
            host.equals("viewmap", ignoreCase = true) -> {
                val lat = query["lat"] ?: return null
                val lon = query["lon"] ?: return null
                val name = query["poiname"].orEmpty()
                buildMarkerUrl(lon, lat, name)
            }
            host.equals("navi", ignoreCase = true) -> {
                val lat = query["lat"] ?: return null
                val lon = query["lon"] ?: return null
                val name = query["poiname"].orEmpty()
                buildNavigationUrl(toLon = lon, toLat = lat, toName = name)
            }
            host.equals("route", ignoreCase = true) && path.contains("plan", ignoreCase = true) -> {
                val slat = query["slat"] ?: return null
                val slon = query["slon"] ?: return null
                val sname = query["sname"].orEmpty()
                val dlat = query["dlat"] ?: return null
                val dlon = query["dlon"] ?: return null
                val dname = query["dname"].orEmpty()
                val mode = travelModeFromT(query["t"])
                buildNavigationUrl(
                    fromLon = slon,
                    fromLat = slat,
                    fromName = sname,
                    toLon = dlon,
                    toLat = dlat,
                    toName = dname,
                    mode = mode
                )
            }
            else -> null
        }
    }

    internal fun parseHostAndPath(url: String): Pair<String, String> {
        val schemeBody = url.substringAfter("://", "")
        val slash = schemeBody.indexOf('/')
        val query = schemeBody.indexOf('?')
        val hostEnd = when {
            slash < 0 && query < 0 -> schemeBody.length
            slash < 0 -> query
            query < 0 -> slash
            else -> minOf(slash, query)
        }
        val host = schemeBody.substring(0, hostEnd)
        val path = when {
            slash < 0 -> ""
            else -> {
                val pathEnd = if (query >= 0) query else schemeBody.length
                schemeBody.substring(slash, pathEnd)
            }
        }
        return host to path
    }

    internal fun parseQuery(url: String): Map<String, String> {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return emptyMap()
        return url.substring(queryStart + 1)
            .split('&')
            .mapNotNull { part ->
                if (part.isEmpty()) return@mapNotNull null
                val eq = part.indexOf('=')
                if (eq < 0) {
                    decode(part) to ""
                } else {
                    decode(part.substring(0, eq)) to decode(part.substring(eq + 1))
                }
            }
            .toMap()
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private fun buildMarkerUrl(lon: String, lat: String, name: String): String {
        val base = "https://uri.amap.com/marker?position=$lon,$lat"
        return if (name.isBlank()) base else "$base&name=${encode(name)}"
    }

    private fun buildNavigationUrl(
        fromLon: String? = null,
        fromLat: String? = null,
        fromName: String? = null,
        toLon: String,
        toLat: String,
        toName: String,
        mode: String = "car"
    ): String {
        val to = formatNavPoint(toLon, toLat, toName)
        val from = if (fromLon != null && fromLat != null) {
            formatNavPoint(fromLon, fromLat, fromName.orEmpty())
        } else {
            null
        }
        return buildString {
            append("https://uri.amap.com/navigation?")
            if (from != null) {
                append("from=").append(encode(from))
                append("&")
            }
            append("to=").append(encode(to))
            append("&mode=").append(mode)
            append("&callnative=0")
        }
    }

    private fun formatNavPoint(lon: String, lat: String, name: String): String {
        return if (name.isBlank()) "$lon,$lat" else "$lon,$lat,$name"
    }

    /** 高德 `t` 参数 → uri.amap.com `mode` */
    internal fun travelModeFromT(t: String?): String {
        return when (t?.trim()) {
            "1" -> "bus"
            "2" -> "walk"
            "3" -> "ride"
            else -> "car"
        }
    }
}
