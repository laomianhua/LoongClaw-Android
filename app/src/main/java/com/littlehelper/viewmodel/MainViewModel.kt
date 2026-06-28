package com.littlehelper.viewmodel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.littlehelper.AppPhase
import com.littlehelper.AttachmentPanelState
import com.littlehelper.ChatMessage
import com.littlehelper.ChatRole
import com.littlehelper.InputComposerUiState
import com.littlehelper.InputMode
import com.littlehelper.PanelState
import com.littlehelper.PendingAttachmentUi
import com.littlehelper.attachment.AttachmentSizeValidator
import com.littlehelper.attachment.AttachmentThumbnailHelper
import com.littlehelper.attachment.PickedAttachment
import com.littlehelper.upload.UploadMessageMarker
import com.littlehelper.chat.ChatHistoryStore
import com.littlehelper.upload.FileUploadManager
import com.littlehelper.upload.FileUploadResult
import com.littlehelper.network.VolcengineAsrService
import com.littlehelper.network.VolcengineStreamingAsrService
import com.littlehelper.shell.modal.ModalHistoryReducer
import com.littlehelper.shell.modal.ModalHistoryState
import com.littlehelper.shell.modal.ModalHistoryStore
import com.littlehelper.shell.modal.ModalState
import com.littlehelper.shell.model.CapturePhase
import com.littlehelper.shell.model.ConnectionState
import com.littlehelper.shell.model.ModuleId
import com.littlehelper.shell.model.ModuleLoadState
import com.littlehelper.shell.model.PanelCommand
import com.littlehelper.shell.model.ShellMode
import com.littlehelper.shell.model.ShellUiState
import com.littlehelper.shell.projection.ShellUiProjector
import com.littlehelper.shell.session.SessionController
import com.littlehelper.shell.transport.OpenClawDeviceIdentityStore
import com.littlehelper.shell.transport.OpenClawSessionClientFactory
import com.littlehelper.settings.AssistantTone
import com.littlehelper.settings.AssistantToneStore
import com.littlehelper.settings.GatewayTtsStore
import com.littlehelper.tts.TtsManager
import com.littlehelper.BuildConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MainUiState(
    val shellMode: ShellMode = ShellMode.LOCAL,
    val shell: ShellUiState = ShellUiState(),
    val phase: AppPhase = AppPhase.IDLE,
    val messages: List<ChatMessage> = emptyList(),
    val speakingMessageId: String? = null,
    val panelState: PanelState = PanelState.COLLAPSED,
    val assistantTone: AssistantTone = AssistantTone.FRIEND,
    val gatewayTtsEnabled: Boolean = GatewayTtsStore.DEFAULT_ENABLED,
    val localModalState: ModalState = ModalState(),
    val inputComposer: InputComposerUiState = InputComposerUiState()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val OPENCLAW_RECONNECT_GRACE_MS = 20_000L
        private const val OPENCLAW_RECONNECT_INTERVAL_MS = 2_000L
    }

    private val assistantToneStore = AssistantToneStore(application)
    private val gatewayTtsStore = GatewayTtsStore(application)
    private val chatHistoryStore = ChatHistoryStore(application)
    private val modalHistoryStore = ModalHistoryStore(application)
    private val fileUploadManager = FileUploadManager()
    private val welcomeMessage = ChatMessage.assistant(
        application.getString(com.littlehelper.R.string.welcome_message)
    )
    private val loadedChatHistory = chatHistoryStore.load()
    private val loadedModalHistory = modalHistoryStore.load()
    private val initialChatMessages = loadedChatHistory.ifEmpty { listOf(welcomeMessage) }

    private val _uiState = MutableStateFlow(
        MainUiState(
            assistantTone = assistantToneStore.load(),
            gatewayTtsEnabled = gatewayTtsStore.load(),
            messages = initialChatMessages
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val openClawIdentityStore = OpenClawDeviceIdentityStore(application)
    private val openClawClient = OpenClawSessionClientFactory.create(application, viewModelScope)
    private val sessionController = SessionController(
        client = openClawClient,
        scope = viewModelScope,
        initialState = applyRestoredModalHistory(
            if (loadedChatHistory.isNotEmpty()) {
                ShellUiState.initial(welcomeMessage = null).copy(messages = loadedChatHistory)
            } else {
                ShellUiState.initial(welcomeMessage)
            },
            loadedModalHistory
        )
    )

    private var openClawReconnectJob: Job? = null
    private var chatHistorySaveJob: Job? = null
    private var modalHistorySaveJob: Job? = null
    private var lastOpenClawConnectionState = ConnectionState.DISCONNECTED
    private var ttsManager: TtsManager? = null
    private var lastSpokenShellAssistantId: String? = null
    private var openClawTtsBaselineSet = false
    private var lastSyncedToneWire: String? = null
    private var pendingAttachment: PickedAttachment? = null
    private var pendingUploadResult: FileUploadResult? = null

    // ASR 服务保留但停用（已切换系统输入法）
    @Suppress("unused")
    private val asrService = VolcengineAsrService()
    @Suppress("unused")
    private val streamingAsrService = VolcengineStreamingAsrService()

    private val _clearAllConfirmation = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val clearAllConfirmation: SharedFlow<Unit> = _clearAllConfirmation.asSharedFlow()

    init {
        viewModelScope.launch {
            sessionController.shellState.collect { shell ->
                val previousShell = _uiState.value.shell
                _uiState.update { state ->
                    val merged = state.copy(shell = shell)
                    if (merged.shellMode == ShellMode.OPENCLAW) {
                        merged.copy(phase = ShellUiProjector.phase(merged))
                    } else {
                        merged
                    }
                }
                if (_uiState.value.shellMode == ShellMode.OPENCLAW) {
                    handleOpenClawAssistantTts(previousShell.messages, shell.messages)
                    scheduleChatHistorySave(shell.messages)
                    scheduleModalHistorySave(shell.modalHistory)
                }
            }
        }
        if (BuildConfig.USE_OPENCLAW_SHELL) {
            _uiState.update { it.copy(shellMode = ShellMode.OPENCLAW) }
            viewModelScope.launch {
                sessionController.patch {
                    it.copy(
                        deviceId = openClawIdentityStore.loadOrCreateIdentity().deviceId,
                        connectionBannerVisible = false
                    )
                }
                scheduleSilentOpenClawReconnect()
            }
        }
        viewModelScope.launch {
            openClawClient.connectionState.collect { state ->
                if (_uiState.value.shellMode != ShellMode.OPENCLAW) return@collect
                if (lastOpenClawConnectionState == ConnectionState.ONLINE &&
                    state != ConnectionState.ONLINE &&
                    openClawReconnectJob?.isActive != true
                ) {
                    scheduleSilentOpenClawReconnect()
                }
                lastOpenClawConnectionState = state
            }
        }
    }

    fun attachTtsManager(manager: TtsManager) {
        ttsManager = manager
    }

    fun onAppResumed() {
        if (_uiState.value.shellMode != ShellMode.OPENCLAW) return
        if (openClawClient.connectionState.value == ConnectionState.ONLINE) return
        scheduleSilentOpenClawReconnect()
    }

    fun retryOpenClawConnect() {
        if (_uiState.value.shellMode != ShellMode.OPENCLAW) return
        scheduleSilentOpenClawReconnect()
    }

    fun setAssistantTone(tone: AssistantTone) {
        assistantToneStore.save(tone)
        _uiState.update { it.copy(assistantTone = tone) }
        viewModelScope.launch { syncAssistantToneToGateway(force = true) }
    }

    fun setGatewayTtsEnabled(enabled: Boolean) {
        if (enabled == _uiState.value.gatewayTtsEnabled) return
        gatewayTtsStore.save(enabled)
        _uiState.update { it.copy(gatewayTtsEnabled = enabled) }
        if (!enabled) {
            stopGatewayTtsPlayback()
        }
    }

    fun toggleGatewayTts() {
        setGatewayTtsEnabled(!_uiState.value.gatewayTtsEnabled)
    }

    private suspend fun syncAssistantToneToGateway(force: Boolean = false) {
        if (_uiState.value.shellMode != ShellMode.OPENCLAW) return
        val toneWire = assistantToneStore.load().wire
        if (!force && toneWire == lastSyncedToneWire) return
        runCatching {
            sessionController.ensureConnected()
            sessionController.syncAssistantInstructions(assistantToneStore.systemText())
            lastSyncedToneWire = toneWire
        }
    }

    private fun scheduleSilentOpenClawReconnect() {
        if (_uiState.value.shellMode != ShellMode.OPENCLAW) return
        openClawReconnectJob?.cancel()
        openClawReconnectJob = viewModelScope.launch {
            sessionController.beginSilentRecovery()
            val deadline = System.currentTimeMillis() + OPENCLAW_RECONNECT_GRACE_MS
            var lastError: String? = null
            while (isActive && System.currentTimeMillis() < deadline) {
                if (openClawClient.connectionState.value == ConnectionState.ONLINE) {
                    sessionController.endSilentRecovery(success = true)
                    syncAssistantToneToGateway()
                    return@launch
                }
                val result = runCatching { sessionController.retryConnect() }
                if (openClawClient.connectionState.value == ConnectionState.ONLINE) {
                    sessionController.endSilentRecovery(success = true)
                    syncAssistantToneToGateway()
                    return@launch
                }
                if (sessionController.shellState.value.pairingRequired) return@launch
                lastError = result.exceptionOrNull()?.message
                delay(OPENCLAW_RECONNECT_INTERVAL_MS)
            }
            sessionController.endSilentRecovery(
                success = false,
                errorMessage = lastError?.let { "Gateway 连接失败: $it" }
                    ?: "无法连接 OpenClaw Gateway，请检查网络后重试"
            )
        }
    }

    fun setPanelState(state: PanelState) {
        if (_uiState.value.shellMode == ShellMode.OPENCLAW) {
            sessionController.onUserPanelOverride(state)
            return
        }
        _uiState.update { it.copy(panelState = state) }
    }

    /** delta: -1 更早一页，+1 更新一页 */
    fun navigateModalHistory(delta: Int) {
        if (_uiState.value.shellMode != ShellMode.OPENCLAW) return
        sessionController.patch { shell ->
            val (history, modal) = ModalHistoryReducer.navigate(shell.modalHistory, delta)
            val nextModal = modal ?: return@patch shell
            shell.copy(modalHistory = history, modalState = nextModal)
        }
    }

    fun deleteCurrentModalHistoryPage() {
        if (_uiState.value.shellMode != ShellMode.OPENCLAW) return
        sessionController.patch { shell ->
            val (history, modal) = ModalHistoryReducer.deleteAt(
                shell.modalHistory,
                shell.modalHistory.currentIndex,
                shell.modalState
            )
            var next = shell.copy(modalHistory = history, modalState = modal)
            if (!modal.isOpen) {
                next = next.copy(
                    panelState = PanelState.COLLAPSED,
                    pendingPanelCommand = com.littlehelper.shell.model.PanelCommand.COLLAPSED
                )
            }
            next
        }
        persistModalHistoryNow(sessionController.shellState.value.modalHistory)
    }

    fun openCanvasAmap() {
        if (_uiState.value.shellMode != ShellMode.OPENCLAW) return
        com.littlehelper.shell.modules.CanvasWebViewBridge.openAmap()
    }

    fun setInputMode(mode: InputMode) {
        if (_uiState.value.shellMode != ShellMode.OPENCLAW) return
        val phase = ShellUiProjector.phase(_uiState.value)
        if (phase == AppPhase.RECORDING || phase == AppPhase.SENDING || phase == AppPhase.PROCESSING) return
        _uiState.update {
            it.copy(
                inputComposer = it.inputComposer.copy(
                    mode = mode,
                    attachmentPanel = AttachmentPanelState.CLOSED,
                    draftText = if (mode == InputMode.VOICE) "" else it.inputComposer.draftText
                )
            )
        }
    }

    fun updateComposerDraft(text: String) {
        _uiState.update { it.copy(inputComposer = it.inputComposer.copy(draftText = text)) }
    }

    /** 校验大小后上传到 upload_server；成功仅更新「已选」提示，发送时再附加 upload 标记。 */
    fun onAttachmentPicked(bytes: ByteArray, fileName: String, mimeType: String) {
        if (!AttachmentSizeValidator.isWithinLimit(bytes.size, mimeType, fileName)) {
            Toast.makeText(
                getApplication(),
                AttachmentSizeValidator.oversizeMessage(mimeType, fileName),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        viewModelScope.launch {
            val result = runCatching {
                fileUploadManager.upload(bytes, fileName, mimeType)
            }
            result.onSuccess { upload ->
                pendingUploadResult = upload
                pendingAttachment = PickedAttachment(
                    bytes = bytes,
                    fileName = upload.fileName,
                    mimeType = mimeType
                )
                val thumbnailBytes = if (mimeType.startsWith("image/")) {
                    AttachmentThumbnailHelper.createThumbnail(bytes)
                } else {
                    null
                }
                _uiState.update {
                    it.copy(
                        inputComposer = it.inputComposer.copy(
                            pendingAttachment = PendingAttachmentUi(
                                fileName = upload.fileName,
                                mimeType = mimeType,
                                sizeBytes = bytes.size,
                                thumbnailBytes = thumbnailBytes
                            )
                        )
                    )
                }
            }.onFailure { error ->
                Toast.makeText(
                    getApplication(),
                    "文件上传失败：${error.message ?: "未知错误"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun buildWireMessageText(userText: String, upload: FileUploadResult?): String {
        if (upload == null) return userText
        return UploadMessageMarker.append(userText, upload.fileId, upload.fileName)
    }

    private fun buildDisplayMessageText(userText: String, upload: FileUploadResult?): String {
        val trimmed = userText.trim()
        if (trimmed.isNotBlank()) return trimmed
        return upload?.fileName.orEmpty()
    }

    fun clearPendingAttachment() {
        clearPendingUploadState()
    }

    private fun clearPendingUploadState() {
        pendingUploadResult = null
        pendingAttachment = null
        _uiState.update {
            it.copy(inputComposer = it.inputComposer.copy(pendingAttachment = null))
        }
    }

    fun toggleAttachmentPanel() {
        if (_uiState.value.shellMode != ShellMode.OPENCLAW) return
        val phase = ShellUiProjector.phase(_uiState.value)
        if (phase == AppPhase.RECORDING || phase == AppPhase.SENDING || phase == AppPhase.PROCESSING) return
        _uiState.update { state ->
            val next = if (state.inputComposer.attachmentPanel == AttachmentPanelState.OPEN) {
                AttachmentPanelState.CLOSED
            } else {
                AttachmentPanelState.OPEN
            }
            state.copy(inputComposer = state.inputComposer.copy(attachmentPanel = next))
        }
    }

    fun sendComposerText() {
        if (_uiState.value.shellMode != ShellMode.OPENCLAW) return
        val userText = _uiState.value.inputComposer.draftText.trim()
        val upload = pendingUploadResult
        if (userText.isBlank() && upload == null) return
        val phase = ShellUiProjector.phase(_uiState.value)
        if (phase == AppPhase.RECORDING || phase == AppPhase.SENDING || phase == AppPhase.PROCESSING) return
        if (_uiState.value.shell.connectionState != ConnectionState.ONLINE) {
            showOpenClawError("Gateway 未连接，请稍后再试")
            return
        }
        val wireText = buildWireMessageText(userText, upload)
        val displayText = buildDisplayMessageText(userText, upload)
        if (wireText.isBlank()) return
        viewModelScope.launch {
            commitOpenClawUserMessage(displayText)
            runCatching { sessionController.sendTextMessage(wireText) }
                .onSuccess {
                    clearPendingUploadState()
                    _uiState.update { it.copy(inputComposer = InputComposerUiState(mode = InputMode.VOICE)) }
                }
                .onFailure { error -> showOpenClawError("发送失败: ${error.message}") }
        }
    }

    fun deleteChatMessage(messageId: String) {
        if (_uiState.value.shellMode == ShellMode.OPENCLAW) {
            sessionController.patch { shell ->
                shell.copy(messages = shell.messages.filterNot { it.id == messageId })
            }
            persistChatHistoryNow(sessionController.shellState.value.messages)
            return
        }
        _uiState.update { state ->
            state.copy(messages = state.messages.filterNot { it.id == messageId })
        }
        persistChatHistoryNow(_uiState.value.messages)
    }

    fun confirmClearAllRecords() {
        val doneMessage = getApplication<Application>().getString(
            com.littlehelper.R.string.clear_all_done_message
        )
        if (_uiState.value.shellMode == ShellMode.OPENCLAW) {
            sessionController.patch { shell ->
                val welcome = shell.messages.firstOrNull()
                val newMessages = if (welcome != null) listOf(welcome) else emptyList()
                shell.copy(messages = newMessages)
            }
            persistChatHistoryNow(sessionController.shellState.value.messages)
        } else {
            _uiState.update { state ->
                state.copy(messages = listOf(ChatMessage.assistant(doneMessage)), phase = AppPhase.IDLE)
            }
            persistChatHistoryNow(_uiState.value.messages)
        }
        speakShellOrLocalText(doneMessage)
    }

    private val _tempClearAllTrigger = Unit
    fun triggerClearAllConfirmation() {
        _clearAllConfirmation.tryEmit(Unit)
    }

    private fun commitOpenClawUserMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        sessionController.patch { shell ->
            val messages = shell.messages.toMutableList()
            val partialIndex = messages.indexOfLast {
                it.isPartial || it.id == ChatMessage.PARTIAL_USER_ID
            }
            if (partialIndex >= 0) {
                messages[partialIndex] = messages[partialIndex].copy(text = trimmed, isPartial = false)
                return@patch shell.copy(messages = messages)
            }
            val lastUserIndex = messages.indexOfLast { it.role == ChatRole.USER && !it.isPartial }
            if (lastUserIndex >= 0) {
                val last = messages[lastUserIndex]
                when {
                    last.text == trimmed -> return@patch shell
                    trimmed.startsWith(last.text) || last.text.startsWith(trimmed) -> {
                        messages[lastUserIndex] = last.copy(text = trimmed)
                        return@patch shell.copy(messages = messages)
                    }
                }
            }
            if (shell.messages.none { it.role == ChatRole.USER && !it.isPartial && it.text == trimmed }) {
                shell.copy(messages = shell.messages + ChatMessage.user(trimmed, isPartial = false))
            } else {
                shell
            }
        }
    }

    private fun showOpenClawError(message: String) {
        val display = formatUserFacingError(message)
        sessionController.patch { shell ->
            shell.copy(
                messages = shell.messages.filterNot {
                    it.isPartial || it.id == ChatMessage.PARTIAL_USER_ID
                } + ChatMessage.assistant(display, isError = true),
                capturePhase = CapturePhase.IDLE,
                bannerError = display,
                awaitingAssistantReply = false
            )
        }
    }

    private fun formatUserFacingError(raw: String?): String {
        val msg = raw?.trim().orEmpty()
        if (msg.isBlank()) return "操作失败，请重试"
        val lower = msg.lowercase()
        if (lower.contains("missing") && lower.contains("operation")) {
            return "Gateway 尚未就绪，请等左上角变绿后再试"
        }
        if (lower.contains("gateway 未连接") || lower.contains("websocket")) {
            return "Gateway 未连接，请稍后再试"
        }
        return msg
    }

    // ──────────────────── TTS ────────────────────

    private fun speakShellOrLocalText(text: String) {
        if (text.isBlank()) return
        if (_uiState.value.shellMode == ShellMode.OPENCLAW) {
            if (!_uiState.value.gatewayTtsEnabled) return
            val latest = _uiState.value.shell.messages
                .lastOrNull { it.role == ChatRole.ASSISTANT && !it.isPartial } ?: return
            speakShellAssistantText(latest.id, text)
        } else {
            val messageId = _uiState.value.messages.lastOrNull { it.role == ChatRole.ASSISTANT }?.id
            _uiState.update { it.copy(speakingMessageId = messageId) }
            ttsManager?.speak(text) {
                _uiState.update { it.copy(speakingMessageId = null) }
            }
        }
    }

    private fun speakShellAssistantText(messageId: String, text: String) {
        if (text.isBlank()) return
        if (_uiState.value.shellMode == ShellMode.OPENCLAW && !_uiState.value.gatewayTtsEnabled) return
        sessionController.patch { it.copy(speakingMessageId = messageId) }
        ttsManager?.speak(text) {
            sessionController.patch { shell ->
                if (shell.speakingMessageId == messageId) shell.copy(speakingMessageId = null)
                else shell
            }
        } ?: sessionController.patch { it.copy(speakingMessageId = null) }
    }

    private fun scheduleChatHistorySave(messages: List<ChatMessage>) {
        chatHistorySaveJob?.cancel()
        chatHistorySaveJob = viewModelScope.launch {
            delay(ChatHistoryStore.SAVE_DEBOUNCE_MS)
            chatHistoryStore.save(messages)
        }
    }

    private fun persistChatHistoryNow(messages: List<ChatMessage>) {
        chatHistorySaveJob?.cancel()
        chatHistoryStore.save(messages)
    }

    private fun scheduleModalHistorySave(history: ModalHistoryState) {
        modalHistorySaveJob?.cancel()
        modalHistorySaveJob = viewModelScope.launch {
            delay(ModalHistoryStore.SAVE_DEBOUNCE_MS)
            modalHistoryStore.save(history)
        }
    }

    private fun persistModalHistoryNow(history: ModalHistoryState) {
        modalHistorySaveJob?.cancel()
        modalHistoryStore.save(history)
    }

    private fun applyRestoredModalHistory(
        shell: ShellUiState,
        history: ModalHistoryState?
    ): ShellUiState {
        if (history == null || history.entries.isEmpty()) return shell
        val safeIndex = history.currentIndex.coerceIn(0, history.entries.lastIndex)
        val entry = history.entries[safeIndex]
        return shell.copy(
            modalHistory = history.copy(currentIndex = safeIndex).normalized(),
            modalState = ModalState(
                isOpen = true,
                blocks = entry.blocks,
                loadRevision = entry.loadRevision
            ),
            activeModule = ModuleId.WHITEBOARD,
            moduleLoadState = ModuleLoadState.READY,
            panelState = PanelState.COLLAPSED,
            pendingPanelCommand = PanelCommand.COLLAPSED
        )
    }

    private fun handleOpenClawAssistantTts(
        previousMessages: List<ChatMessage>,
        nextMessages: List<ChatMessage>
    ) {
        if (!_uiState.value.gatewayTtsEnabled) return
        if (!openClawTtsBaselineSet) {
            openClawTtsBaselineSet = true
            lastSpokenShellAssistantId = nextMessages.lastOrNull {
                it.role == ChatRole.ASSISTANT && !it.isPartial
            }?.id
            return
        }
        val latest = nextMessages.lastOrNull {
            it.role == ChatRole.ASSISTANT && !it.isPartial && !it.isError
        } ?: return
        if (latest.id == lastSpokenShellAssistantId) return
        if (previousMessages.any { it.id == latest.id }) return
        lastSpokenShellAssistantId = latest.id
        speakShellAssistantText(latest.id, latest.text)
    }

    private fun stopGatewayTtsPlayback() {
        ttsManager?.stop()
        if (_uiState.value.shellMode == ShellMode.OPENCLAW) {
            sessionController.patch { it.copy(speakingMessageId = null) }
        } else {
            _uiState.update { it.copy(speakingMessageId = null) }
        }
    }
}
