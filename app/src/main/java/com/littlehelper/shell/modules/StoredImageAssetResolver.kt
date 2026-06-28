package com.littlehelper.shell.modules

import com.google.gson.JsonObject
import com.littlehelper.shell.modal.ModalBlock
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 从 MODAL blocks 推断可下载的原图（Gateway 未注入 `__LITTLEHELPER_IMAGE__` 时的兜底）。
 */
object StoredImageAssetResolver {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val MARKDOWN_IMAGE = Regex("""!\[[^\]]*]\(([^)]+)\)""")

    fun fromModalBlocks(blocks: List<ModalBlock>, @Suppress("UNUSED_PARAMETER") gatewayBaseUrl: String): StoredImageAsset? {
        blocks.filter { it.type.equals("webview", ignoreCase = true) }
            .forEach { block ->
                val rawUrl = block.data.get("url")?.asString?.trim().orEmpty()
                if (rawUrl.contains("view_stored_img", ignoreCase = true)) {
                    parseViewStoredImgUrl(rawUrl, block.title)?.let { return it }
                }
            }
        blocks.forEach { block ->
            resolveFromBlock(block)?.let { return it }
        }
        return null
    }

    private fun resolveFromBlock(block: ModalBlock): StoredImageAsset? {
        return when (block.type.lowercase()) {
            "webview" -> resolveFromWebViewData(block.data, block.title)
            "markdown" -> resolveFromMarkdownData(block.data, block.title)
            else -> null
        }
    }

    private fun resolveFromWebViewData(data: JsonObject, title: String?): StoredImageAsset? {
        val rawUrl = data.get("url")?.asString?.trim().orEmpty()
        if (rawUrl.isEmpty()) return null
        parseViewStoredImgUrl(rawUrl, title)?.let { return it }
        parseDirectImageUrl(rawUrl, title)?.let { return it }
        return null
    }

    private fun resolveFromMarkdownData(data: JsonObject, title: String?): StoredImageAsset? {
        val content = sequenceOf("content", "text", "markdown", "body")
            .mapNotNull { key -> data.get(key)?.takeIf { !it.isJsonNull }?.asString?.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return null
        val imageUrl = MARKDOWN_IMAGE.find(content)?.groupValues?.get(1)?.trim().orEmpty()
        if (imageUrl.isEmpty()) return null
        return parseDirectImageUrl(imageUrl, title)
            ?: parseViewStoredImgUrl(imageUrl, title)
    }

    internal fun parseViewStoredImgUrl(rawUrl: String, title: String?): StoredImageAsset? {
        if (!rawUrl.contains("view_stored_img", ignoreCase = true)) return null
        val query = extractQuery(rawUrl)
        val fileName = query["f"]?.trim().orEmpty()
        if (fileName.isEmpty()) return null
        val displayName = query["name"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: title?.trim()?.takeIf { it.isNotEmpty() }
            ?: fileName.substringAfter('_', fileName)
        return buildAsset(storageFileName = fileName, displayName = displayName)
    }

    internal fun parseDirectImageUrl(rawUrl: String, title: String?): StoredImageAsset? {
        val path = extractPath(rawUrl)
        val fileName = path.substringAfterLast('/').trim()
        if (fileName.isEmpty()) return null
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension !in IMAGE_EXTENSIONS) return null
        val displayName = title?.trim()?.takeIf { it.isNotEmpty() } ?: fileName
        return buildAsset(storageFileName = fileName, displayName = displayName)
    }

    private fun buildAsset(storageFileName: String, displayName: String): StoredImageAsset {
        val fileId = storageFileName.substringBefore('_').ifBlank { storageFileName.take(8) }
        return StoredImageAsset(
            fileId = fileId,
            storageFileName = storageFileName,
            displayName = displayName,
            mimeType = mimeFromFileName(storageFileName),
            downloadUrl = "/__openclaw__/file/download/$storageFileName"
        )
    }

    private fun extractQuery(rawUrl: String): Map<String, String> {
        val query = extractQueryString(rawUrl) ?: return emptyMap()
        return query.split('&').mapNotNull { segment ->
            val idx = segment.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = decodeQueryComponent(segment.substring(0, idx))
            val value = decodeQueryComponent(segment.substring(idx + 1))
            key to value
        }.toMap()
    }

    private fun extractQueryString(rawUrl: String): String? {
        val withoutFragment = rawUrl.substringBefore('#')
        val queryStart = withoutFragment.indexOf('?')
        if (queryStart < 0) return null
        return withoutFragment.substring(queryStart + 1)
    }

    private fun extractPath(rawUrl: String): String {
        val withoutFragment = rawUrl.substringBefore('#').substringBefore('?')
        val schemeIdx = withoutFragment.indexOf("://")
        return if (schemeIdx >= 0) {
            withoutFragment.substring(schemeIdx + 3).substringAfter('/', "")
        } else {
            withoutFragment.trimStart('/')
        }
    }

    private fun decodeQueryComponent(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun mimeFromFileName(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            else -> "image/jpeg"
        }
    }
}
