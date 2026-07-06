package com.littlehelper.network

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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
 * 火山引擎（ByteDance）语音识别服务（已停用；保留接口供未来切换）。
 */
class VolcengineAsrService(
    private val client: OkHttpClient = defaultClient()
) : AsrService {

    private val gson = Gson()

    override fun hasConfig(): Boolean = false

    override suspend fun transcribe(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        Result.failure(IllegalStateException("火山 ASR 未配置（当前版本已停用）"))
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
