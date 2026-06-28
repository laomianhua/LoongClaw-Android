package com.littlehelper.shell.modules

import org.junit.Assert.assertEquals
import org.junit.Test

class StoredImageDownloadUrlResolverTest {

    private val gatewayBase = "http://192.168.1.55:18789"

    @Test
    fun resolve_relativeOpenClawDownloadPath() {
        val url = StoredImageDownloadUrlResolver.resolve(
            rawDownloadUrl = "/__openclaw__/file/download/abc123_photo.jpg",
            gatewayBaseUrl = gatewayBase
        )
        assertEquals(
            "http://192.168.1.55:18789/__openclaw__/file/download/abc123_photo.jpg",
            url
        )
    }

    @Test
    fun resolve_uploadServerFilesPath() {
        val url = StoredImageDownloadUrlResolver.resolve(
            rawDownloadUrl = "/files/94b13de8",
            gatewayBaseUrl = gatewayBase
        )
        assertEquals(
            "http://${com.littlehelper.BuildConfig.OPENCLAW_GATEWAY_HOST}:18889/files/94b13de8",
            url
        )
    }

    @Test
    fun resolve_canvasPath_rewritesToUploadPortDownload() {
        val url = StoredImageDownloadUrlResolver.resolve(
            rawDownloadUrl = "/__openclaw__/canvas/94b13de8_photo.jpg",
            gatewayBaseUrl = gatewayBase
        )
        assertEquals(
            "http://${com.littlehelper.BuildConfig.OPENCLAW_GATEWAY_HOST}:18889/file/download/94b13de8_photo.jpg",
            url
        )
    }

    @Test
    fun resolve_replacesHostPlaceholder() {
        val url = StoredImageDownloadUrlResolver.resolve(
            rawDownloadUrl = "http://__HOST__:18889/file/download/photo.jpg",
            gatewayBaseUrl = gatewayBase
        )
        assertEquals(
            "http://${com.littlehelper.BuildConfig.OPENCLAW_GATEWAY_HOST}:18889/file/download/photo.jpg",
            url
        )
    }

    @Test
    fun parseStoredImageAsset_fromEvaluateJavascriptResult() {
        val jsPayload = """{"fileId":"94b13de8","fileName":"photo.jpg","mimeType":"image/jpeg","downloadUrl":"/__openclaw__/file/download/x.jpg"}"""
        val wrapped = "\"${jsPayload.replace("\"", "\\\"")}\""
        val asset = WebViewJsResultParser.parseStoredImageAsset(wrapped)
        requireNotNull(asset)
        assertEquals("94b13de8", asset.fileId)
        assertEquals("photo.jpg", asset.displayName)
        assertEquals("photo.jpg", asset.storageFileName)
        assertEquals("/__openclaw__/file/download/x.jpg", asset.downloadUrl)
    }
}
