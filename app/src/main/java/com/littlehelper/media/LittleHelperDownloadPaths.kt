package com.littlehelper.media

import android.os.Environment
import java.io.File

object LittleHelperDownloadPaths {

    const val FOLDER_NAME = "LoongClaw"
    /** MediaStore 要求 indexed 目录路径以 `/` 结尾。 */
    const val RELATIVE_PATH = "Download/LoongClaw/"

    fun downloadsDir(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            FOLDER_NAME
        ).apply { if (!exists()) mkdirs() }
}
