package com.littlehelper.media

/** 校验 HTTP 下载体是否为预期文件（防 HTML/JSON 误存导致「假成功」）。 */
object DownloadedFileValidator {

    fun looksLikeHtmlOrJson(bytes: ByteArray): Boolean {
        val head = bytes.take(64).toByteArray().decodeToString().trimStart()
        return head.startsWith("<!") || head.startsWith("<html", ignoreCase = true) ||
            head.startsWith("{") || head.startsWith("[")
    }

    /** 网关/代理返回的错误页；合法 `.html` 文件不算错误页。 */
    fun looksLikeErrorPage(bytes: ByteArray, expectedMime: String): Boolean {
        if (!looksLikeHtmlOrJson(bytes)) return false
        return !expectedMime.lowercase().startsWith("text/html")
    }

    fun isLikelyFile(bytes: ByteArray, mimeType: String): Boolean {
        if (bytes.isEmpty()) return false
        val normalized = mimeType.lowercase()
        if (looksLikeHtmlOrJson(bytes) && !normalized.startsWith("text/html")) return false
        return when (normalized) {
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif" -> isLikelyImage(bytes)
            "application/pdf" -> isLikelyPdf(bytes)
            "text/html", "text/plain", "text/markdown", "text/csv" -> true
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            -> isLikelyZip(bytes)
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

    private fun isLikelyZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() &&
            bytes[3] == 4.toByte()
}
