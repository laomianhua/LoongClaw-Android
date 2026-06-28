package com.littlehelper.media

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object SystemFileOpener {

    fun open(context: Context, file: MyFilesRepository.LocalDownloadFile) {
        val uri = shareableUri(context, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, file.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(com.littlehelper.R.string.my_files_open_with))
        )
    }

    fun shareableUri(context: Context, file: MyFilesRepository.LocalDownloadFile): android.net.Uri {
        val path = file.absolutePath
        if (!path.isNullOrBlank()) {
            val onDisk = File(path)
            if (onDisk.exists()) {
                return FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    onDisk
                )
            }
        }
        return file.uri
    }
}
