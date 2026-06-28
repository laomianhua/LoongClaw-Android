package com.littlehelper.media

import android.os.Environment
import java.io.File

object LittleHelperDownloadPaths {

    const val FOLDER_NAME = "LittleHelper"
    const val RELATIVE_PATH = "Download/LittleHelper"

    fun downloadsDir(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            FOLDER_NAME
        ).apply { if (!exists()) mkdirs() }
}
