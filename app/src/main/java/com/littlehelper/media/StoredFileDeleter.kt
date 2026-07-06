package com.littlehelper.media

import com.littlehelper.shell.transport.GatewayConfig
import com.littlehelper.shell.transport.GatewayRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class StoredFileDeleter(
    private val uploadHost: String = GatewayRuntime.uploadHost(),
    private val client: OkHttpClient = StoredFileDownloader.downloadClient(),
) {

    suspend fun deleteStorageFile(storageFileName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val name = storageFileName.trim()
        if (name.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("empty file name"))
        }
        val encoded = URLEncoder.encode(name, Charsets.UTF_8.name()).replace("+", "%20")
        val url =
            "http://$uploadHost:${GatewayConfig.UPLOAD_PORT}/file/delete/$encoded"
        runCatching {
            client.newCall(Request.Builder().url(url).post(EMPTY_BODY).build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("删除失败 HTTP ${response.code}")
                }
                if (body.isNotBlank()) {
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    if (json != null && json.optBoolean("ok", false)) {
                        return@use
                    }
                    val err = json?.optString("error").orEmpty()
                    if (err.isNotBlank()) {
                        throw IOException(err)
                    }
                }
            }
        }
    }

    companion object {
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
    }
}
