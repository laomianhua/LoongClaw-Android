package com.littlehelper.shell.modules

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.littlehelper.shell.modal.ModalSlot
import com.littlehelper.shell.modules.renderers.GatewayCanvasChromeClient
import com.littlehelper.shell.modules.renderers.GatewayCanvasWebViewClient
import com.littlehelper.shell.modules.renderers.buildWebViewLoadHeaders
import com.littlehelper.shell.modules.renderers.configureForCanvas
import com.littlehelper.shell.transport.GatewayCanvasAuth
import com.google.gson.JsonObject

/**
 * 全 App 唯一白板 WebView：tab 切换时复用实例，仅 [WebView.loadUrl]。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ModalSingleWebViewHost(
    activeSlot: ModalSlot?,
    gatewayBaseUrl: String,
    modifier: Modifier = Modifier,
) {
    var loadedUrl by remember { mutableStateOf<String?>(null) }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                configureForCanvas(scrollable = true)
                webViewClient = GatewayCanvasWebViewClient(
                    gatewayBaseUrl = gatewayBaseUrl,
                )
                webChromeClient = GatewayCanvasChromeClient()
            }
        },
        update = { webView ->
            val url = activeSlot?.url
            if (url.isNullOrBlank()) {
                loadedUrl = null
                return@AndroidView
            }
            if (loadedUrl == url) return@AndroidView
            loadedUrl = url
            CanvasWebViewBridge.attach(webView)
            val headers = buildWebViewLoadHeaders(
                JsonObject(),
                GatewayCanvasAuth.resolveCanvasHttpToken()
            )
            webView.clearCache(true)
            webView.loadUrl(url, headers)
        },
        onRelease = { webView ->
            CanvasWebViewBridge.detach(webView)
            webView.stopLoading()
            webView.destroy()
        }
    )
}
