package com.littlehelper.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LittleHelperFileSaver {

    fun saveDownloadedBytes(
        context: Context,
        bytes: ByteArray,
        displayName: String,
        storageFileName: String,
        mimeType: String,
    ): Result<SavedDownloadFile> = runCatching {
        val desired = DownloadFileNameBuilder.build(displayName, mimeType, storageFileName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStoreQ(context, bytes, desired, mimeType)
        } else {
            saveLegacy(context, bytes, desired, mimeType)
        }
    }

    fun saveScreenshot(context: Context, bitmap: Bitmap): Result<SavedDownloadFile> = runCatching {
        val displayName = "白板截图_${timestamp()}.jpg"
        val bytes = bitmapToJpeg(bitmap)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStoreQ(context, bytes, displayName, "image/jpeg")
        } else {
            saveLegacy(context, bytes, displayName, "image/jpeg")
        }
    }

    private fun saveViaMediaStoreQ(
        context: Context,
        bytes: ByteArray,
        desiredName: String,
        mimeType: String,
    ): SavedDownloadFile {
        val resolver = context.contentResolver
        val uniqueName = DownloadFileNameBuilder.resolveUniqueName(desiredName) { name ->
            queryDownloadExists(resolver, name)
        }
        val pendingValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, LittleHelperDownloadPaths.RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pendingValues)
            ?: throw IOException("无法写入下载目录")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
            } ?: throw IOException("无法打开输出流")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }

        resolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null)

        return SavedDownloadFile(
            displayName = uniqueName,
            mimeType = mimeType,
            uri = uri,
            absolutePath = null
        )
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(
        context: Context,
        bytes: ByteArray,
        desiredName: String,
        mimeType: String,
    ): SavedDownloadFile {
        val dir = LittleHelperDownloadPaths.downloadsDir()
        val uniqueName = DownloadFileNameBuilder.resolveUniqueName(desiredName) { name ->
            java.io.File(dir, name).exists()
        }
        val file = java.io.File(dir, uniqueName)
        file.writeBytes(bytes)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, uniqueName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.DATA, file.absolutePath)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: Uri.fromFile(file)
        return SavedDownloadFile(
            displayName = uniqueName,
            mimeType = mimeType,
            uri = uri,
            absolutePath = file.absolutePath
        )
    }

    private fun queryDownloadExists(resolver: android.content.ContentResolver, name: String): Boolean {
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val args = arrayOf(name, LittleHelperDownloadPaths.RELATIVE_PATH)
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            null
        ).use { cursor -> return cursor != null && cursor.moveToFirst() }
    }

    private fun bitmapToJpeg(bitmap: Bitmap): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
            throw IOException("图片压缩失败")
        }
        return stream.toByteArray()
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}

data class SavedDownloadFile(
    val displayName: String,
    val mimeType: String,
    val uri: Uri,
    val absolutePath: String?
)
