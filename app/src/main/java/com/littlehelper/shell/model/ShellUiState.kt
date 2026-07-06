package com.littlehelper.shell.model

import com.littlehelper.ChatMessage
import com.littlehelper.PanelState
import com.littlehelper.shell.modal.ModalState
import com.littlehelper.shell.modal.ModalSlotState

data class ShellUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val sessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val streamingMessageId: String? = null,
    val activeModule: ModuleId = ModuleId.NONE,
    val moduleLoadState: ModuleLoadState = ModuleLoadState.IDLE,
    val modulePayload: ModulePayload = ModulePayload.Empty,
    val panelState: PanelState = PanelState.COLLAPSED,
    val pendingPanelCommand: PanelCommand? = null,
    val capturePhase: CapturePhase = CapturePhase.IDLE,
    val speakingMessageId: String? = null,
    val bannerError: String? = null,
    val bannerErrorDetail: String? = null,
    val connectFailureKind: com.littlehelper.shell.transport.ConnectFailureKind? = null,
    val connectGatewayCode: String? = null,
    val connectUserAction: String? = null,
    val deviceId: String? = null,
    val pairingRequired: Boolean = false,
    val modalState: ModalState = ModalState(),
    val modalSlots: ModalSlotState = ModalSlotState(),
    val modalParseWarning: String? = null,
    val streamingAssistantRaw: String? = null,
    val connectionBannerVisible: Boolean = true,
    val awaitingAssistantReply: Boolean = false,
    val silentReconnectActive: Boolean = false
) {
    companion object {
        fun initial(welcomeMessage: ChatMessage? = null): ShellUiState {
            val messages = if (welcomeMessage != null) listOf(welcomeMessage) else emptyList()
            return ShellUiState(
                messages = messages,
                connectionState = ConnectionState.DISCONNECTED
            )
        }
    }
}
