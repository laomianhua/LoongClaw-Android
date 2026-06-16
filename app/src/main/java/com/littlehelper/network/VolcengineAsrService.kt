package com.littlehelper.network

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.littlehelper.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 语音识别服务接口，方便未来切换 ASR 提供商。
 */
interface AsrService {
    /** 是否已配置必要的 Key；未配置时 [transcribe] 会立即返回 failure。 */
    fun hasConfig(): Boolean

    /**
     * 将本地录音文件转为文字。
     * @return [Result.success] 包含识别文本；[Result.failure] 包含可读错误信息。
     */
    suspend fun transcribe(audioFile: File): Result<String>
}

/**
 * 火山引擎（ByteDance）语音识别服务，对接"一句话识别"API。
 *
 * 需要在 local.properties（不提交 Git）中配置三个字段：
 *   VOLC_APPID=<你的 AppId>
 *   VOLC_TOKEN=<你的 Token>
 *   VOLC_CLUSTER=<集群名，例如 volcasr_default>
 *
 * API 文档参考：https://www.volcengine.com/docs/6561/80818
 */
class VolcengineAsrService(
    private val client: OkHttpClient = defaultClient()
) : AsrService {

    private val gson = Gson()

    override fun hasConfig(): Boolean =
        BuildConfig.VOLC_APPID.isNotBlank() &&
            BuildConfig.VOLC_TOKEN.isNotBlank() &&
            BuildConfig.VOLC_CLUSTER.isNotBlank()

    override suspend fun transcribe(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        if (!hasConfig()) {
            return@withContext Result.failure(
                IllegalStateException("请在 local.properties 配置 VOLC_APPID / VOLC_TOKEN / VOLC_CLUSTER")
            )
        }
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(
                IllegalStateException("录音文件为空，请重新录一遍")
            )
        }

        runCatching {
            val audioBase64 = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
                        val format = "wav"
                        val codec = "raw"

                        val token = BuildConfig.VOLC_TOKEN

                        val reqBody = VolcAsrRequest(
                            app = VolcApp(
                                appid = BuildConfig.VOLC_APPID,
                                token = token,
                                cluster = BuildConfig.VOLC_CLUSTER
                            ),
                            user = VolcUser(uid = "littlehelper"),
                            request = VolcReqMeta(
                                reqid = UUID.randomUUID().toString(),
                                nbest = 1,
                                showUtterances = false,
                                sequence = -1
                            ),
                            audio = VolcAudio(
                                format = format,
                                codec = codec,
                                rate = 16000,
                                bits = 16,
                                channel = 1,
                                encodeType = "base64",
                                data = audioBase64
                            )
                        )

            val jsonBody = gson.toJson(reqBody)
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(ASR_URL)
                .header("Authorization", "Bearer; $token")
                .post(jsonBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                android.util.Log.d("VolcengineAsr", "Response: $body")
                if (!response.isSuccessful) {
                    throw IllegalStateException(parseHttpError(response.code, body))
                }
                val parsed = gson.fromJson(body, VolcAsrResponse::class.java)
                if (parsed?.code != SUCCESS_CODE) {
                    throw IllegalStateException(
                        "语音识别失败（code=${parsed?.code}）：${parsed?.message ?: "未知错误"}"
                    )
                }
                val text = parsed.result?.joinToString("") { it.text.orEmpty() }?.trim().orEmpty()
                if (text.isEmpty()) throw IllegalStateException("没有识别到内容，请重新录一遍")
                text
            }
        }
    }

    private fun parseHttpError(code: Int, body: String): String {
        val apiMsg = runCatching {
            gson.fromJson(body, VolcAsrResponse::class.java)?.message
        }.getOrNull()
        return when {
            !apiMsg.isNullOrBlank() -> "火山引擎报错: $apiMsg"
            code == 401 -> "火山引擎 Token 无效，请检查 VOLC_TOKEN"
            code == 403 -> "火山引擎 Token 无效 (403)，请检查 VOLC_TOKEN"
            code in 500..599 -> "语音识别服务暂时不可用，请稍后再试"
            else -> "转文字失败（HTTP $code），请重录一遍"
        }
    }

    // ── Request models ────────────────────────────────────────────────────────

    private data class VolcAsrRequest(
        val app: VolcApp,
        val user: VolcUser,
        val request: VolcReqMeta,
        val audio: VolcAudio
    )

    private data class VolcApp(val appid: String, val token: String, val cluster: String)

    private data class VolcUser(val uid: String)

    private data class VolcReqMeta(
        val reqid: String,
        val nbest: Int,
        @SerializedName("show_utterances") val showUtterances: Boolean,
        val sequence: Int
    )

    private data class VolcAudio(
        val format: String,
        val codec: String,
        val rate: Int = 16000,
        val bits: Int = 16,
        val channel: Int = 1,
        @SerializedName("encode_type") val encodeType: String,
        val data: String
    )

    // ── Response models ───────────────────────────────────────────────────────

    private data class VolcAsrResponse(
        val code: Int?,
        val message: String?,
        @SerializedName("result") val result: List<VolcUtterance>?
    )

    private data class VolcUtterance(val text: String?)

    companion object {
        private const val ASR_URL = "https://openspeech.bytedance.com/api/v1/asr"
        private const val SUCCESS_CODE = 1000

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
