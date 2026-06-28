package com.littlehelper.ui.attachment

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.littlehelper.attachment.PickedAttachment

object AttachmentFileReader {

    fun read(context: Context, uri: Uri, fallbackFileName: String? = null): PickedAttachment? {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val fileName = resolveDisplayName(contentResolver, uri)
            ?: fallbackFileName
            ?: "attachment"
        val bytes = contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
            ?: return null
        return PickedAttachment(
            bytes = bytes,
            fileName = fileName,
            mimeType = mimeType
        )
    }

    private fun resolveDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) {
                        return cursor.getString(index)?.takeIf { it.isNotBlank() }
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
