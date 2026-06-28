package com.littlehelper.network



import android.util.Log

import com.littlehelper.BuildConfig

import kotlinx.coroutines.CompletableDeferred

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.TimeoutCancellationException

import kotlinx.coroutines.withContext

import kotlinx.coroutines.withTimeout

import okhttp3.OkHttpClient

import okhttp3.Request

import okhttp3.Response

import okhttp3.WebSocket

import okhttp3.WebSocketListener

import okio.ByteString

import java.io.ByteArrayOutputStream

import java.util.UUID

import java.util.concurrent.TimeUnit

import java.util.concurrent.atomic.AtomicBoolean



/**

 * 火山引擎 v2 流式语音识别（WebSocket）。

 *

 * 文档：https://www.volcengine.com/docs/6561/80818

 * 大模型 v3（推荐迁移）：https://www.volcengine.com/docs/6561/1354869

 *

 * 分包建议：200ms/包（16kHz mono 16-bit ≈ 6400 bytes），发包间隔 100~200ms。

 */

class VolcengineStreamingAsrService(

    private val client: OkHttpClient = defaultClient()

) {

    private val config = VolcAsrConfig.fromBuildConfig()



    fun hasConfig(): Boolean = config.isValid()



    inner class LiveSession(

        private var onPartial: (String) -> Unit

    ) {

        private var session: StreamingSession? = null



        val isReady: Boolean get() = session?.isReady == true



        val latestPartial: String get() = session?.latestText.orEmpty()



        fun setOnPartial(callback: (String) -> Unit) {

            onPartial = callback

        }



        suspend fun start(): Result<Unit> = withContext(Dispatchers.IO) {

            if (!hasConfig()) {

                return@withContext Result.failure(

                    IllegalStateException(

                        "请在 local.properties 配置 VOLC_APPID / VOLC_TOKEN，以及 " +

                            "VOLC_RESOURCE_ID（大模型流式 v3）或 VOLC_STREAMING_CLUSTER（v2 流式）"

                    )

                )

            }

            runCatching {

                val connected = StreamingSession(config, client) { partial ->

                    if (partial.isNotBlank()) onPartial(partial)

                }

                session = connected

                withTimeout(CONNECT_TIMEOUT_MS) {

                    connected.connect()

                }

            }.onFailure { error ->

                Log.w(TAG, "ASR connect failed: ${error.message}")

                session?.closeQuietly()

                session = null

            }

        }



        fun feedPcm(pcm: ByteArray) {

            if (pcm.isEmpty()) return

            session?.enqueuePcm(pcm)

        }



        suspend fun finish(): Result<String> = withContext(Dispatchers.IO) {

            val active = session

            session = null

            if (active == null || !active.isReady) {

                active?.closeQuietly()

                return@withContext Result.failure(IllegalStateException("流式 ASR 未就绪"))

            }

            try {

                active.flushAndFinish()

                val text = withTimeout(FINISH_TIMEOUT_MS) {

                    active.awaitFinal().trim()

                }

                if (text.isNotBlank()) {

                    Result.success(text)

                } else {

                    val partial = active.latestText.trim()

                    if (partial.isNotBlank()) {

                        Result.success(partial)

                    } else {

                        Result.failure(IllegalStateException("没有识别到内容，请重新录一遍"))

                    }

                }

            } catch (e: TimeoutCancellationException) {

                val partial = active.latestText.trim()

                if (partial.isNotBlank()) {

                    Log.w(TAG, "ASR finish timeout, using latest partial (${partial.length} chars)")

                    Result.success(partial)

                } else {

                    Result.failure(IllegalStateException("流式识别超时，请重试"))

                }

            } catch (e: Exception) {

                Result.failure(e)

            } finally {

                active.closeQuietly()

            }

        }



        fun cancel() {

            session?.closeQuietly()

            session = null

        }

    }



    fun newLiveSession(onPartial: (String) -> Unit): LiveSession = LiveSession(onPartial)



    companion object {

        private const val TAG = "VolcStreamingAsr"

        /** 建连 + 首包 server ack；弱网适当放宽。 */

        private const val CONNECT_TIMEOUT_MS = 10_000L

        /** 负包发出后等待最终结果；二遍识别会多等一轮 nostream 复核。 */

        private const val FINISH_TIMEOUT_MS = 22_000L

        /** 16kHz mono 16-bit，200ms/包（文档明确：bigmodel_async 双向流式优化版 200ms 性能最优）。 */

        private const val PCM_FRAME_BYTES = 6400

        /** 建连前最多缓存约 10s PCM，避免无限堆积。 */

        private const val MAX_PENDING_PCM_BYTES = PCM_FRAME_BYTES * 50



        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()

            .connectTimeout(10, TimeUnit.SECONDS)

            .readTimeout(0, TimeUnit.MILLISECONDS)

            .writeTimeout(15, TimeUnit.SECONDS)

            .pingInterval(20, TimeUnit.SECONDS)

            .build()

    }



    private class StreamingSession(

        private val config: VolcAsrConfig,

        private val client: OkHttpClient,

        private val onPartial: (String) -> Unit

    ) {

        private var webSocket: WebSocket? = null

        private val ready = CompletableDeferred<Unit>()

        private val finished = CompletableDeferred<String>()

        @Volatile

        var latestText: String = ""

            private set

        private val closed = AtomicBoolean(false)

        private var audioSeq = 2

        private var lastAudioSent = false

        private val pendingBeforeReady = ByteArrayOutputStream()

        private val frameBuffer = ByteArrayOutputStream()

        private val pcmLock = Any()



        val isReady: Boolean get() = ready.isCompleted



        suspend fun connect() {

            val reqId = UUID.randomUUID().toString()

            val opened = CompletableDeferred<Unit>()

            val wsUrl = config.streamingWsUrl

            val requestBuilder = Request.Builder().url(wsUrl)

            if (config.useV3Streaming) {

                requestBuilder

                    .header("X-Api-App-Key", config.appId)

                    .header("X-Api-Access-Key", config.token)

                    .header("X-Api-Resource-Id", config.resourceId)

                    .header("X-Api-Connect-Id", reqId)

            } else {

                requestBuilder.header("Authorization", "Bearer; ${config.token}")

            }

            val request = requestBuilder.build()

            Log.i(

                TAG,

                "connect api=${if (config.useV3Streaming) "v3" else "v2"} url=$wsUrl " +

                    "resource=${config.resourceId.takeIf { it.isNotBlank() }.orEmpty()}"

            )



            webSocket = client.newWebSocket(request, object : WebSocketListener() {

                override fun onOpen(webSocket: WebSocket, response: Response) {

                    Log.i(TAG, "WebSocket connected logid=${response.header("X-Tt-Logid")}")

                    val init = if (config.useV3Streaming) {

                        VolcAsrBinaryProtocol.buildV3FullClientRequest(uid = "littlehelper")

                    } else {

                        VolcAsrBinaryProtocol.buildFullClientRequest(

                            appId = config.appId,

                            token = config.token,

                            cluster = config.streamingCluster,

                            reqId = reqId,

                            uid = "littlehelper"

                        )

                    }

                    webSocket.send(ByteString.of(*init))

                    opened.complete(Unit)

                }



                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {

                    handleMessage(bytes.toByteArray())

                }



                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {

                    Log.e(TAG, "WebSocket failure logid=${response?.header("X-Tt-Logid")}", t)

                    opened.completeExceptionally(t)

                    ready.completeExceptionally(t)

                    completeFinishedExceptionally(t)

                }



                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {

                    Log.i(TAG, "WebSocket closed code=$code reason=$reason")

                    completeFinishedIfNeeded(latestText)

                }

            })



            opened.await()

            ready.await()

            flushPendingPcm()

        }



        fun enqueuePcm(pcm: ByteArray) {

            synchronized(pcmLock) {

                if (!isReady) {

                    if (pendingBeforeReady.size() + pcm.size > MAX_PENDING_PCM_BYTES) {

                        val overflow = pendingBeforeReady.size() + pcm.size - MAX_PENDING_PCM_BYTES

                        if (overflow >= pendingBeforeReady.size()) {

                            pendingBeforeReady.reset()

                        } else {

                            val keep = pendingBeforeReady.toByteArray().copyOfRange(overflow, pendingBeforeReady.size())

                            pendingBeforeReady.reset()

                            pendingBeforeReady.write(keep)

                        }

                        Log.w(TAG, "pending PCM truncated before ASR ready")

                    }

                    pendingBeforeReady.write(pcm)

                    return

                }

                frameBuffer.write(pcm)

                emitFullFramesLocked()

            }

        }



        fun flushAndFinish() {

            synchronized(pcmLock) {

                val tail = frameBuffer.toByteArray()

                frameBuffer.reset()

                if (tail.isNotEmpty()) {

                    sendAudioLocked(tail, isLast = false)

                }

                lastAudioSent = true

                sendAudioLocked(ByteArray(0), isLast = true)

            }

        }



        private fun flushPendingPcm() {

            synchronized(pcmLock) {

                if (pendingBeforeReady.size() > 0) {

                    frameBuffer.write(pendingBeforeReady.toByteArray())

                    pendingBeforeReady.reset()

                }

                emitFullFramesLocked()

            }

        }



        private fun emitFullFramesLocked() {

            var data = frameBuffer.toByteArray()

            var offset = 0

            while (data.size - offset >= PCM_FRAME_BYTES) {

                sendAudioLocked(

                    data.copyOfRange(offset, offset + PCM_FRAME_BYTES),

                    isLast = false

                )

                offset += PCM_FRAME_BYTES

            }

            frameBuffer.reset()

            if (offset < data.size) {

                frameBuffer.write(data, offset, data.size - offset)

            }

        }



        private fun sendAudioLocked(pcm: ByteArray, isLast: Boolean) {

            val ws = webSocket ?: run {

                if (isLast) completeFinishedExceptionally(IllegalStateException("WebSocket 未连接"))

                return

            }

            val seq = if (isLast) audioSeq else audioSeq++

            runCatching {

                val frame = VolcAsrBinaryProtocol.buildAudioRequest(pcm, seq, isLast)

                ws.send(ByteString.of(*frame))

            }.onFailure { error ->

                Log.e(TAG, "sendAudio failed", error)

                if (isLast) completeFinishedExceptionally(error)

            }

        }



        suspend fun awaitFinal(): String {

            if (finished.isCompleted) return finished.getCompleted()

            return finished.await()

        }



        private fun handleMessage(data: ByteArray) {

            try {

                val message = VolcAsrBinaryProtocol.parseServerMessage(data)

                if (!ready.isCompleted) {

                    ready.complete(Unit)

                    flushPendingPcm()

                }



                val payload = message.payload

                val code = payload?.get("code")?.asInt

                if (!VolcAsrBinaryProtocol.isSuccessCode(code)) {

                    val msg = payload?.get("message")?.asString ?: "未知错误"

                    throw IllegalStateException("流式 ASR 失败(code=$code): $msg")

                }



                val extracted = VolcAsrBinaryProtocol.extractText(payload)

                if (extracted.isNotBlank()) {

                    latestText = VolcAsrBinaryProtocol.mergeStreamingText(latestText, extracted)

                    onPartial(latestText)

                } else if (payload != null && Log.isLoggable(TAG, Log.DEBUG)) {

                    Log.d(TAG, "ASR payload without text keys=${payload.keySet()}")

                }



                if (message.isLastPackage) {

                    completeFinishedIfNeeded(latestText)

                }

            } catch (error: Exception) {

                Log.e(TAG, "handleMessage failed", error)

                completeFinishedExceptionally(error)

            }

        }



        private fun completeFinishedIfNeeded(text: String) {

            if (!finished.isCompleted && text.isNotBlank()) {

                finished.complete(text)

            }

        }



        private fun completeFinishedExceptionally(error: Throwable) {

            if (!finished.isCompleted) {

                finished.completeExceptionally(error)

            }

        }



        fun closeQuietly() {

            if (closed.compareAndSet(false, true)) {

                completeFinishedIfNeeded(latestText)

                if (!finished.isCompleted) {

                    finished.complete("")

                }

                webSocket?.close(1000, "done")

                webSocket = null

            }

        }

    }

}



internal data class VolcAsrConfig(

    val appId: String,

    val token: String,

    val streamingCluster: String,

    val oneShotCluster: String,

    val resourceId: String,

    val streamingWsUrl: String

) {

    val useV3Streaming: Boolean get() = resourceId.isNotBlank()



    fun isValid(): Boolean =

        appId.isNotBlank() && token.isNotBlank() &&

            (useV3Streaming || streamingCluster.isNotBlank())



    companion object {

        private const val V2_WS_URL = "wss://openspeech.bytedance.com/api/v2/asr"

        private const val V3_WS_URL_DEFAULT = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"



        fun fromBuildConfig(): VolcAsrConfig {

            val resourceId = BuildConfig.VOLC_RESOURCE_ID

            val configuredWsUrl = BuildConfig.VOLC_STREAMING_WS_URL

            val wsUrl = configuredWsUrl.ifBlank {

                if (resourceId.isNotBlank()) V3_WS_URL_DEFAULT else V2_WS_URL

            }

            return VolcAsrConfig(

                appId = BuildConfig.VOLC_APPID,

                token = BuildConfig.VOLC_TOKEN,

                streamingCluster = BuildConfig.VOLC_STREAMING_CLUSTER,

                oneShotCluster = BuildConfig.VOLC_CLUSTER,

                resourceId = resourceId,

                streamingWsUrl = wsUrl

            )

        }

    }

}


