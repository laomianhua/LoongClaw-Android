package com.littlehelper.shell.transport

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.littlehelper.settings.GatewayAuthMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 设置页「测试握手」：独立短连接，验证 connect → hello-ok → chat.send（智能体可用）。
 * 不影响主连接客户端。
 */
object GatewayHandshakeProbe {

    private const val TAG = "OpenClawHandshakeProbe"
    private const val PROBE_TIMEOUT_MS = 20_000L
    private const val CONNECT_REQ_ID = "conn_probe"
    private const val CHAT_PROBE_REQ_ID = "chat_probe"
    private const val PROBE_MAX_ATTEMPTS = 2
    private const val PROBE_RETRY_DELAY_MS = 2_000L

    suspend fun testWebSocketHandshake(
        config: GatewayConfig,
        identityStore: OpenClawDeviceIdentityStore,
        useSharedCredentialOnly: Boolean = false,
    ): Result<String> = withContext(Dispatchers.IO) {
        val host = config.host.trim()
        if (host.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("请先填写服务器地址"))
        }
        if (config.port !in 1..65535) {
            return@withContext Result.failure(IllegalArgumentException("端口无效"))
        }
        validateCredentials(
            config,
            if (useSharedCredentialOnly) null else identityStore.getDeviceToken(config.connectRole),
        )?.let { message ->
            return@withContext Result.failure(IllegalArgumentException(message))
        }

        var lastResult: Result<String>? = null
        repeat(PROBE_MAX_ATTEMPTS) { attempt ->
            val result = runSingleProbe(config, identityStore, useSharedCredentialOnly)
            if (result.isSuccess) return@withContext result
            lastResult = result
            val retryable = result.exceptionOrNull().let { error ->
                error is ConnectFailedException && error.presentation.pairingRequired
            }
            if (retryable && attempt < PROBE_MAX_ATTEMPTS - 1) {
                delay(PROBE_RETRY_DELAY_MS)
            } else {
                return@withContext result
            }
        }
        lastResult ?: Result.failure(IllegalStateException("握手探测未执行"))
    }

    private suspend fun runSingleProbe(
        config: GatewayConfig,
        identityStore: OpenClawDeviceIdentityStore,
        useSharedCredentialOnly: Boolean,
    ): Result<String> {
        val deferred = CompletableDeferred<Result<String>>()
        val gson = Gson()
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder().url(config.wsUrl()).build()
        val webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (deferred.isCompleted) return
                    deferred.complete(Result.failure(t))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (deferred.isCompleted) return
                    val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                        ?: return
                    when (root.get("type")?.asString) {
                        "event" -> handleEvent(root, config, identityStore, gson, webSocket, useSharedCredentialOnly)
                        "res" -> handleResponse(root, config, identityStore, gson, webSocket, deferred, useSharedCredentialOnly)
                    }
                }

                private fun handleEvent(
                    root: JsonObject,
                    config: GatewayConfig,
                    identityStore: OpenClawDeviceIdentityStore,
                    gson: Gson,
                    webSocket: WebSocket,
                    useSharedCredentialOnly: Boolean,
                ) {
                    if (root.get("event")?.asString != "connect.challenge") return
                    val challenge = OpenClawConnectHandshake.parseChallenge(
                        root.getAsJsonObject("payload")
                    ) ?: return
                    val identity = identityStore.loadOrCreateIdentity()
                    val storedDeviceToken = identityStore.getDeviceToken(config.connectRole)
                    val authCredential = if (useSharedCredentialOnly) {
                        OpenClawDeviceAuth.resolveSharedCredential(config)
                    } else {
                        OpenClawDeviceAuth.resolveAuthCredential(config, storedDeviceToken)
                    }
                    val signedDevice = OpenClawDeviceAuth.buildSignedDevice(
                        identity = identity,
                        config = config,
                        challenge = challenge,
                        authToken = authCredential,
                        identityStore = identityStore,
                    )
                    val frame = OpenClawConnectHandshake.buildConnectRequest(
                        requestId = CONNECT_REQ_ID,
                        config = config,
                        challenge = challenge,
                        signedDevice = signedDevice,
                        authToken = authCredential,
                    )
                    webSocket.send(gson.toJson(frame))
                    Log.d(TAG, "connect probe sent host=${config.host}:${config.port}")
                }

                private fun handleResponse(
                    root: JsonObject,
                    config: GatewayConfig,
                    identityStore: OpenClawDeviceIdentityStore,
                    gson: Gson,
                    webSocket: WebSocket,
                    deferred: CompletableDeferred<Result<String>>,
                    useSharedCredentialOnly: Boolean,
                ) {
                    when (root.get("id")?.asString) {
                        CONNECT_REQ_ID -> handleConnectResponse(
                            root, config, identityStore, gson, webSocket, deferred, useSharedCredentialOnly
                        )
                        CHAT_PROBE_REQ_ID -> handleChatProbeResponse(
                            root, identityStore, deferred, useSharedCredentialOnly
                        )
                    }
                }

                private fun handleConnectResponse(
                    root: JsonObject,
                    config: GatewayConfig,
                    identityStore: OpenClawDeviceIdentityStore,
                    gson: Gson,
                    webSocket: WebSocket,
                    deferred: CompletableDeferred<Result<String>>,
                    useSharedCredentialOnly: Boolean,
                ) {
                    val ok = root.get("ok")?.asBoolean == true
                    val payload = root.getAsJsonObject("payload")
                    if (ok && OpenClawConnectHandshake.isHelloOk(payload)) {
                        OpenClawConnectHandshake.extractDeviceToken(payload, config.connectRole)
                            ?.let { identityStore.saveDeviceToken(config.connectRole, it) }
                        sendChatProbe(config, gson, webSocket)
                        return
                    }
                    completeWithGatewayError(root, identityStore, deferred, useSharedCredentialOnly)
                }

                private fun handleChatProbeResponse(
                    root: JsonObject,
                    identityStore: OpenClawDeviceIdentityStore,
                    deferred: CompletableDeferred<Result<String>>,
                    useSharedCredentialOnly: Boolean,
                ) {
                    if (root.get("ok")?.asBoolean == true) {
                        deferred.complete(
                            Result.success("WebSocket 握手成功，智能体可用")
                        )
                        return
                    }
                    completeWithGatewayError(root, identityStore, deferred, useSharedCredentialOnly)
                }

                private fun sendChatProbe(
                    config: GatewayConfig,
                    gson: Gson,
                    webSocket: WebSocket,
                ) {
                    val frame = JsonObject().apply {
                        addProperty("type", "req")
                        addProperty("id", CHAT_PROBE_REQ_ID)
                        addProperty("method", OpenClawGatewayMethods.CHAT_SEND)
                        add("params", JsonObject().apply {
                            addProperty("sessionKey", config.mainSessionKey)
                            addProperty("message", ".")
                            addProperty("idempotencyKey", UUID.randomUUID().toString())
                        })
                    }
                    webSocket.send(gson.toJson(frame))
                    Log.d(TAG, "chat.send probe key=${config.mainSessionKey}")
                }

                private fun completeWithGatewayError(
                    root: JsonObject,
                    identityStore: OpenClawDeviceIdentityStore,
                    deferred: CompletableDeferred<Result<String>>,
                    useSharedCredentialOnly: Boolean,
                ) {
                    val error = root.getAsJsonObject("error")
                    val deviceId = identityStore.loadOrCreateIdentity().deviceId
                    val hasDeviceToken = !useSharedCredentialOnly &&
                        !identityStore.getDeviceToken(config.connectRole).isNullOrBlank()
                    val presentation = GatewayConnectErrorMapper.mapGatewayError(
                        error = error,
                        deviceId = deviceId,
                        hasStoredDeviceToken = hasDeviceToken,
                        credentialUsed = CredentialKind.SHARED,
                    )
                    deferred.complete(
                        Result.failure(ConnectFailedException(presentation, deviceId))
                    )
                }
            }
        )

        return try {
            withTimeout(PROBE_TIMEOUT_MS) {
                deferred.await().fold(
                    onSuccess = { Result.success(it) },
                    onFailure = { mapProbeFailure(it) },
                )
            }
        } catch (e: Exception) {
            if (!deferred.isCompleted) {
                deferred.complete(Result.failure(e))
            }
            mapProbeFailure(e)
        } finally {
            runCatching { webSocket.close(1000, "probe done") }
            client.dispatcher.executorService.shutdown()
        }
    }

    internal fun validateCredentials(
        config: GatewayConfig,
        storedDeviceToken: String?,
    ): String? {
        if (!storedDeviceToken.isNullOrBlank()) return null
        return when (config.authMode) {
            GatewayAuthMode.TOKEN ->
                if (config.gatewayToken.isBlank()) "请填写 Token" else null
            GatewayAuthMode.PASSWORD ->
                if (config.password.isBlank()) "请填写密码" else null
            GatewayAuthMode.NONE -> null
        }
    }

    private fun mapProbeFailure(error: Throwable): Result<String> {
        if (error is ConnectFailedException || error is IllegalArgumentException) {
            return Result.failure(error)
        }
        val presentation = GatewayConnectErrorMapper.mapThrowable(error)
        return Result.failure(ConnectFailedException(presentation))
    }
}
