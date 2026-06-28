package com.littlehelper.shell.transport

import com.littlehelper.ChatRole
import com.littlehelper.shell.demo.HoldingsDemoReplay
import com.littlehelper.shell.demo.HoldingsModalDemo
import com.littlehelper.shell.model.ClawSessionEvent
import com.littlehelper.shell.model.ClawSessionResponse
import com.littlehelper.shell.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Phase 0 模拟 Gateway：演示 PCM 分片上传时序 + 意图预加载 + 流式 chat.delta。
 * Phase 1：持仓白板（===MODAL=== 协议）与地图 Demo 分轨回放。
 */
class MockOpenClawSessionClient(
    private val scope: CoroutineScope
) : OpenClawSessionClient {

    private val _events = MutableSharedFlow<ClawSessionEvent>(extraBufferCapacity = 32)
    override val events: Flow<ClawSessionEvent> = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val sessionId = "mock-session-${UUID.randomUUID()}"

    override suspend fun connect() {
        _connectionState.value = ConnectionState.CONNECTING
        delay(120)
        _connectionState.value = ConnectionState.ONLINE
        _events.emit(ClawSessionEvent.SessionOpened(sessionId))
    }

    override suspend fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun sendTextMessage(text: String) {
        val turnId = UUID.randomUUID().toString()
        _events.emit(
            ClawSessionEvent.ChatFinal(
                sessionId = sessionId,
                turnId = turnId,
                role = ChatRole.USER,
                text = text
            )
        )
        when {
            isHoldingsRequest(text) -> replayHoldingsModalTurn(turnId)
            isHoldingsUpdateRequest(text) -> replayHoldingsUpdateTurn(turnId)
            else -> replayMapDemoTurn(turnId)
        }
    }

    override suspend fun syncAssistantInstructions(instructions: String) = Unit

    override suspend fun startTurn(turnId: String) {
        _events.emit(ClawSessionEvent.TurnUploading(turnId))
    }

    override suspend fun sendAudioChunk(turnId: String, seq: Int, chunk: ByteArray) = Unit

    override suspend fun endTurn(turnId: String, durationMs: Long) {
        scope.launch {
            replayMapDemoTurn(turnId)
        }
    }

    private fun isHoldingsRequest(text: String): Boolean =
        text.contains("持仓") || text == HoldingsModalDemo.USER_MESSAGE

    private fun isHoldingsUpdateRequest(text: String): Boolean =
        text.contains("均线") || text == HoldingsModalDemo.UPDATE_USER_MESSAGE

    private suspend fun replayHoldingsModalTurn(turnId: String) {
        delay(80)
        streamAssistantMessage(turnId, HoldingsModalDemo.AGENT_RESPONSE)
    }

    private suspend fun replayHoldingsUpdateTurn(turnId: String) {
        delay(80)
        streamAssistantMessage(turnId, HoldingsModalDemo.UPDATE_AGENT_RESPONSE)
    }

    private suspend fun streamAssistantMessage(turnId: String, fullResponse: String) {
        val checkpoints = buildStreamingCheckpoints(fullResponse)
        for (chunk in checkpoints) {
            _events.emit(
                ClawSessionEvent.ChatDelta(
                    sessionId = sessionId,
                    turnId = turnId,
                    role = ChatRole.ASSISTANT,
                    text = chunk,
                    appendDelta = false
                )
            )
            delay(90)
        }
        _events.emit(
            ClawSessionEvent.ChatFinal(
                sessionId = sessionId,
                turnId = turnId,
                role = ChatRole.ASSISTANT,
                text = fullResponse
            )
        )
    }

    private fun buildStreamingCheckpoints(fullText: String): List<String> {
        val markers = listOf(
            fullText.indexOf("===MODAL==="),
            fullText.indexOf("===END==="),
            fullText.length
        ).filter { it > 0 }.distinct().sorted()
        return if (markers.isEmpty()) {
            listOf(
                fullText.take(40),
                fullText.take(fullText.length / 2),
                fullText
            ).distinct()
        } else {
            markers.map { fullText.take(it) } + fullText
        }.distinct()
    }

    private suspend fun replayMapDemoTurn(turnId: String) {
        val assistantText = "您好！我是 OpenClaw 助手，有什么可以帮您的吗？"
        delay(80)
        streamAssistantMessage(turnId, assistantText)
        _events.emit(
            ClawSessionEvent.IntentFinal(
                ClawSessionResponse(
                    sessionId = sessionId,
                    turnId = turnId,
                    textContent = assistantText,
                    intent = null
                )
            )
        )
    }
}
