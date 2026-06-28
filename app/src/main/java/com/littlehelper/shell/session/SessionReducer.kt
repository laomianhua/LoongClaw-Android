package com.littlehelper.shell.session

import com.littlehelper.ChatMessage
import com.littlehelper.ChatRole
import com.littlehelper.PanelState
import com.littlehelper.shell.model.CapturePhase
import com.littlehelper.shell.model.ClawSessionEvent
import com.littlehelper.shell.model.ModuleId
import com.littlehelper.shell.model.ModuleLoadState
import com.littlehelper.shell.model.ModulePayload
import com.littlehelper.shell.model.PanelCommand
import com.littlehelper.shell.model.ShellUiState
import com.littlehelper.shell.modal.ModalAction
import com.littlehelper.shell.modal.ModalHistoryReducer
import com.littlehelper.shell.modal.ModalStateReducer
import com.littlehelper.shell.parser.MessageBlockParser
import com.littlehelper.upload.UploadMessageMarker

/**
 * Session 事件纯函数归约器：Gateway Event → ShellUiState。
 * 所有 OpenClaw 驱动的 UI 变更必须经此入口，便于单测与审计。
 */
object SessionReducer {

    private fun ShellUiState.withLiveGateway(): ShellUiState = copy(
        connectionState = com.littlehelper.shell.model.ConnectionState.ONLINE,
        silentReconnectActive = false
    )

    fun reduce(state: ShellUiState, event: ClawSessionEvent): ShellUiState {
        return when (event) {
            is ClawSessionEvent.ConnectionChanged -> state.copy(
                connectionState = event.state,
                bannerError = if (event.state == com.littlehelper.shell.model.ConnectionState.ONLINE) {
                    null
                } else {
                    state.bannerError
                }
            )

            is ClawSessionEvent.SessionOpened -> state.copy(
                sessionId = event.sessionId,
                connectionState = com.littlehelper.shell.model.ConnectionState.ONLINE,
                bannerError = null,
                pairingRequired = false,
                connectionBannerVisible = false
            )

            is ClawSessionEvent.TurnUploading -> state.copy(
                capturePhase = CapturePhase.UPLOADING,
                bannerError = null
            )

            is ClawSessionEvent.ChatDelta -> reduceChatDelta(state, event)

            is ClawSessionEvent.ChatFinal -> reduceChatFinal(state, event)

            is ClawSessionEvent.IntentPreload -> reduceIntentPreload(state, event)

            is ClawSessionEvent.IntentFinal -> reduceIntentFinal(state, event)

            is ClawSessionEvent.AssistantThinking -> state.withLiveGateway().copy(awaitingAssistantReply = true)

            is ClawSessionEvent.SessionError -> state.copy(
                capturePhase = CapturePhase.IDLE,
                bannerError = event.message,
                pairingRequired = event.pairingRequired,
                connectionState = if (event.pairingRequired) {
                    state.connectionState
                } else {
                    com.littlehelper.shell.model.ConnectionState.DEGRADED
                },
                streamingMessageId = null,
                streamingAssistantRaw = null,
                awaitingAssistantReply = false
            )
        }
    }

    private fun reduceChatDelta(
        state: ShellUiState,
        event: ClawSessionEvent.ChatDelta
    ): ShellUiState {
        if (event.text.isBlank() && !event.appendDelta) return state
        val streamingId = streamingIdFor(event.turnId, event.role)
        val existing = state.messages.indexOfFirst { it.id == streamingId }
        val mergedText = mergeDeltaText(state, event, existing)
        val displayText = if (event.role == ChatRole.ASSISTANT) {
            MessageBlockParser.chatDisplayText(mergedText)
        } else {
            mergedText
        }
        val updatedMessage = ChatMessage(
            id = streamingId,
            role = event.role,
            text = displayText,
            isPartial = true
        )
        val messages = if (existing >= 0) {
            state.messages.toMutableList().apply { set(existing, updatedMessage) }
        } else {
            state.messages + updatedMessage
        }
        var next = state.copy(
            messages = messages,
            streamingMessageId = streamingId,
            streamingAssistantRaw = if (event.role == ChatRole.ASSISTANT) {
                mergedText
            } else {
                state.streamingAssistantRaw
            },
            awaitingAssistantReply = if (event.role == ChatRole.ASSISTANT) false else state.awaitingAssistantReply
        ).withLiveGateway()
        if (event.role == ChatRole.ASSISTANT) {
            next = tryApplyAssistantModalFromRaw(next, mergedText)
        }
        return next
    }

    /**
     * 合并流式文本。助手消息必须用 [ShellUiState.streamingAssistantRaw] 作基底，
     * 不能用气泡里的 CHAT 预览（否则 ===MODAL=== 段在第二次 delta 丢失）。
     * 同时兼容 Gateway 发送「全量快照」或「增量片段」两种 delta 模式。
     */
    internal fun mergeDeltaText(
        state: ShellUiState,
        event: ClawSessionEvent.ChatDelta,
        existingIndex: Int
    ): String {
        val incoming = event.text
        if (!event.appendDelta || existingIndex < 0) return incoming

        val base = if (event.role == ChatRole.ASSISTANT) {
            state.streamingAssistantRaw
                ?: state.messages.getOrNull(existingIndex)?.text.orEmpty()
        } else {
            state.messages[existingIndex].text
        }
        if (base.isEmpty()) return incoming

        // MODAL 帧替换此前流式累积的正文，避免 Markdown 持仓表残留在气泡里。
        if (MessageBlockParser.hasModalDirective(incoming)) {
            if (incoming.length >= base.length && incoming.startsWith(base)) return incoming
            if (base.length > incoming.length && base.startsWith(incoming)) return base
            return incoming
        }

        // 全量快照：新文本已包含旧内容
        if (incoming.length >= base.length && incoming.startsWith(base)) return incoming
        // 回退/重复帧：保留已有更长内容
        if (base.length > incoming.length && base.startsWith(incoming)) return base

        return base + incoming
    }

    private fun reduceChatFinal(
        state: ShellUiState,
        event: ClawSessionEvent.ChatFinal
    ): ShellUiState {
        val streamingId = streamingIdFor(event.turnId, event.role)
        val streamedText = state.streamingAssistantRaw
            ?: state.messages.find { it.id == streamingId }?.text.orEmpty()
        val rawText = resolveAssistantRawText(event.text, streamedText, event.role)

        if (rawText.isBlank()) {
            return state.copy(
                streamingMessageId = null,
                streamingAssistantRaw = null,
                messages = state.messages.filterNot {
                    it.id == streamingId ||
                        it.id == ChatMessage.PARTIAL_USER_ID
                }
            )
        }
        val finalId = finalizedIdFor(event.turnId, event.role)
        val display = displayTextFor(rawText, event.role)
        val withoutPartial = state.messages.filterNot {
            it.id == streamingId || it.id == ChatMessage.PARTIAL_USER_ID
        }
        if (withoutPartial.any { it.id == finalId }) {
            var next = state.copy(
                messages = withoutPartial,
                streamingMessageId = null,
                streamingAssistantRaw = null,
                awaitingAssistantReply = if (event.role == ChatRole.ASSISTANT) {
                    false
                } else {
                    state.awaitingAssistantReply
                }
            )
            if (event.role == ChatRole.ASSISTANT) {
                next = applyAssistantModal(next, rawText)
            }
            return next.withLiveGateway()
        }
        if (event.role == ChatRole.ASSISTANT) {
            val dupIndex = withoutPartial.indexOfLast {
                it.role == ChatRole.ASSISTANT && !it.isPartial && it.text == display
            }
            if (dupIndex >= 0) {
                // 气泡已存在（文字相同），仍须处理 MODAL（例如重复「好的，已打开」但带新白板）。
                var next = state.copy(
                    messages = withoutPartial,
                    streamingMessageId = null,
                    streamingAssistantRaw = null,
                    awaitingAssistantReply = false
                ).withLiveGateway()
                if (MessageBlockParser.hasModalDirective(rawText)) {
                    next = applyAssistantModal(next, rawText)
                }
                return next
            }
        }
        // 本地已展示过相同用户句（历史乐观插入）时，用 Gateway 回显替换而非追加
        if (event.role == ChatRole.USER) {
            val dupIndex = withoutPartial.indexOfLast {
                it.role == ChatRole.USER && !it.isPartial && it.text == display
            }
            if (dupIndex >= 0) {
                return state.copy(
                    messages = withoutPartial,
                    streamingMessageId = null,
                    streamingAssistantRaw = null
                ).withLiveGateway()
            }
        }
        val finalMessage = ChatMessage(
            id = finalId,
            role = event.role,
            text = display,
            isPartial = false
        )
        var next = state.copy(
            messages = withoutPartial + finalMessage,
            streamingMessageId = null,
            streamingAssistantRaw = null,
            modalParseWarning = if (event.role == ChatRole.USER) null else state.modalParseWarning,
            awaitingAssistantReply = if (event.role == ChatRole.ASSISTANT) false else state.awaitingAssistantReply
        )
        if (event.role == ChatRole.ASSISTANT) {
            next = applyAssistantModal(next, rawText)
        }
        return next.withLiveGateway()
    }

    /** Gateway 流式场景：final 帧可能比 delta 累积更短，取更完整的正文解析 MODAL。 */
    private fun resolveAssistantRawText(
        finalText: String,
        streamedText: String,
        role: ChatRole
    ): String {
        if (role != ChatRole.ASSISTANT) return finalText
        if (finalText.isBlank()) return streamedText
        if (streamedText.isBlank()) return finalText
        return when {
            finalText.contains(MessageBlockParser.MARKER_MODAL) -> finalText
            streamedText.contains(MessageBlockParser.MARKER_MODAL) -> streamedText
            MessageBlockParser.hasModalDirective(finalText) -> finalText
            MessageBlockParser.hasModalDirective(streamedText) -> streamedText
            finalText.length >= streamedText.length -> finalText
            else -> streamedText
        }
    }

    private fun displayTextFor(raw: String, role: ChatRole): String {
        if (role == ChatRole.USER) {
            return UploadMessageMarker.displayTextForChat(raw)
        }
        return MessageBlockParser.chatDisplayText(raw)
    }

    /**
     * 流式阶段：JSON 完整（含 END 或裸 JSON）才归约 MODAL，避免半截 JSON 每帧重载白板。
     */
    private fun tryApplyAssistantModalFromRaw(state: ShellUiState, raw: String): ShellUiState {
        if (!MessageBlockParser.hasModalDirective(raw)) return state
        if (!raw.contains(MessageBlockParser.MARKER_END)) {
            val parsed = MessageBlockParser.parse(raw)
            if (parsed.modalAction == null || parsed.modalParseError != null) return state
        }
        return applyAssistantModal(state, raw)
    }

    private fun applyAssistantModal(state: ShellUiState, raw: String): ShellUiState {
        val parsed = MessageBlockParser.parse(raw)
        if (parsed.modalParseError != null) {
            return state.copy(modalParseWarning = "白板内容解析失败，已仅显示文字回复")
        }
        val action = parsed.modalAction ?: return state.copy(modalParseWarning = null)
        val modalState = ModalStateReducer.apply(
            current = state.modalState,
            action = action,
            incoming = parsed.modalBlocks
        )
        val modalHistory = when (action) {
            ModalAction.OPEN, ModalAction.UPDATE ->
                ModalHistoryReducer.pushSnapshot(state.modalHistory, modalState)
            ModalAction.CLOSE -> state.modalHistory
            ModalAction.NOOP -> state.modalHistory
        }
        var next = state.copy(
            modalState = modalState,
            modalHistory = modalHistory,
            modalParseWarning = null
        )
        when (action) {
            ModalAction.OPEN -> next = next.copy(
                activeModule = ModuleId.WHITEBOARD,
                moduleLoadState = ModuleLoadState.READY,
                panelState = PanelState.EXPANDED,
                pendingPanelCommand = PanelCommand.EXPANDED
            )
            ModalAction.CLOSE -> next = next.copy(
                panelState = PanelState.COLLAPSED,
                pendingPanelCommand = PanelCommand.COLLAPSED
            )
            ModalAction.UPDATE, ModalAction.NOOP -> Unit
        }
        return next
    }

    private fun reduceIntentPreload(
        state: ShellUiState,
        event: ClawSessionEvent.IntentPreload
    ): ShellUiState {
        val preload = event.preload
        val panelState = preload.drawerState?.toPanelState() ?: state.panelState
        return state.copy(
            sessionId = preload.sessionId,
            activeModule = preload.targetModule,
            moduleLoadState = ModuleLoadState.PRELOADING,
            panelState = panelState,
            pendingPanelCommand = preload.drawerState,
            awaitingAssistantReply = false
        )
    }

    private fun reduceIntentFinal(
        state: ShellUiState,
        event: ClawSessionEvent.IntentFinal
    ): ShellUiState {
        val response = event.response
        var next = state.copy(
            sessionId = response.sessionId,
            bannerError = null,
            awaitingAssistantReply = false
        )

        if (response.textContent.isNotBlank()) {
            val assistantId = finalizedIdFor(response.turnId, ChatRole.ASSISTANT)
            val withoutStreaming = next.messages.filterNot {
                it.id == streamingIdFor(response.turnId, ChatRole.ASSISTANT)
            }
            val streamedText = next.streamingAssistantRaw.orEmpty().ifBlank {
                next.messages.find {
                    it.id == streamingIdFor(response.turnId, ChatRole.ASSISTANT)
                }?.text.orEmpty()
            }
            val rawText = resolveAssistantRawText(response.textContent, streamedText, ChatRole.ASSISTANT)
            val displayText = displayTextFor(rawText, ChatRole.ASSISTANT)
            val alreadyFinal = withoutStreaming.any { it.id == assistantId }
            val dupByText = withoutStreaming.indexOfLast {
                it.role == ChatRole.ASSISTANT && !it.isPartial && it.text == displayText
            }
            next = next.copy(
                messages = if (alreadyFinal || dupByText >= 0) {
                    withoutStreaming
                } else {
                    withoutStreaming + ChatMessage(
                        id = assistantId,
                        role = ChatRole.ASSISTANT,
                        text = displayText
                    )
                },
                streamingMessageId = null,
                streamingAssistantRaw = null
            )
            next = applyAssistantModal(next, rawText)
        }

        val intent = response.intent ?: return next.copy(
            moduleLoadState = if (next.activeModule == ModuleId.NONE) {
                ModuleLoadState.IDLE
            } else {
                ModuleLoadState.READY
            }
        )

        val panelState = intent.drawerState?.toPanelState() ?: next.panelState
        next = next.copy(
            activeModule = intent.targetModule,
            modulePayload = intent.payload,
            moduleLoadState = ModuleLoadState.READY,
            panelState = panelState,
            pendingPanelCommand = intent.drawerState
        )

        return next
    }

    fun applyPanelCommandConsumed(state: ShellUiState): ShellUiState =
        state.copy(pendingPanelCommand = null)

    fun applyMapInstructionConsumed(state: ShellUiState): ShellUiState = state

    fun applyUserPanelOverride(state: ShellUiState, panelState: PanelState): ShellUiState =
        // 方案 §3：手动收放只改抽屉高度，不影响 Agent 白板状态
        state.copy(panelState = panelState, pendingPanelCommand = null)

    fun applyUserModuleSelect(state: ShellUiState, module: ModuleId): ShellUiState =
        state.copy(
            activeModule = module,
            panelState = PanelState.EXPANDED,
            pendingPanelCommand = null
        )

    private fun streamingIdFor(turnId: String, role: ChatRole): String =
        "stream-$turnId-${role.name}"

    private fun finalizedIdFor(turnId: String, role: ChatRole): String =
        "final-$turnId-${role.name}"
}
