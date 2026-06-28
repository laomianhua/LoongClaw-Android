package com.littlehelper.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class DownloadFileNameBuilderTest {

    @Test
    fun build_usesDisplayNameAndDateSuffix() {
        val date = GregorianCalendar(2026, Calendar.JUNE, 27).time
        val name = DownloadFileNameBuilder.build(
            displayName = "大胖",
            mimeType = "image/jpeg",
            storageFileName = "94b13de811b14923.jpg",
            date = date
        )
        assertEquals("大胖_0627.jpg", name)
    }

    @Test
    fun build_pdfUsesPdfExtension() {
        val date = GregorianCalendar(2026, Calendar.JUNE, 27).time
        val name = DownloadFileNameBuilder.build(
            displayName = "持仓估值",
            mimeType = "application/pdf",
            storageFileName = "report.pdf",
            date = date
        )
        assertEquals("持仓估值_0627.pdf", name)
    }

    @Test
    fun resolveUniqueName_appendsCounterWhenExists() {
        val first = DownloadFileNameBuilder.resolveUniqueName("大胖_0627.jpg") { false }
        val second = DownloadFileNameBuilder.resolveUniqueName("大胖_0627.jpg") { name ->
            name == "大胖_0627.jpg"
        }
        assertEquals("大胖_0627.jpg", first)
        assertEquals("大胖_0627_2.jpg", second)
    }

    @Test
    fun sanitizeTitle_replacesIllegalCharacters() {
        val sanitized = DownloadFileNameBuilder.sanitizeTitle("a/b:c")
        assertTrue(sanitized.contains('/').not())
        assertTrue(sanitized.contains(':').not())
    }
}
