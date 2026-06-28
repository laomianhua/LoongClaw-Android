package com.littlehelper.shell.modules.renderers

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import com.google.gson.JsonObject
import com.littlehelper.shell.modules.CanvasWebViewBridge
import com.littlehelper.shell.modules.ModalCanvasUrlResolver
import com.littlehelper.shell.transport.GatewayCanvasAuth
import com.littlehelper.ui.theme.AppColors

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewBlockRenderer(
    data: JsonObject,
    gatewayBaseUrl: String,
    gatewayAuthToken: String = "",
    loadRevision: Long = 0L,
    modifier: Modifier = Modifier
) {
    val resolvedUrl = remember(data, gatewayBaseUrl, loadRevision) {
        ModalCanvasUrlResolver.resolve(data.get("url")?.asString, gatewayBaseUrl)
            ?.let { GatewayCanvasAuth.prepareCanvasLoadUrl(it) }
            ?.let { ModalCanvasUrlResolver.appendLoadRevision(it, loadRevision) }
    }
    val loadHeaders = remember(data, gatewayAuthToken) {
        buildWebViewLoadHeaders(data, gatewayAuthToken)
    }
    val cookies = remember(data) { data.get("cookies")?.takeIf { !it.isJsonNull }?.asString?.trim() }
    val layout = remember(data) { parseWebViewLayoutConfig(data) }
    val extraHeaders = remember(data) { readHeaders(data) }

    if (resolvedUrl.isNullOrBlank()) {
        WebViewErrorState(
            message = "webview 缺少有效 data.url",
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (layout.fillAvailableHeight) {
                        Modifier.fillMaxHeight()
                    } else {
                        Modifier.height(layout.fixedHeight ?: 420.dp)
                    }
                )
        )
        return
    }

    val requestKey = remember(resolvedUrl, loadHeaders, loadRevision) {
        "$loadRevision|$resolvedUrl|" + loadHeaders.entries.joinToString { "${it.key}=${it.value}" }
    }

    val sizeModifier = modifier
        .fillMaxWidth()
        .then(
            if (layout.fillAvailableHeight) {
                Modifier.fillMaxHeight()
            } else {
                Modifier.height(layout.fixedHeight ?: 420.dp)
            }
        )

    key(requestKey) {
        AndroidView(
            modifier = sizeModifier
                .background(Color.White, RoundedCornerShape(12.dp)),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    configureForCanvas(scrollable = layout.scrollable)
                    webViewClient = GatewayCanvasWebViewClient(
                        gatewayBaseUrl = gatewayBaseUrl,
                        authToken = gatewayAuthToken,
                        extraHeaders = extraHeaders
                    )
                    webChromeClient = GatewayCanvasChromeClient()
                }
            },
            update = { webView ->
                CanvasWebViewBridge.attach(webView)
                cookies?.takeIf { it.isNotEmpty() }?.let { cookieHeader ->
                    CookieManager.getInstance().setCookie(resolvedUrl, cookieHeader)
                    CookieManager.getInstance().flush()
                }
                webView.clearCache(true)
                webView.loadUrl(resolvedUrl, loadHeaders)
            },
            onRelease = { webView ->
                CanvasWebViewBridge.detach(webView)
                webView.stopLoading()
                webView.destroy()
            }
        )
    }
}

private fun readHeaders(data: JsonObject): Map<String, String> {
    val headersObj = data.getAsJsonObject("headers") ?: return emptyMap()
    val map = linkedMapOf<String, String>()
    headersObj.entrySet().forEach { entry ->
        val value = entry.value?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
        if (value.isNotEmpty()) {
            map[entry.key] = value
        }
    }
    return map
}

internal fun buildWebViewLoadHeaders(data: JsonObject, gatewayAuthToken: String): Map<String, String> {
    val headers = readHeaders(data).toMutableMap()
    if (gatewayAuthToken.isNotBlank() && !headers.containsKey("Authorization")) {
        headers["Authorization"] = "Bearer $gatewayAuthToken"
    }
    headers.putIfAbsent("Cache-Control", "no-cache, no-store")
    headers.putIfAbsent("Pragma", "no-cache")
    return headers
}

@SuppressLint("ClickableViewAccessibility")
private fun WebView.configureForCanvas(scrollable: Boolean) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.cacheMode = WebSettings.LOAD_NO_CACHE
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = false
    settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
    settings.allowContentAccess = true
    settings.allowFileAccess = false
    settings.allowFileAccessFromFileURLs = false
    settings.allowUniversalAccessFromFileURLs = false
    settings.setSupportZoom(false)
    settings.builtInZoomControls = false
    settings.displayZoomControls = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    }

    isVerticalScrollBarEnabled = scrollable
    isHorizontalScrollBarEnabled = scrollable
    overScrollMode = if (scrollable) View.OVER_SCROLL_IF_CONTENT_SCROLLS else View.OVER_SCROLL_NEVER
    ViewCompat.setNestedScrollingEnabled(this, scrollable)

    if (!scrollable) return

    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                var parent = view.parent
                while (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true)
                    parent = parent.parent
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                var parent = view.parent
                while (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(false)
                    parent = parent.parent
                }
            }
        }
        false
    }
}

private class GatewayCanvasChromeClient : WebChromeClient() {
    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
        Log.d(
            TAG,
            "JS ${message.messageLevel()} ${message.sourceId()}:${message.lineNumber()} ${message.message()}"
        )
        return super.onConsoleMessage(message)
    }

    companion object {
        private const val TAG = "CanvasWebView"
    }
}

@Composable
private fun WebViewErrorState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFFFFF3E0), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            fontSize = 13.sp,
            color = AppColors.textHint
        )
    }
}
