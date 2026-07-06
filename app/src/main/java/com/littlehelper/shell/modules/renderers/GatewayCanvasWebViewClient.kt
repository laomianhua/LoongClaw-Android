package com.littlehelper.shell.modules.renderers

import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.littlehelper.shell.modules.AmapCanvasInjector
import com.littlehelper.shell.modules.AmapDeepLink
import com.littlehelper.shell.modules.CanvasWebViewBridge
import com.littlehelper.shell.modules.GalleryDeepLink

internal class GatewayCanvasWebViewClient(
    gatewayBaseUrl: String,
    extraHeaders: Map<String, String> = emptyMap()
) : WebViewClient() {

    private val resourceLoader = GatewayCanvasResourceLoader(
        gatewayBaseUrl = gatewayBaseUrl,
        extraHeaders = extraHeaders
    )

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url?.toString().orEmpty()
        if (url.isNotBlank()) {
            resourceLoader.load(url, request.requestHeaders)?.let { return it }
        }
        return super.shouldInterceptRequest(view, request)
    }

    @Deprecated("Deprecated in API 21+")
    override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
        if (url.isNotBlank()) {
            resourceLoader.load(url, emptyMap())?.let { return it }
        }
        return super.shouldInterceptRequest(view, url)
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        Log.w(
            TAG,
            "HTTP ${errorResponse.statusCode} ${request.method} ${request.url} mainFrame=${request.isForMainFrame}"
        )
        super.onReceivedHttpError(view, request, errorResponse)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        Log.w(
            TAG,
            "Web error ${error.errorCode} ${error.description} ${request.url} mainFrame=${request.isForMainFrame}"
        )
        super.onReceivedError(view, request, error)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url?.toString().orEmpty()
        if (url.isNotBlank() && GalleryDeepLink.handle(view, url)) {
            return true
        }
        if (url.isNotBlank() && AmapDeepLink.handle(view, url)) {
            return true
        }
        return super.shouldOverrideUrlLoading(view, request)
    }

    @Deprecated("Deprecated in API 24+")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        if (url.isNotBlank() && GalleryDeepLink.handle(view, url)) {
            return true
        }
        if (url.isNotBlank() && AmapDeepLink.handle(view, url)) {
            return true
        }
        return super.shouldOverrideUrlLoading(view, url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        AmapCanvasInjector.injectIfNeeded(view, url)
        CanvasWebViewBridge.refreshAmapAvailability()
        CanvasWebViewBridge.refreshMediaState()
        view.postDelayed({ CanvasWebViewBridge.refreshMediaState() }, 350)
        view.postDelayed({ CanvasWebViewBridge.refreshMediaState() }, 1200)
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        Log.d(TAG, "Page started: $url")
        super.onPageStarted(view, url, favicon)
    }

    companion object {
        private const val TAG = "CanvasWebView"
    }
}
