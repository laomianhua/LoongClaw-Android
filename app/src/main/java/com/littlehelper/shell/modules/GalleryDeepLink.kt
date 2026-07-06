package com.littlehelper.shell.modules

import android.util.Log
import android.webkit.WebView

/**
 * 白板 WebView 内画廊 Deep Link（`littlehelper://gallery/...`）。
 * - `download?index=N` — 由 App 原生请求 :18889 下载
 * - `delete?index=N` — 由 App 原生请求 :18889 删除（文件管理器）
 */
object GalleryDeepLink {

    private const val SCHEME = "littlehelper"
    private const val HOST = "gallery"
    private const val TAG = "GalleryDeepLink"
    private const val PREFIX = "$SCHEME://$HOST/"

    fun isDeepLink(url: String): Boolean {
        val path = galleryPath(url) ?: return false
        return path.equals("download", ignoreCase = true) || path.equals("delete", ignoreCase = true)
    }

    private fun galleryPath(url: String): String? {
        val trimmed = url.trim()
        val idx = trimmed.indexOf(PREFIX, ignoreCase = true)
        if (idx < 0) return null
        return trimmed.substring(idx + PREFIX.length).substringBefore('?').trim('/')
    }

    private fun queryIndex(url: String): Int? {
        val trimmed = url.trim()
        val query = trimmed.substringAfter('?', "")
        if (query.isEmpty() || query == trimmed) return null
        return query.split('&')
            .asSequence()
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.size == 2 && it[0].equals("index", ignoreCase = true) }
            ?.get(1)
            ?.toIntOrNull()
    }

    fun handle(webView: WebView, url: String): Boolean {
        if (!isDeepLink(url)) return false
        return when (galleryPath(url)?.lowercase()) {
            "download" -> handleDownload(url)
            "delete" -> handleDelete(url)
            else -> {
                Log.w(TAG, "Unknown gallery deep link: $url")
                false
            }
        }
    }

    private fun handleDownload(url: String): Boolean {
        val index = queryIndex(url) ?: return false
        if (index < 0) return false
        return CanvasWebViewBridge.requestGalleryDownload(index)
    }

    private fun handleDelete(url: String): Boolean {
        val index = queryIndex(url) ?: return false
        if (index < 0) return false
        return CanvasWebViewBridge.requestGalleryDelete(index)
    }
}
