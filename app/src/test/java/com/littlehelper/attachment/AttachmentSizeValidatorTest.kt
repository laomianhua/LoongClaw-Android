package com.littlehelper.attachment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentSizeValidatorTest {

    @Test
    fun imageWithinLimit() {
        assertTrue(
            AttachmentSizeValidator.isWithinLimit(
                sizeBytes = 9 * 1024 * 1024,
                mimeType = "image/jpeg",
                fileName = "photo.jpg"
            )
        )
    }

    @Test
    fun imageOverLimit() {
        assertFalse(
            AttachmentSizeValidator.isWithinLimit(
                sizeBytes = 11 * 1024 * 1024,
                mimeType = "image/png",
                fileName = "photo.png"
            )
        )
    }

    @Test
    fun pdfWithinLimit() {
        assertTrue(
            AttachmentSizeValidator.isWithinLimit(
                sizeBytes = 29 * 1024 * 1024,
                mimeType = "application/pdf",
                fileName = "doc.pdf"
            )
        )
    }

    @Test
    fun pdfOverLimit() {
        assertFalse(
            AttachmentSizeValidator.isWithinLimit(
                sizeBytes = 31 * 1024 * 1024,
                mimeType = "application/pdf",
                fileName = "doc.pdf"
            )
        )
    }

    @Test
    fun otherFileUsesTenMegabyteLimit() {
        assertTrue(
            AttachmentSizeValidator.isWithinLimit(
                sizeBytes = 10 * 1024 * 1024,
                mimeType = "text/plain",
                fileName = "notes.txt"
            )
        )
        assertFalse(
            AttachmentSizeValidator.isWithinLimit(
                sizeBytes = 10 * 1024 * 1024 + 1,
                mimeType = "application/zip",
                fileName = "archive.zip"
            )
        )
    }
}
