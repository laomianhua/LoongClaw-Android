package com.littlehelper.attachment

import java.util.Locale

enum class AttachmentKind {
    IMAGE,
    PDF,
    DOCUMENT
}

fun attachmentKindFor(mimeType: String, fileName: String): AttachmentKind {
    if (mimeType.startsWith("image/")) return AttachmentKind.IMAGE
    if (mimeType == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true)) {
        return AttachmentKind.PDF
    }
    return AttachmentKind.DOCUMENT
}

fun formatAttachmentSize(sizeBytes: Int): String {
    return when {
        sizeBytes >= 1024 * 1024 ->
            String.format(Locale.getDefault(), "%.1fMB", sizeBytes / (1024f * 1024f))
        sizeBytes >= 1024 ->
            String.format(Locale.getDefault(), "%.0fKB", sizeBytes / 1024f)
        else -> "${sizeBytes}B"
    }
}
