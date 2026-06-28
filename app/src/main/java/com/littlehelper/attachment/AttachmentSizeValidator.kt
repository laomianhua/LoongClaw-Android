package com.littlehelper.attachment

object AttachmentSizeValidator {

    const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
    const val MAX_PDF_BYTES = 30L * 1024 * 1024
    const val MAX_OTHER_BYTES = 10L * 1024 * 1024

    fun maxBytesFor(mimeType: String, fileName: String): Long {
        val lowerMime = mimeType.lowercase()
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when {
            lowerMime == "application/pdf" || extension == "pdf" -> MAX_PDF_BYTES
            lowerMime.startsWith("image/") -> MAX_IMAGE_BYTES
            else -> MAX_OTHER_BYTES
        }
    }

    fun oversizeMessage(mimeType: String, fileName: String): String {
        val lowerMime = mimeType.lowercase()
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when {
            lowerMime == "application/pdf" || extension == "pdf" -> "PDF 不能超过 30MB"
            lowerMime.startsWith("image/") -> "图片不能超过 10MB"
            else -> "文件不能超过 10MB"
        }
    }

    fun isWithinLimit(sizeBytes: Int, mimeType: String, fileName: String): Boolean {
        return sizeBytes.toLong() <= maxBytesFor(mimeType, fileName)
    }
}
