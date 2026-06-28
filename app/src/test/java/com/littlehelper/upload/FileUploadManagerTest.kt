package com.littlehelper.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class FileUploadManagerTest {

    @Test
    fun parseUploadResult_success() {
        val json = """
            {
              "fileId": "550e8400-e29b-41d4-a716-446655440000",
              "fileName": "photo.jpg",
              "size": 12345,
              "path": "/home/user/.openclaw/uploads/550e8400-e29b-41d4-a716-446655440000.jpg"
            }
        """.trimIndent()

        val result = FileUploadManager.parseUploadResult(json)

        assertEquals("550e8400-e29b-41d4-a716-446655440000", result.fileId)
        assertEquals("photo.jpg", result.fileName)
        assertEquals(12345L, result.size)
    }

    @Test
    fun parseUploadResult_rejectsMissingFileId() {
        val json = """{"fileName":"a.txt","size":1,"path":"/tmp/a.txt"}"""
        assertThrows(IOException::class.java) {
            FileUploadManager.parseUploadResult(json)
        }
    }
}
