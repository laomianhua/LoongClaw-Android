package com.littlehelper.shell.modules

import android.net.Uri
import android.util.Log
import android.webkit.WebView

/**
 * 白板 WebView 内画廊 Deep Link（`littlehelper://gallery/...`）。
 * v2：长按触发 `littlehelper://gallery/download?index=N` 下载原图。
 */
object GalleryDeepLink {

    private const val SCHEME = "littlehelper"
    private const val HOST = "gallery"
    private const val TAG = "GalleryDeepLink"

    fun isDeepLink(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("$SCHEME://$HOST/download", ignoreCase = true)
    }

    fun handle(webView: WebView, url: String): Boolean {
        if (!isDeepLink(url)) return false
        val uri = Uri.parse(url.trim())
        return when (uri.path?.trim('/').orEmpty()) {
            "download" -> handleDownload(uri)
            else -> {
                Log.w(TAG, "Unknown gallery deep link: $url")
                false
            }
        }
    }

    private fun handleDownload(uri: Uri): Boolean {
        val index = uri.getQueryParameter("index")?.toIntOrNull() ?: return false
        if (index < 0) return false
        return CanvasWebViewBridge.requestGalleryDownload(index)
    }
}
