package com.littlehelper.media

/** 下载链路 MIME：从文件名推断，并在 Gateway 漏传/误传时纠正。 */
object DownloadMimeTypes {

    fun fromFileName(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return "application/octet-stream"
        return EXT_TO_MIME[ext] ?: "application/octet-stream"
    }

    /**
     * 合并 Canvas 声明的 mimeType 与文件名扩展名。
     * 缺失或误用默认 `image/jpeg`（非图片扩展名）时以扩展名为准。
     */
    fun resolve(declaredMime: String, fileName: String): String {
        val fromName = fromFileName(fileName)
        val declared = declaredMime.trim()
        if (declared.isEmpty()) return fromName
        if (declared.equals("image/jpeg", ignoreCase = true) &&
            fromName != "image/jpeg" &&
            fromName != "application/octet-stream"
        ) {
            return fromName
        }
        return declared
    }

    private val EXT_TO_MIME = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "bmp" to "image/bmp",
        "pdf" to "application/pdf",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "html" to "text/html",
        "htm" to "text/html",
        "txt" to "text/plain",
        "md" to "text/markdown",
        "csv" to "text/csv",
        "json" to "application/json",
    )
}
