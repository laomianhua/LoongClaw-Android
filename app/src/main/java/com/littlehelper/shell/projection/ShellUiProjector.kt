package com.littlehelper.shell.projection

import com.littlehelper.AppPhase
import com.littlehelper.ChatMessage
import com.littlehelper.ChatRole
import com.littlehelper.PanelState
import com.littlehelper.shell.model.CapturePhase
import com.littlehelper.shell.model.ConnectionState
import com.littlehelper.shell.model.ModuleLoadState
import com.littlehelper.shell.model.ModulePayload
import com.littlehelper.shell.model.ShellMode
import com.littlehelper.shell.model.ShellNoteItem
import com.littlehelper.shell.modal.ModalHistoryState
import com.littlehelper.shell.modal.ModalState
import com.littlehelper.viewmodel.MainUiState

/** 将 ShellUiState 投影为 UI 可消费的统一读模型（OPENCLAW 轨）。 */
object ShellUiProjector {

    fun messages(state: MainUiState): List<ChatMessage> {
        val raw = if (state.shellMode == ShellMode.OPENCLAW) state.shell.messages else state.messages
        return raw.filterNot { message ->
            message.isPartial &&
                message.role == ChatRole.USER &&
                (ChatMessage.isVoiceDraftPlaceholder(message.text) ||
                    ChatMessage.isTranscribingPlaceholder(message.text))
        }
    }

    fun panelState(state: MainUiState): PanelState =
        if (state.shellMode == ShellMode.OPENCLAW) state.shell.panelState else state.panelState

    fun moduleLoadState(state: MainUiState): ModuleLoadState =
        if (state.shellMode == ShellMode.OPENCLAW) state.shell.moduleLoadState else ModuleLoadState.IDLE

    fun modulePayload(state: MainUiState): ModulePayload =
        if (state.shellMode == ShellMode.OPENCLAW) state.shell.modulePayload else ModulePayload.Empty

    fun shellNoteItems(state: MainUiState): List<ShellNoteItem> {
        val payload = modulePayload(state)
        return if (payload is ModulePayload.Note) payload.items else emptyList()
    }

    fun speakingMessageId(state: MainUiState): String? =
        if (state.shellMode == ShellMode.OPENCLAW) state.shell.speakingMessageId else state.speakingMessageId

    fun phase(state: MainUiState): AppPhase {
        if (state.shellMode == ShellMode.LOCAL) return state.phase
        if (state.shell.speakingMessageId != null) return AppPhase.ANSWERING
        return when (state.shell.capturePhase) {
            CapturePhase.IDLE -> AppPhase.IDLE
            CapturePhase.RECORDING -> AppPhase.RECORDING
            CapturePhase.UPLOADING -> AppPhase.SENDING
            CapturePhase.PROCESSING -> AppPhase.PROCESSING
        }
    }

    fun intercomHintOverride(state: MainUiState): String? {
        if (state.shellMode != ShellMode.OPENCLAW) return null
        val shell = state.shell
        if (shell.pairingRequired) return "批准后点上方横幅重试"
        if (shell.connectionState == ConnectionState.CONNECTING) return "正在连接 Gateway…"
        if (shell.connectionState == ConnectionState.DEGRADED) {
            return shell.bannerError ?: "连接异常，点上方重试"
        }
        return null
    }

    fun modalState(state: MainUiState): ModalState =
        if (state.shellMode == ShellMode.OPENCLAW) state.shell.modalState else state.localModalState

    fun modalHistory(state: MainUiState): ModalHistoryState =
        if (state.shellMode == ShellMode.OPENCLAW) state.shell.modalHistory else ModalHistoryState()

    fun isModalCanvasOpen(state: MainUiState): Boolean = modalState(state).isOpen

    fun showAssistantThinking(state: MainUiState): Boolean {
        if (state.shellMode != ShellMode.OPENCLAW) return false
        if (!state.shell.awaitingAssistantReply) return false
        return state.shell.messages.none { message ->
            message.role == com.littlehelper.ChatRole.ASSISTANT && message.isPartial
        }
    }

    fun gatewayConnectionState(state: MainUiState): ConnectionState {
        if (state.shellMode != ShellMode.OPENCLAW) return ConnectionState.ONLINE
        val shell = state.shell
        if (shell.silentReconnectActive && shell.connectionState != ConnectionState.ONLINE) {
            return ConnectionState.CONNECTING
        }
        val sessionLive = shell.awaitingAssistantReply ||
            shell.messages.any { it.role == com.littlehelper.ChatRole.ASSISTANT && it.isPartial }
        if (sessionLive && shell.connectionState != ConnectionState.DISCONNECTED) {
            return ConnectionState.ONLINE
        }
        return shell.connectionState
    }
}
