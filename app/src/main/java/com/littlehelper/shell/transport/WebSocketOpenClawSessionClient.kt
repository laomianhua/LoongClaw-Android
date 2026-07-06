package com.littlehelper.shell.transport

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.littlehelper.shell.model.ClawSessionEvent
import com.littlehelper.shell.model.ConnectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * OpenClaw Gateway WebSocket 客户端（协议 v4）。
 *
 * 消息：connect → hello-ok → sessions.messages.subscribe → chat.send
 * 语音：talk.session.create → startTurn → appendAudio → endTurn → close。
 */
class WebSocketOpenClawSessionClient(
    initialConfig: GatewayConfig,
    private val identityStore: OpenClawDeviceIdentityStore,
    private val scope: CoroutineScope,
    private val gson: Gson = Gson(),
) : OpenClawSessionClient {

    @Volatile
    private var config: GatewayConfig = initialConfig

    fun updateConfig(newConfig: GatewayConfig) {
        config = newConfig
        messagesSubscribedKey = null
    }

    fun currentConfig(): GatewayConfig = config

    private var helloPolicy: OpenClawConnectHandshake.GatewayHelloPolicy =
        OpenClawConnectHandshake.GatewayHelloPolicy()

    private fun buildOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(helloPolicy.tickIntervalMs, TimeUnit.MILLISECONDS)
        .build()

    private val _events = MutableSharedFlow<ClawSessionEvent>(extraBufferCapacity = 64)
    override val events: Flow<ClawSessionEvent> = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val sendMutex = Mutex()
    private val requestIdSeq = AtomicInteger(0)
    private val pendingTalkCreates = ConcurrentHashMap<String, CompletableDeferred<String>>()

    private val connectGeneration = AtomicInteger(0)
    private val connectUnavailableRetries = AtomicInteger(0)

    private var webSocket: WebSocket? = null
    private var connectDeferred: CompletableDeferred<String>? = null
    private var sessionConnId: String? = null
    private var messagesSubscribedKey: String? = null
    private var pendingMessagesSubscribe: CompletableDeferred<Boolean>? = null
    private var activeTurnId: String? = null
    /** talk.session.create 返回的 id，与 agent sessionKey 不同。 */
    private var activeTalkSessionId: String? = null

    private fun emitConnectionState(state: ConnectionState) {
        if (_connectionState.value == state) return
        _connectionState.value = state
        scope.launch {
            _events.emit(ClawSessionEvent.ConnectionChanged(state))
        }
    }

    override suspend fun connect() {
        if (_connectionState.value == ConnectionState.ONLINE) return
        if (!config.isConnectable) return

        disconnect()
        emitConnectionState(ConnectionState.CONNECTING)

        val deferred = CompletableDeferred<String>()
        connectDeferred = deferred
        connectUnavailableRetries.set(0)
        val connectionGeneration = connectGeneration.incrementAndGet()

        val request = Request.Builder()
            .url(config.wsUrl())
            .build()

        val client = buildOkHttpClient()
        webSocket = client.newWebSocket(request, GatewayListener(connectionGeneration))

        try {
            withTimeout(CONNECT_TIMEOUT_MS) {
                val connId = deferred.await()
                sessionConnId = connId
                subscribeSessionMessages()
                emitConnectionState(ConnectionState.ONLINE)
                _events.emit(ClawSessionEvent.SessionOpened(connId))
            }
        } catch (e: Exception) {
            emitConnectionState(ConnectionState.DEGRADED)
            if (e !is ConnectFailedException) {
                val presentation = GatewayConnectErrorMapper.mapThrowable(e)
                _events.emit(presentation.toSessionError())
            }
            disconnect()
            throw e
        } finally {
            connectDeferred = null
        }
    }

    override suspend fun disconnect() {
        activeTalkSessionId?.let { closeTalkSession(it) }
        webSocket?.close(1000, "client disconnect")
        webSocket = null
        sessionConnId = null
        messagesSubscribedKey = null
        pendingMessagesSubscribe?.cancel()
        pendingMessagesSubscribe = null
        activeTurnId = null
        activeTalkSessionId = null
        pendingTalkCreates.clear()
        emitConnectionState(ConnectionState.DISCONNECTED)
    }

    override suspend fun startTurn(turnId: String) {
        ensureOnline()
        activeTurnId = turnId
        activeTalkSessionId = null
        _events.emit(ClawSessionEvent.TurnUploading(turnId))

        val talkSessionId = createTalkSession(turnId)
        activeTalkSessionId = talkSessionId
        sendRequest(
            method = OpenClawGatewayMethods.TALK_SESSION_START_TURN,
            params = JsonObject().apply {
                addProperty("sessionId", talkSessionId)
            }
        )
    }

    override suspend fun sendAudioChunk(turnId: String, seq: Int, chunk: ByteArray) {
        ensureOnline()
        val talkSessionId = activeTalkSessionId
            ?: throw IllegalStateException("talk session 未就绪，请先 startTurn")
        sendRequest(
            method = OpenClawGatewayMethods.TALK_SESSION_APPEND_AUDIO,
            params = JsonObject().apply {
                addProperty("sessionId", talkSessionId)
                addProperty("seq", seq)
                addProperty("data", Base64.encodeToString(chunk, Base64.NO_WRAP))
            }
        )
    }

    override suspend fun endTurn(turnId: String, durationMs: Long) {
        ensureOnline()
        val talkSessionId = activeTalkSessionId ?: return
        sendRequest(
            method = OpenClawGatewayMethods.TALK_SESSION_END_TURN,
            params = JsonObject().apply {
                addProperty("sessionId", talkSessionId)
            }
        )
        closeTalkSession(talkSessionId)
        activeTurnId = null
        activeTalkSessionId = null
    }

    override suspend fun sendTextMessage(text: String) {
        sendRawMessage(text.trim())
    }

    private suspend fun sendRawMessage(text: String) {
        ensureOnline()
        sendRequest(
            method = OpenClawGatewayMethods.CHAT_SEND,
            params = JsonObject().apply {
                addProperty("sessionKey", config.mainSessionKey)
                addProperty("message", text)
                addProperty("idempotencyKey", UUID.randomUUID().toString())
            }
        )
        Log.d(TAG, "chat.send chars=${text.length} key=${config.mainSessionKey}")
    }

    private suspend fun createTalkSession(turnId: String): String {
        val reqId = "talk_create_$turnId"
        val deferred = CompletableDeferred<String>()
        pendingTalkCreates[reqId] = deferred
        sendRequest(
            method = OpenClawGatewayMethods.TALK_SESSION_CREATE,
            requestId = reqId,
            params = JsonObject().apply {
                addProperty("sessionKey", config.mainSessionKey)
            }
        )
        return try {
            withTimeout(TALK_CREATE_TIMEOUT_MS) { deferred.await() }
        } finally {
            pendingTalkCreates.remove(reqId)
        }
    }

    private suspend fun closeTalkSession(talkSessionId: String) {
        sendRequest(
            method = OpenClawGatewayMethods.TALK_SESSION_CLOSE,
            params = JsonObject().apply {
                addProperty("sessionId", talkSessionId)
            }
        )
    }

    private suspend fun subscribeSessionMessages() {
        val key = config.mainSessionKey
        if (messagesSubscribedKey == key) return
        val deferred = CompletableDeferred<Boolean>()
        pendingMessagesSubscribe = deferred
        Log.d(TAG, "sessions.messages.subscribe send key=$key")
        sendRequest(
            method = OpenClawGatewayMethods.SESSIONS_MESSAGES_SUBSCRIBE,
            requestId = SESSION_SUBSCRIBE_REQ_ID,
            params = JsonObject().apply {
                addProperty("key", key)
            }
        )
        try {
            withTimeout(SESSION_SUBSCRIBE_TIMEOUT_MS) {
                deferred.await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "sessions.messages.subscribe timeout/fail key=$key err=${e.message}")
            throw e
        }
        messagesSubscribedKey = key
        Log.d(TAG, "messages subscribed: $key")
    }

    private suspend fun ensureOnline() {
        if (_connectionState.value != ConnectionState.ONLINE) {
            connect()
        }
    }

    private suspend fun sendRequest(
        method: String,
        params: JsonObject,
        requestId: String? = null
    ) {
        val id = requestId ?: "req_${requestIdSeq.incrementAndGet()}"
        val frame = JsonObject().apply {
            addProperty("type", "req")
            addProperty("id", id)
            addProperty("method", method)
            add("params", params)
        }
        sendMutex.withLock {
            val sent = webSocket?.send(gson.toJson(frame)) == true
            if (!sent) {
                _events.emit(ClawSessionEvent.SessionError("WebSocket 发送失败: $method"))
            }
        }
    }

    private fun sendConnect(
        challenge: OpenClawConnectHandshake.Challenge,
        connectionGeneration: Int,
        socket: WebSocket
    ) {
        if (connectionGeneration != connectGeneration.get()) {
            Log.d(TAG, "ignore stale connect.challenge gen=$connectionGeneration")
            return
        }
        if (webSocket !== socket) {
            Log.d(TAG, "ignore connect.challenge for replaced socket")
            return
        }
        val identity = identityStore.loadOrCreateIdentity()
        val storedDeviceToken = identityStore.getDeviceToken(config.connectRole)
        val authToken = OpenClawDeviceAuth.resolveAuthCredential(config, storedDeviceToken)
        val signedDevice = OpenClawDeviceAuth.buildSignedDevice(
            identity = identity,
            config = config,
            challenge = challenge,
            authToken = authToken,
            identityStore = identityStore
        )
        val payloadPreview = OpenClawDeviceAuth.buildPayloadV3ForTest(
            deviceId = identity.deviceId,
            config = config,
            signedAtMs = signedDevice.signedAt,
            authToken = authToken,
            nonce = challenge.nonce
        )
        val frame = OpenClawConnectHandshake.buildConnectRequest(
            requestId = "conn_1",
            config = config,
            challenge = challenge,
            signedDevice = signedDevice,
            authToken = authToken
        )
        socket.send(gson.toJson(frame))
        Log.d(
            TAG,
            "connect sent gen=$connectionGeneration mode=${config.clientMode} " +
                "device=${identity.deviceId.take(12)}… nonce=${challenge.nonce.take(8)}… " +
                "payload=${payloadPreview.take(56)}…"
        )
    }

    private inner class GatewayListener(
        private val connectionGeneration: Int
    ) : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket opened: ${config.wsUrl()}")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch { handleIncoming(text, connectionGeneration, webSocket) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure", t)
            emitConnectionState(ConnectionState.DEGRADED)
            connectDeferred?.completeExceptionally(t)
            pendingTalkCreates.values.forEach { it.completeExceptionally(t) }
            pendingTalkCreates.clear()
            pendingMessagesSubscribe?.completeExceptionally(t)
            pendingMessagesSubscribe = null
            scope.launch {
                val presentation = GatewayConnectErrorMapper.mapThrowable(t)
                _events.emit(presentation.toSessionError())
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code $reason")
            emitConnectionState(ConnectionState.DISCONNECTED)
        }
    }

    private suspend fun handleIncoming(
        text: String,
        connectionGeneration: Int,
        socket: WebSocket
    ) {
        val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return
        when (root.get("type")?.asString) {
            "event" -> handleEvent(root, connectionGeneration, socket)
            "res" -> handleResponse(root)
        }
    }

    private suspend fun handleEvent(
        root: JsonObject,
        connectionGeneration: Int,
        socket: WebSocket
    ) {
        val eventName = root.get("event")?.asString ?: return
        val payload = root.getAsJsonObject("payload")

        if (eventName == "connect.challenge") {
            val challenge = OpenClawConnectHandshake.parseChallenge(payload) ?: return
            sendConnect(challenge, connectionGeneration, socket)
            return
        }

        val sessionId = sessionConnId ?: config.mainSessionKey
        GatewayEventMapper.mapGatewayMessage(gson.toJson(root), sessionId)?.let { event ->
            if (eventName == "session.message" || eventName == "chat.delta") {
                val preview = when (event) {
                    is ClawSessionEvent.ChatFinal -> event.text
                    is ClawSessionEvent.ChatDelta -> event.text
                    else -> ""
                }
                val hasModal = preview.isNotBlank() &&
                    com.littlehelper.shell.parser.MessageBlockParser.hasModalDirective(preview)
                Log.d(
                    TAG,
                    "$eventName mapped chars=${preview.length} modal=$hasModal preview=${preview.take(48)}"
                )
            }
            _events.emit(event)
        }
    }

    private fun handleResponse(root: JsonObject) {
        val id = root.get("id")?.asString
        val ok = root.get("ok")?.asBoolean == true
        val payload = root.getAsJsonObject("payload")

        if (id == "conn_1") {
            if (ok && OpenClawConnectHandshake.isHelloOk(payload)) {
                helloPolicy = OpenClawConnectHandshake.extractPolicy(payload)
                Log.d(
                    TAG,
                    "hello-ok policy tickIntervalMs=${helloPolicy.tickIntervalMs} " +
                        "maxPayload=${helloPolicy.maxPayload} " +
                        "maxBufferedBytes=${helloPolicy.maxBufferedBytes}"
                )
                OpenClawConnectHandshake.extractDeviceToken(payload, config.connectRole)?.let { token ->
                    identityStore.saveDeviceToken(config.connectRole, token)
                    Log.d(TAG, "deviceToken saved for role=${config.connectRole}")
                }
                val connId = OpenClawConnectHandshake.extractConnId(payload) ?: config.mainSessionKey
                connectDeferred?.complete(connId)
            } else {
                val errorObj = root.getAsJsonObject("error")
                val retryHint = OpenClawConnectHandshake.parseConnectRetry(errorObj)
                if (retryHint != null &&
                    connectUnavailableRetries.incrementAndGet() <= MAX_CONNECT_UNAVAILABLE_RETRIES
                ) {
                    Log.d(
                        TAG,
                        "connect UNAVAILABLE reason=${retryHint.reason} " +
                            "retryAfterMs=${retryHint.retryAfterMs} " +
                            "attempt=${connectUnavailableRetries.get()}/$MAX_CONNECT_UNAVAILABLE_RETRIES " +
                            "(await next connect.challenge)"
                    )
                    scope.launch {
                        delay(retryHint.retryAfterMs)
                    }
                    return
                }
                val deviceId = identityStore.loadOrCreateIdentity().deviceId
                val hasDeviceToken = !identityStore.getDeviceToken(config.connectRole).isNullOrBlank()
                val presentation = GatewayConnectErrorMapper.mapGatewayError(
                    error = errorObj,
                    deviceId = deviceId,
                    hasStoredDeviceToken = hasDeviceToken,
                    credentialUsed = if (hasDeviceToken) {
                        CredentialKind.DEVICE
                    } else {
                        CredentialKind.SHARED
                    },
                )
                Log.w(
                    TAG,
                    "connect failed kind=${presentation.kind} code=${presentation.gatewayCode} " +
                        "credential=${if (hasDeviceToken) "device" else "shared"} " +
                        "device=${deviceId.take(12)}…"
                )
                scope.launch {
                    _events.emit(presentation.toSessionError())
                }
                connectDeferred?.completeExceptionally(
                    ConnectFailedException(presentation, deviceId)
                )
            }
            return
        }

        if (id == SESSION_SUBSCRIBE_REQ_ID) {
            completeMessagesSubscribe(ok, root.getAsJsonObject("error"))
            return
        }

        id?.let { reqId ->
            pendingTalkCreates[reqId]?.let { deferred ->
                if (ok) {
                    val talkSessionId = payload?.extractTalkSessionId()
                    if (talkSessionId != null) {
                        deferred.complete(talkSessionId)
                    } else {
                        deferred.completeExceptionally(
                            IllegalStateException("talk.session.create 响应缺少 sessionId")
                        )
                    }
                } else {
                    deferred.completeExceptionally(
                        IllegalStateException(root.get("error")?.toString() ?: "talk.session.create 失败")
                    )
                }
                return
            }
        }

        if (!ok) {
            val errorObj = root.getAsJsonObject("error")
            val presentation = GatewayConnectErrorMapper.mapGatewayError(errorObj)
            scope.launch {
                _events.emit(presentation.toSessionError())
            }
            return
        }

        if (payload?.get("subscribed")?.asBoolean == true) {
            Log.d(TAG, "subscribe ok key=${payload.get("key")?.asString}")
        }
    }

    private fun completeMessagesSubscribe(ok: Boolean, error: JsonObject?) {
        val deferred = pendingMessagesSubscribe
        if (deferred == null) {
            Log.w(
                TAG,
                "sessions.messages.subscribe stray/late res ok=$ok err=${error?.get("message")?.asString} " +
                    "(no pending waiter — likely timed out earlier)"
            )
            return
        }
        pendingMessagesSubscribe = null
        if (ok) {
            Log.d(TAG, "sessions.messages.subscribe res ok=true")
            deferred.complete(true)
            return
        }
        val message = error?.get("message")?.asString.orEmpty()
        Log.w(TAG, "sessions.messages.subscribe res ok=false err=$message")
        val presentation = GatewayConnectErrorMapper.mapGatewayError(error)
        deferred.completeExceptionally(
            ConnectFailedException(presentation)
        )
    }

    private fun JsonObject.extractTalkSessionId(): String? =
        get("sessionId")?.takeIf { !it.isJsonNull }?.asString

    companion object {
        private const val TAG = "OpenClawWS"
        private const val CONNECT_TIMEOUT_MS = 25_000L
        private const val SESSION_SUBSCRIBE_TIMEOUT_MS = 8_000L
        private const val TALK_CREATE_TIMEOUT_MS = 10_000L
        private const val MAX_CONNECT_UNAVAILABLE_RETRIES = 8
        private const val SESSION_SUBSCRIBE_REQ_ID = "sess_subscribe"
    }
}
