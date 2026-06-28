package com.littlehelper.media

import android.content.Context
import com.littlehelper.shell.modules.StoredImageAsset
import com.littlehelper.shell.modules.StoredImageDownloadUrlResolver
import com.littlehelper.shell.transport.GatewayCanvasAuth
import com.littlehelper.shell.transport.GatewayConfig
import com.littlehelper.upload.FileUploadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class StoredFileDownloader(
    private val gatewayBaseUrl: String = GatewayConfig.fromBuildConfig().httpBaseUrl(),
    private val authToken: String = GatewayCanvasAuth.resolveCanvasHttpToken(),
    private val client: OkHttpClient = FileUploadManager.defaultClient()
) {

    suspend fun downloadBytes(asset: StoredImageAsset): ByteArray = withContext(Dispatchers.IO) {
        val url = StoredImageDownloadUrlResolver.resolve(asset.downloadUrl, gatewayBaseUrl)
        val requestBuilder = Request.Builder().url(url).get()
        if (authToken.isNotBlank() &&
            GatewayCanvasAuth.shouldInjectAuth(url, gatewayBaseUrl, mapOf("Authorization" to "Bearer $authToken"))
        ) {
            requestBuilder.header("Authorization", "Bearer $authToken")
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.bytes()
            if (!response.isSuccessful || body == null) {
                throw IOException("下载失败 HTTP ${response.code}")
            }
            if (DownloadedFileValidator.looksLikeHtmlOrJson(body)) {
                throw IOException("下载内容无效（可能是 HTML/JSON 错误页）")
            }
            if (!DownloadedFileValidator.isLikelyFile(body, asset.mimeType)) {
                throw IOException("下载内容不是有效文件")
            }
            body
        }
    }

    suspend fun downloadToDownloads(context: Context, asset: StoredImageAsset): Result<SavedDownloadFile> =
        withContext(Dispatchers.IO) {
            val primary = runCatching { downloadAndSave(context, asset) }
            if (primary.isSuccess) return@withContext primary

            val fileId = asset.fileId.trim()
            if (fileId.isNotEmpty() && !asset.downloadUrl.trim().startsWith("/files/")) {
                val fallback = asset.copy(downloadUrl = "/files/$fileId")
                val retry = runCatching { downloadAndSave(context, fallback) }
                if (retry.isSuccess) return@withContext retry
            }
            primary
        }

    private suspend fun downloadAndSave(context: Context, asset: StoredImageAsset): SavedDownloadFile {
        val bytes = downloadBytes(asset)
        return LittleHelperFileSaver.saveDownloadedBytes(
            context = context,
            bytes = bytes,
            displayName = asset.displayName,
            storageFileName = asset.storageFileName,
            mimeType = asset.mimeType
        ).getOrThrow()
    }
}

/** @deprecated 使用 [StoredFileDownloader] */
typealias StoredImageDownloader = StoredFileDownloader
