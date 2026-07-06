package com.littlehelper.upload

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.littlehelper.shell.transport.GatewayClientIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class FileUploadResult(
    val fileId: String,
    val fileName: String,
    val size: Long,
    val path: String
)

class FileUploadManager(
    private val host: String = "",
    private val port: Int = UPLOAD_PORT,
    private val client: OkHttpClient = defaultClient(),
    private val gson: Gson = Gson()
) {

    suspend fun upload(bytes: ByteArray, fileName: String, mimeType: String): FileUploadResult =
        withContext(Dispatchers.IO) {
            val mediaType = (mimeType.ifBlank { "application/octet-stream" }).toMediaType()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("client", GatewayClientIdentity.CLIENT)
                .addFormDataPart(
                    "file",
                    fileName,
                    bytes.toRequestBody(mediaType)
                )
                .build()

            val request = Request.Builder()
                .url("http://$host:$port/upload")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = parseErrorMessage(responseBody) ?: "HTTP ${response.code}"
                    throw IOException(message)
                }
                parseUploadResult(responseBody, gson)
            }
        }

    companion object {
        const val UPLOAD_PORT = 18889

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        internal fun parseUploadResult(json: String, gson: Gson = Gson()): FileUploadResult {
            val obj = gson.fromJson(json, JsonObject::class.java)
                ?: throw IOException("empty upload response")
            val fileId = obj.get("fileId")?.asString?.trim().orEmpty()
            val fileName = obj.get("fileName")?.asString?.trim().orEmpty()
            val path = obj.get("path")?.asString?.trim().orEmpty()
            val size = when {
                obj.has("size") && !obj.get("size").isJsonNull -> obj.get("size").asLong
                else -> 0L
            }
            if (fileId.isBlank() || fileName.isBlank()) {
                throw IOException("invalid upload response")
            }
            return FileUploadResult(
                fileId = fileId,
                fileName = fileName,
                size = size,
                path = path
            )
        }

        internal fun parseErrorMessage(json: String, gson: Gson = Gson()): String? {
            if (json.isBlank()) return null
            return runCatching {
                gson.fromJson(json, JsonObject::class.java)?.get("error")?.asString
            }.getOrNull()
        }
    }

    private fun parseErrorMessage(json: String): String? = parseErrorMessage(json, gson)
}
