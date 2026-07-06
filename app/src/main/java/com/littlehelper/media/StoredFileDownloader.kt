package com.littlehelper.media

import android.content.Context
import com.littlehelper.shell.modules.StoredImageAsset
import com.littlehelper.shell.modules.StoredImageDownloadUrlResolver
import com.littlehelper.shell.transport.GatewayCanvasAuth
import com.littlehelper.shell.transport.GatewayConfig
import com.littlehelper.shell.transport.GatewayRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class StoredFileDownloader(
    private val gatewayBaseUrl: String = GatewayRuntime.httpBaseUrl(),
    private val uploadHost: String = GatewayRuntime.uploadHost(),
    private val client: OkHttpClient = downloadClient()
) {

    suspend fun downloadBytes(asset: StoredImageAsset): ByteArray = withContext(Dispatchers.IO) {
        val url = StoredImageDownloadUrlResolver.resolve(
            asset.downloadUrl,
            gatewayBaseUrl,
            uploadHost = uploadHost,
        )
        val token = GatewayCanvasAuth.resolveCanvasHttpToken()
        val requestBuilder = Request.Builder().url(url).get()
        if (token.isNotBlank() &&
            GatewayCanvasAuth.shouldInjectAuth(url, gatewayBaseUrl, mapOf("Authorization" to "Bearer $token"))
        ) {
            requestBuilder.header("Authorization", "Bearer $token")
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

            // 兜底：用 storageFileName 直接拼 18889 下载路径，比 fileId 更可靠
            val storageName = asset.storageFileName.trim()
            if (storageName.isNotEmpty() && storageName.contains('.')) {
                val fallbackUrl =
                    "http://$uploadHost:${GatewayConfig.UPLOAD_PORT}" +
                        "/file/download/${storageName}"
                if (fallbackUrl != StoredImageDownloadUrlResolver.resolve(
                        asset.downloadUrl,
                        gatewayBaseUrl,
                        uploadHost = uploadHost,
                    )
                ) {
                    val retry = runCatching {
                        downloadAndSave(context, asset.copy(downloadUrl = fallbackUrl))
                    }
                    if (retry.isSuccess) return@withContext retry
                }
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

    companion object {
        /** 专为文件下载设计的短超时客户端，避免因上传超时（120s）导致用户等待过久。 */
        fun downloadClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

/** @deprecated 使用 [StoredFileDownloader] */
typealias StoredImageDownloader = StoredFileDownloader
