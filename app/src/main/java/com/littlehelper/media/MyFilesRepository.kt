package com.littlehelper.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

object MyFilesRepository {

    data class LocalDownloadFile(
        val id: Long,
        val name: String,
        val size: Long,
        val mimeType: String,
        val modifiedAt: Long,
        val uri: Uri,
        val absolutePath: String?
    )

    fun list(context: Context): List<LocalDownloadFile> {
        val fromStore = listFromMediaStore(context)
        val merged = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            fromStore
        } else {
            val fromDir = listFromLegacyDir().filter { legacy ->
                fromStore.none { it.name == legacy.name }
            }
            fromStore + fromDir
        }
        return merged.sortedByDescending { it.modifiedAt }
    }

    fun delete(context: Context, file: LocalDownloadFile): Boolean {
        if (file.id > 0L) {
            val deleted = context.contentResolver.delete(file.uri, null, null)
            if (deleted > 0) return true
        }
        return file.absolutePath?.let { path ->
            File(path).takeIf { it.exists() }?.delete() == true
        } ?: false
    }

    private fun listFromMediaStore(context: Context): List<LocalDownloadFile> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.MIME_TYPE,
            MediaStore.Downloads.DATE_MODIFIED,
            MediaStore.Downloads.DATA
        )
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        } else {
            "${MediaStore.Downloads.DATA} LIKE ?"
        }
        val args = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(LittleHelperDownloadPaths.RELATIVE_PATH)
        } else {
            arrayOf("%${File.separator}Download${File.separator}LittleHelper${File.separator}%")
        }
        val files = mutableListOf<LocalDownloadFile>()
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            "${MediaStore.Downloads.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)
            val modifiedIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
            val dataIdx = cursor.getColumnIndex(MediaStore.Downloads.DATA)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val name = cursor.getString(nameIdx) ?: continue
                val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                files.add(
                    LocalDownloadFile(
                        id = id,
                        name = name,
                        size = cursor.getLong(sizeIdx).coerceAtLeast(0L),
                        mimeType = cursor.getString(mimeIdx) ?: "application/octet-stream",
                        modifiedAt = cursor.getLong(modifiedIdx) * 1000L,
                        uri = uri,
                        absolutePath = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                    )
                )
            }
        }
        return files
    }

    private fun listFromLegacyDir(): List<LocalDownloadFile> {
        val dir = LittleHelperDownloadPaths.downloadsDir()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()?.mapNotNull { file ->
            if (!file.isFile) return@mapNotNull null
            LocalDownloadFile(
                id = -file.name.hashCode().toLong(),
                name = file.name,
                size = file.length(),
                mimeType = mimeFromName(file.name),
                modifiedAt = file.lastModified(),
                uri = Uri.fromFile(file),
                absolutePath = file.absolutePath
            )
        }.orEmpty()
    }

    private fun mimeFromName(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
}
