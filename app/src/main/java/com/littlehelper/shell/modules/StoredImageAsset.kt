package com.littlehelper.shell.modules

/**
 * Gateway Canvas 约定：`window.__LITTLEHELPER_IMAGE__` / 画廊 `items[]`。
 */
data class StoredImageAsset(
    val fileId: String,
    /** Gateway 存储文件名，如 `94b13de811b14923.jpg`。 */
    val storageFileName: String,
    /** 人类可读标题，由 Gateway `displayName` 提供。 */
    val displayName: String,
    val mimeType: String,
    val downloadUrl: String
)
