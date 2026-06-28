package com.littlehelper.shell.modules

/** Gateway Canvas 约定：`window.__LITTLEHELPER_GALLERY__`（纵向画廊，长按下载）。 */
data class StoredImageGallery(
    val title: String,
    val items: List<StoredImageAsset>
)
