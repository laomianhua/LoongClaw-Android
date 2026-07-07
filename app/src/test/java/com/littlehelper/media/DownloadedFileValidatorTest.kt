package com.littlehelper.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadedFileValidatorTest {

    @Test
    fun isLikelyImage_acceptsJpegMagicBytes() {
        val bytes = ByteArray(12) { index ->
            when (index) {
                0 -> 0xFF.toByte()
                1 -> 0xD8.toByte()
                2 -> 0xFF.toByte()
                3 -> 0xE0.toByte()
                else -> 0
            }
        }
        assertTrue(DownloadedFileValidator.isLikelyImage(bytes))
    }

    @Test
    fun isLikelyFile_rejectsHtmlForImageMimeType() {
        val html = "<!DOCTYPE html><html></html>".toByteArray()
        assertFalse(DownloadedFileValidator.isLikelyFile(html, "image/jpeg"))
    }

    @Test
    fun isLikelyFile_acceptsPdfMagicBytes() {
        val pdf = "%PDF-1.4\n".toByteArray()
        assertTrue(DownloadedFileValidator.isLikelyFile(pdf, "application/pdf"))
    }

    @Test
    fun isLikelyFile_acceptsHtmlWhenMimeIsTextHtml() {
        val html = "<!DOCTYPE html><html></html>".toByteArray()
        assertTrue(DownloadedFileValidator.isLikelyFile(html, "text/html"))
    }

    @Test
    fun isLikelyFile_acceptsDocxZipHeader() {
        val zip = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 0, 0)
        assertTrue(
            DownloadedFileValidator.isLikelyFile(
                zip,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ),
        )
    }

    @Test
    fun looksLikeErrorPage_allowsHtmlFiles() {
        val html = "<!DOCTYPE html><html></html>".toByteArray()
        assertFalse(DownloadedFileValidator.looksLikeErrorPage(html, "text/html"))
    }

    @Test
    fun looksLikeErrorPage_rejectsHtmlForPdf() {
        val html = "<!DOCTYPE html><html></html>".toByteArray()
        assertTrue(DownloadedFileValidator.looksLikeErrorPage(html, "application/pdf"))
    }
}
