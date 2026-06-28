package com.littlehelper.shell.transport

import com.littlehelper.shell.model.ClawSessionEvent
import com.littlehelper.shell.model.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * OpenClaw Gateway 长连接传输契约（Phase 2 实现 WebSocket；Phase 0 由 Mock 实现）。
 */
interface OpenClawSessionClient {
    val events: Flow<ClawSessionEvent>
    val connectionState: StateFlow<ConnectionState>

    suspend fun connect()
    suspend fun disconnect()

    /** Stage 1：向已订阅 session 发送文本（语音经本地 ASR 转写后走此路径）。 */
    suspend fun sendTextMessage(text: String)

    /** 将语气/人设指令同步到 Gateway（sessions.patch，失败时 fallback 发设置消息）。 */
    suspend fun syncAssistantInstructions(instructions: String)

    suspend fun startTurn(turnId: String)
    suspend fun sendAudioChunk(turnId: String, seq: Int, chunk: ByteArray)
    suspend fun endTurn(turnId: String, durationMs: Long)
}
