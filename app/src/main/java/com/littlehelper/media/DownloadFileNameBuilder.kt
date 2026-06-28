package com.littlehelper.media

import android.webkit.MimeTypeMap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DownloadFileNameBuilder {

    fun build(
        displayName: String,
        mimeType: String,
        storageFileName: String = "",
        date: Date = Date()
    ): String {
        val base = sanitizeTitle(displayName)
            .ifBlank { sanitizeTitle(storageFileName.substringBeforeLast('.')) }
            .ifBlank { "file" }
        val dateSuffix = SimpleDateFormat("MMdd", Locale.US).format(date)
        val extension = extensionFor(mimeType, storageFileName)
        return "${base}_${dateSuffix}.${extension}"
    }

    fun resolveUniqueName(desiredName: String, exists: (String) -> Boolean): String {
        if (!exists(desiredName)) return desiredName
        val dot = desiredName.lastIndexOf('.')
        val stem = if (dot > 0) desiredName.substring(0, dot) else desiredName
        val ext = if (dot > 0) desiredName.substring(dot + 1) else ""
        var index = 2
        while (true) {
            val candidate = if (ext.isEmpty()) "${stem}_$index" else "${stem}_$index.$ext"
            if (!exists(candidate)) return candidate
            index++
        }
    }

    internal fun sanitizeTitle(raw: String): String =
        raw.trim().replace(Regex("""[\\/:*?"<>|]"""), "_")

    internal fun extensionFor(mimeType: String, storageFileName: String): String {
        storageFileName.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() }?.let {
            return it
        }
        return when (mimeType.lowercase(Locale.US)) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "application/pdf" -> "pdf"
            else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
        }
    }
}
