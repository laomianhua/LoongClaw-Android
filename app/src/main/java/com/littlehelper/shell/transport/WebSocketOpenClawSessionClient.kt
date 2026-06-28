package com.littlehelper.shell.transport

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.littlehelper.shell.model.ClawSessionEvent
import com.littlehelper.shell.model.ConnectionState
import com.littlehelper.settings.AssistantToneStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * OpenClaw Gateway WebSocket 客户端（协议 v4）。
 *
 * Stage 1：connect → sessions.messages.subscribe → sessions.send / 收 session.message 等事件。
 * Stage 2：talk.session.create → startTurn → appendAudio → endTurn → close。
 */
class WebSocketOpenClawSessionClient(
    private val config: GatewayConfig,
    private val identityStore: OpenClawDeviceIdentityStore,
    private val toneStore: AssistantToneStore,
    private val scope: CoroutineScope,
    private val gson: Gson = Gson(),
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
) : OpenClawSessionClient {

    private val _events = MutableSharedFlow<ClawSessionEvent>(extraBufferCapacity = 64)
    override val events: Flow<ClawSessionEvent> = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val sendMutex = Mutex()
    private val requestIdSeq = AtomicInteger(0)
    private val pendingTalkCreates = ConcurrentHashMap<String, CompletableDeferred<String>>()

    private var webSocket: WebSocket? = null
    private var connectDeferred: CompletableDeferred<String>? = null
    private var sessionConnId: String? = null
    private var messagesSubscribedKey: String? = null
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

        disconnect()
        emitConnectionState(ConnectionState.CONNECTING)

        val deferred = CompletableDeferred<String>()
        connectDeferred = deferred

        val request = Request.Builder()
            .url(config.wsUrl())
            .build()

        webSocket = okHttpClient.newWebSocket(request, GatewayListener())

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
            val pairing = e.message?.contains("待配对") == true
            if (!pairing) {
                _events.emit(
                    ClawSessionEvent.SessionError("Gateway 连接失败: ${e.message}")
                )
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

    override suspend fun syncAssistantInstructions(instructions: String) {
        // 语气设置功能暂缓，待统一 Settings 模块时再实现
    }

    private suspend fun sendRawMessage(text: String) {
        ensureOnline()
        sendRequest(
            method = OpenClawGatewayMethods.SESSIONS_SEND,
            params = JsonObject().apply {
                addProperty("key", config.mainSessionKey)
                addProperty("message", text)
            }
        )
        Log.d(TAG, "sessions.send chars=${text.length}")
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
        if (messagesSubscribedKey == config.mainSessionKey) return
        sendRequest(
            method = OpenClawGatewayMethods.SESSIONS_MESSAGES_SUBSCRIBE,
            params = JsonObject().apply {
                addProperty("key", config.mainSessionKey)
            }
        )
        messagesSubscribedKey = config.mainSessionKey
        Log.d(TAG, "messages subscribed: ${config.mainSessionKey}")
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

    private fun sendConnect(challenge: OpenClawConnectHandshake.Challenge) {
        val identity = identityStore.loadOrCreateIdentity()
        val storedDeviceToken = identityStore.getDeviceToken(config.connectRole)
        val authToken = OpenClawDeviceAuth.resolveAuthToken(config, storedDeviceToken)
        val signedDevice = OpenClawDeviceAuth.buildSignedDevice(
            identity = identity,
            config = config,
            challenge = challenge,
            authToken = authToken,
            identityStore = identityStore
        )
        val frame = OpenClawConnectHandshake.buildConnectRequest(
            requestId = "conn_1",
            config = config,
            challenge = challenge,
            signedDevice = signedDevice,
            authToken = authToken
        )
        webSocket?.send(gson.toJson(frame))
        Log.d(TAG, "connect sent mode=${config.clientMode} role=${config.connectRole} device=${identity.deviceId.take(12)}…")
    }

    private inner class GatewayListener : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket opened: ${config.wsUrl()}")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch { handleIncoming(text) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure", t)
            emitConnectionState(ConnectionState.DEGRADED)
            connectDeferred?.completeExceptionally(t)
            pendingTalkCreates.values.forEach { it.completeExceptionally(t) }
            pendingTalkCreates.clear()
            scope.launch {
                _events.emit(ClawSessionEvent.SessionError("Gateway 断开: ${t.message}"))
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code $reason")
            emitConnectionState(ConnectionState.DISCONNECTED)
        }
    }

    private suspend fun handleIncoming(text: String) {
        val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return
        when (root.get("type")?.asString) {
            "event" -> handleEvent(root)
            "res" -> handleResponse(root)
        }
    }

    private suspend fun handleEvent(root: JsonObject) {
        val eventName = root.get("event")?.asString ?: return
        val payload = root.getAsJsonObject("payload")

        if (eventName == "connect.challenge") {
            val challenge = OpenClawConnectHandshake.parseChallenge(payload) ?: return
            sendConnect(challenge)
            return
        }

        val sessionId = sessionConnId ?: config.mainSessionKey
        GatewayEventMapper.mapGatewayMessage(gson.toJson(root), sessionId)?.let { event ->
            if (eventName == "session.message") {
                val preview = when (event) {
                    is ClawSessionEvent.ChatFinal -> event.text
                    is ClawSessionEvent.ChatDelta -> event.text
                    else -> ""
                }
                val hasModal = preview.isNotBlank() &&
                    com.littlehelper.shell.parser.MessageBlockParser.hasModalDirective(preview)
                Log.d(
                    TAG,
                    "session.message mapped chars=${preview.length} modal=$hasModal preview=${preview.take(48)}"
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
                OpenClawConnectHandshake.extractDeviceToken(payload, config.connectRole)?.let { token ->
                    identityStore.saveDeviceToken(config.connectRole, token)
                    Log.d(TAG, "deviceToken saved for role=${config.connectRole}")
                }
                val connId = OpenClawConnectHandshake.extractConnId(payload) ?: config.mainSessionKey
                connectDeferred?.complete(connId)
            } else {
                val errorObj = root.getAsJsonObject("error")
                val deviceId = identityStore.loadOrCreateIdentity().deviceId
                val pairing = OpenClawConnectHandshake.isPairingError(errorObj)
                val message = OpenClawConnectHandshake.formatConnectError(errorObj, deviceId)
                scope.launch {
                    _events.emit(
                        ClawSessionEvent.SessionError(
                            message = message,
                            pairingRequired = pairing
                        )
                    )
                }
                connectDeferred?.completeExceptionally(IllegalStateException(message))
            }
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
            scope.launch {
                _events.emit(
                    ClawSessionEvent.SessionError(
                        message = root.get("error")?.toString() ?: "Gateway 请求失败: $id"
                    )
                )
            }
            return
        }

        if (payload?.get("subscribed")?.asBoolean == true) {
            Log.d(TAG, "subscribe ok key=${payload.get("key")?.asString}")
        }
    }

    private fun JsonObject.extractTalkSessionId(): String? =
        get("sessionId")?.takeIf { !it.isJsonNull }?.asString

    companion object {
        private const val TAG = "OpenClawWS"
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val TALK_CREATE_TIMEOUT_MS = 10_000L
    }
}
