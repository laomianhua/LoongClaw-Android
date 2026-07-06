package com.littlehelper.shell.modules

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

internal object WebViewJsResultParser {

    private val gson = Gson()

    fun unwrapJsonString(result: String?): String? {
        if (result.isNullOrBlank() || result == "null") return null
        return runCatching { gson.fromJson(result, String::class.java) }.getOrElse {
            result.trim()
                .removeSurrounding("\"")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
    }

    fun parseStoredImageAsset(result: String?): StoredImageAsset? {
        val json = unwrapJsonString(result) ?: return null
        return runCatching {
            gson.fromJson(json, StoredImageAssetDto::class.java)?.toAsset()
        }.getOrNull()
    }

    fun parseStoredImageGallery(result: String?): StoredImageGallery? {
        val json = unwrapJsonString(result) ?: return null
        return runCatching {
            gson.fromJson(json, StoredImageGalleryDto::class.java)?.toGallery()
        }.getOrNull()
    }

    private data class StoredImageGalleryDto(
        val title: String? = null,
        val items: List<StoredImageAssetDto>? = null
    ) {
        fun toGallery(): StoredImageGallery? {
            val parsedItems = items.orEmpty().mapNotNull { it.toAsset() }
            if (parsedItems.isEmpty()) return null
            return StoredImageGallery(
                title = title?.trim().orEmpty(),
                items = parsedItems
            )
        }
    }

    private data class StoredImageAssetDto(
        val fileId: String? = null,
        val fileName: String? = null,
        val displayName: String? = null,
        val mimeType: String? = null,
        val downloadUrl: String? = null,
        val thumbUrl: String? = null
    ) {
        fun toAsset(): StoredImageAsset? {
            val url = StoredImageDownloadUrlResolver.normalizeHostPlaceholder(
                downloadUrl?.trim().orEmpty(),
                com.littlehelper.shell.transport.GatewayRuntime.uploadHost(),
            )
            if (url.isEmpty()) return null
            val storageName = fileName?.trim().orEmpty()
            val label = displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: storageName.substringBeforeLast('.').substringAfter('_', storageName)
            return StoredImageAsset(
                fileId = fileId?.trim().orEmpty(),
                storageFileName = storageName.ifBlank { label },
                displayName = label,
                mimeType = mimeType?.trim().orEmpty().ifBlank { "image/jpeg" },
                downloadUrl = url
            )
        }
    }
}
