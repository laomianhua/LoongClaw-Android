package com.littlehelper.media

/** 校验 HTTP 下载体是否为预期文件（防 HTML/JSON 误存导致「假成功」）。 */
object DownloadedFileValidator {

    fun looksLikeHtmlOrJson(bytes: ByteArray): Boolean {
        val head = bytes.take(64).toByteArray().decodeToString().trimStart()
        return head.startsWith("<!") || head.startsWith("<html", ignoreCase = true) ||
            head.startsWith("{") || head.startsWith("[")
    }

    fun isLikelyFile(bytes: ByteArray, mimeType: String): Boolean {
        if (bytes.isEmpty()) return false
        if (looksLikeHtmlOrJson(bytes)) return false
        return when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif" -> isLikelyImage(bytes)
            "application/pdf" -> isLikelyPdf(bytes)
            else -> bytes.size >= 4
        }
    }

    fun isLikelyImage(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return true
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return true
        }
        if (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte()
        ) {
            return true
        }
        if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        ) {
            return true
        }
        return false
    }

    private fun isLikelyPdf(bytes: ByteArray): Boolean =
        bytes.size >= 5 && bytes.copyOfRange(0, 5).decodeToString() == "%PDF-"
}
