package com.littlehelper.shell.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WebViewJsResultParserGalleryTest {

    @Test
    fun parseStoredImageGallery_parsesItemsWithDisplayName() {
        val json = """
            {
              "title": "大胖相册",
              "items": [
                {
                  "fileId": "94b13de8",
                  "fileName": "94b13de811b14923.jpg",
                  "displayName": "大胖",
                  "mimeType": "image/jpeg",
                  "downloadUrl": "http://192.168.1.55:18889/file/download/94b13de811b14923.jpg",
                  "thumbUrl": "http://192.168.1.55:18889/file/download/94b13de811b14923.jpg"
                },
                {
                  "fileId": "a1b2c3d4",
                  "fileName": "a1b2c3d4.jpg",
                  "displayName": "大胖（沙发）",
                  "mimeType": "image/png",
                  "downloadUrl": "/__openclaw__/file/download/a1b2c3d4.jpg"
                }
              ]
            }
        """.trimIndent()

        val gallery = WebViewJsResultParser.parseStoredImageGallery(json)

        assertNotNull(gallery)
        assertEquals("大胖相册", gallery!!.title)
        assertEquals(2, gallery.items.size)
        assertEquals("大胖", gallery.items[0].displayName)
        assertEquals("94b13de811b14923.jpg", gallery.items[0].storageFileName)
        assertEquals("image/jpeg", gallery.items[0].mimeType)
        assertEquals("大胖（沙发）", gallery.items[1].displayName)
        assertEquals("image/png", gallery.items[1].mimeType)
    }

    @Test
    fun parseStoredImageGallery_fallsBackToFileNameWhenDisplayNameMissing() {
        val json = """
            {
              "title": "",
              "items": [
                {
                  "fileId": "x",
                  "fileName": "photo.jpg",
                  "mimeType": "image/jpeg",
                  "downloadUrl": "http://host/file/download/photo.jpg"
                }
              ]
            }
        """.trimIndent()

        val gallery = WebViewJsResultParser.parseStoredImageGallery(json)

        assertNotNull(gallery)
        assertEquals("photo.jpg", gallery!!.items.single().displayName)
        assertEquals("photo.jpg", gallery.items.single().storageFileName)
    }

    @Test
    fun parseStoredImageGallery_returnsNullWhenNoDownloadUrl() {
        val json = """
            {
              "title": "empty",
              "items": [
                { "fileId": "x", "fileName": "a.jpg", "downloadUrl": "" }
              ]
            }
        """.trimIndent()

        assertNull(WebViewJsResultParser.parseStoredImageGallery(json))
    }

    @Test
    fun parseStoredImageGallery_unwrapsQuotedJsonString() {
        val inner = """{"title":"t","items":[{"fileId":"1","fileName":"a.jpg","downloadUrl":"http://x/a.jpg"}]}"""
        val quoted = "\"${inner.replace("\"", "\\\"")}\""

        val gallery = WebViewJsResultParser.parseStoredImageGallery(quoted)

        assertNotNull(gallery)
        assertEquals("t", gallery!!.title)
        assertEquals(1, gallery.items.size)
    }

    @Test
    fun parseStoredImageGallery_infersMimeTypeFromFileNameWhenMissing() {
        val json = """
            {
              "title": "文件管理",
              "items": [
                {
                  "fileId": "abc",
                  "fileName": "abc_report.pdf",
                  "displayName": "报告",
                  "downloadUrl": "http://host/file/download/abc_report.pdf"
                }
              ]
            }
        """.trimIndent()

        val gallery = WebViewJsResultParser.parseStoredImageGallery(json)

        assertNotNull(gallery)
        assertEquals("application/pdf", gallery!!.items.single().mimeType)
    }
}
