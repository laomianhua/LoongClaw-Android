package com.littlehelper.shell.model

import com.littlehelper.ChatRole

/** Gateway → Shell 下行事件流（WebSocket JSON Event 管道映射为领域事件）。 */
sealed class ClawSessionEvent {

    data class ConnectionChanged(val state: ConnectionState) : ClawSessionEvent()

    data class SessionOpened(val sessionId: String) : ClawSessionEvent()

    data class ChatDelta(
        val sessionId: String,
        val turnId: String,
        val role: ChatRole,
        val text: String,
        /** Gateway `chat.delta` 的 `deltaText` 为增量片段，需追加到同 turn 气泡。 */
        val appendDelta: Boolean = false
    ) : ClawSessionEvent()

    data class ChatFinal(
        val sessionId: String,
        val turnId: String,
        val role: ChatRole,
        val text: String
    ) : ClawSessionEvent()

    /** 意图预加载：抢在 chat 流式结束前切换 ModuleHost 槽位。 */
    data class IntentPreload(val preload: ClawIntentPreload) : ClawSessionEvent()

    /** 完整意图与多模态载荷就绪。 */
    data class IntentFinal(val response: ClawSessionResponse) : ClawSessionEvent()

    data class TurnUploading(val turnId: String) : ClawSessionEvent()

    /** Gateway 推送 thinking/reasoning 块，尚无可见正文。 */
    data class AssistantThinking(val turnId: String) : ClawSessionEvent()

    data class SessionError(
        val message: String,
        val turnId: String? = null,
        val pairingRequired: Boolean = false
    ) : ClawSessionEvent()
}
