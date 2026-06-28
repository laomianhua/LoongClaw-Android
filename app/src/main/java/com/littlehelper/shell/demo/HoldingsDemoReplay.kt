package com.littlehelper.shell.demo

import com.littlehelper.ChatRole
import com.littlehelper.shell.model.ClawSessionEvent
import com.littlehelper.shell.session.SessionReducer
import com.littlehelper.shell.model.ShellUiState
import java.util.UUID

/**
 * 方案 §10 附录回放 + §4 update 示例，供 Mock / 测试按钮复用。
 */
object HoldingsDemoReplay {

    fun newTurnId(): String = "demo-${UUID.randomUUID()}"

    fun events(sessionId: String, turnId: String): List<ClawSessionEvent> = listOf(
        ClawSessionEvent.ChatFinal(
            sessionId = sessionId,
            turnId = turnId,
            role = ChatRole.USER,
            text = HoldingsModalDemo.USER_MESSAGE
        ),
        ClawSessionEvent.ChatFinal(
            sessionId = sessionId,
            turnId = turnId,
            role = ChatRole.ASSISTANT,
            text = HoldingsModalDemo.AGENT_RESPONSE
        )
    )

    fun updateEvents(sessionId: String, turnId: String): List<ClawSessionEvent> = listOf(
        ClawSessionEvent.ChatFinal(
            sessionId = sessionId,
            turnId = turnId,
            role = ChatRole.USER,
            text = HoldingsModalDemo.UPDATE_USER_MESSAGE
        ),
        ClawSessionEvent.ChatFinal(
            sessionId = sessionId,
            turnId = turnId,
            role = ChatRole.ASSISTANT,
            text = HoldingsModalDemo.UPDATE_AGENT_RESPONSE
        )
    )

    fun applyOpenDemo(state: ShellUiState): ShellUiState {
        val sessionId = state.sessionId ?: "demo-session"
        val turnId = newTurnId()
        return events(sessionId, turnId).fold(state) { acc, event ->
            SessionReducer.reduce(acc, event)
        }
    }
}
