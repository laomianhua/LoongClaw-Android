package com.littlehelper.upload

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadMessageMarkerTest {

    @Test
    fun append_withTextAndUpload() {
        val result = UploadMessageMarker.append(
            userText = "帮我分析这张图",
            fileId = "abc123",
            fileName = "photo.jpg"
        )
        assertEquals("帮我分析这张图 [upload:abc123:photo.jpg]", result)
    }

    @Test
    fun append_attachmentOnly() {
        val result = UploadMessageMarker.append(
            userText = "",
            fileId = "abc123",
            fileName = "photo.jpg"
        )
        assertEquals("[upload:abc123:photo.jpg]", result)
    }

    @Test
    fun stripForDisplay_removesMarker() {
        val text = "帮我分析这张图 [upload:abc123:photo.jpg]"
        assertEquals("帮我分析这张图", UploadMessageMarker.stripForDisplay(text))
    }

    @Test
    fun displayTextForChat_showsUserTextWithoutMarker() {
        val text = "帮我分析这张图 [upload:abc123:photo.jpg]"
        assertEquals("帮我分析这张图", UploadMessageMarker.displayTextForChat(text))
    }

    @Test
    fun displayTextForChat_attachmentOnlyShowsFileName() {
        val text = "[upload:abc123:report.pdf]"
        assertEquals("report.pdf", UploadMessageMarker.displayTextForChat(text))
    }
}
