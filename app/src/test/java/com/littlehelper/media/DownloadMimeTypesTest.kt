package com.littlehelper.media

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadMimeTypesTest {

    @Test
    fun fromFileName_mapsCommonExtensions() {
        assertEquals("application/pdf", DownloadMimeTypes.fromFileName("report.pdf"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            DownloadMimeTypes.fromFileName("notes.docx"),
        )
        assertEquals("text/html", DownloadMimeTypes.fromFileName("page.html"))
    }

    @Test
    fun resolve_correctsDefaultImageMimeForNonImageExtension() {
        assertEquals(
            "application/pdf",
            DownloadMimeTypes.resolve("image/jpeg", "report.pdf"),
        )
    }

    @Test
    fun resolve_usesDeclaredWhenConsistent() {
        assertEquals("image/png", DownloadMimeTypes.resolve("image/png", "photo.png"))
    }

    @Test
    fun resolve_fallsBackToExtensionWhenBlank() {
        assertEquals("text/html", DownloadMimeTypes.resolve("", "index.html"))
    }
}
