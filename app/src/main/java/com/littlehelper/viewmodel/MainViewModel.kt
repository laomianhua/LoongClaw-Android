package com.littlehelper.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.littlehelper.AppPhase
import com.littlehelper.AttachmentPanelState
import com.littlehelper.ChatMessage
import com.littlehelper.ChatRole
import com.littlehelper.InputComposerUiState
import com.littlehelper.InputMode
import com.littlehelper.LittleHelperApplication
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
import com.littlehelper.shell.session.OpenClawUserMessageCommitter
import com.littlehelper.shell.modal.ModalSlotReducer
import com.littlehelper.shell.modal.ModalState
import com.littlehelper.shell.model.CapturePhase
import com.littlehelper.shell.model.ConnectionState
import com.littlehelper.shell.model.ModuleId
import com.littlehelper.shell.model.PanelCommand
import com.littlehelper.shell.model.ShellMode
import com.littlehelper.shell.model.ShellUiState
import com.littlehelper.shell.projection.ShellUiProjector
import com.littlehelper.shell.transport.OpenClawDeviceIdentityStore
import com.littlehelper.settings.AppUiLanguage
import com.littlehelper.settings.AppUiLanguageStore
import com.littlehelper.settings.GatewayConnectionSettings
import com.littlehelper.settings.GatewayHandshakeTestResult
import com.littlehelper.settings.GatewaySettingsStore
import com.littlehelper.settings.GatewayTtsStore
import com.littlehelper.settings.HandshakeProgressMapper
import com.littlehelper.shell.transport.GatewayConfigProvider
import com.littlehelper.shell.transport.ConnectErrorPresentation
import com.littlehelper.shell.transport.GatewayConnectErrorMapper
import com.littlehelper.shell.transport.GatewayConnectionTester
import com.littlehelper.tts.TtsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val shellMode: ShellMode = ShellMode.LOCAL,
    val shell: ShellUiState = ShellUiState(),
    val phase: AppPhase = AppPhase.IDLE,
    val messages: List<ChatMessage> = emptyList(),
    val speakingMessageId: String? = null,
    val panelState: PanelState = PanelState.COLLAPSED,
    val gatewayTtsEnabled: Boolean = GatewayTtsStore.DEFAULT_ENABLED,
    val appUiLanguage: AppUiLanguage = AppUiLanguage.ZH,
    val localModalState: ModalState = ModalState(),
    val inputComposer: InputComposerUiState = InputComposerUiState(),
    val showGatewaySettings: Boolean = false,
    val gatewaySettingsForm: GatewayConnectionSettings = GatewayConnectionSettings(),
    val gatewayTestResult: GatewayHandshakeTestResult? = null,
    val gatewayHandshakeProgress: com.littlehelper.settings.GatewayHandshakeProgress? = null,
    val gatewaySettingsTesting: Boolean = false,
    val gatewayBaseUrl: String = "",
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val gatewayTtsStore = GatewayTtsStore(application)
    private val appUiLanguageStore = AppUiLanguageStore(application)
    private val gatewaySettingsStore = GatewaySettingsStore(application)
    private val chatHistoryStore = ChatHistoryStore(application)
    private val openClawIdentityStore = OpenClawDeviceIdentityStore(application)
    private val gatewayConnection =
        (application as LittleHelperApplication).gatewayConnectionManager
    private var fileUploadManager =
        FileUploadManager(host = gatewayConnection.currentGatewayConfig().host)
    private val welcomeMessage = ChatMessage.assistant(
        application.getString(com.littlehelper.R.string.welcome_message)
    )
    private val loadedChatHistory = chatHistoryStore.load()
    private val initialChatMessages = loadedChatHistory.ifEmpty { listOf(welcomeMessage) }

    private val _uiState = MutableStateFlow(
        MainUiState(
            shellMode = ShellMode.OPENCLAW,
            gatewayTtsEnabled = gatewayTtsStore.load(),
            appUiLanguage = appUiLanguageStore.load(),
            messages = initialChatMessages,
            gatewaySettingsForm = gatewaySettingsStore.loadForForm(),
            gatewayBaseUrl = gatewayConnection.currentGatewayConfig().httpBaseUrl(),
            showGatewaySettings = GatewayConfigProvider.needsFirstTimeSetup(application),
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var chatHistorySaveJob: Job? = null
    private var ttsManager: TtsManager? = null
    private var lastSpokenShellAssistantId: String? = null
    private var openClawTtsBaselineSet = false
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
        gatewayConnection.setShellModeProvider { _uiState.value.shellMode }
        gatewayConnection.setOnGatewayConfigApplied { config ->
            fileUploadManager = FileUploadManager(host = config.host)
            _uiState.update {
                it.copy(
                    gatewayBaseUrl = config.httpBaseUrl(),
                )
            }
        }
        viewModelScope.launch {
            gatewayConnection.sessionController.shellState.collect { shell ->
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
                }
            }
        }
    }

    fun attachTtsManager(manager: TtsManager) {
        ttsManager = manager
    }

    fun onAppResumed() {
        gatewayConnection.onAppResumed()
    }

    fun retryOpenClawConnect() {
        gatewayConnection.retryOpenClawConnect()
    }

    fun setGatewayTtsEnabled(enabled: Boolean) {
        if (enabled == _uiState.value.gatewayTtsEnabled) return
        gatewayTtsStore.save(enabled)
        _uiState.update { it.copy(gatewayTtsEnabled = enabled) }
        if (!enabled) {
            stopGatewayTtsPlayback()
        }
    }

    fun setAppUiLanguage(language: AppUiLanguage, onApplied: () -> Unit) {
        if (language == _uiState.value.appUiLanguage) return
        appUiLanguageStore.save(language)
        _uiState.update { it.copy(appUiLanguage = language) }
        onApplied()
    }

    fun toggleGatewayTts() {
        setGatewayTtsEnabled(!_uiState.value.gatewayTtsEnabled)
    }

    fun openGatewaySettings() {
        val form = gatewaySettingsStore.loadForForm()
        _uiState.update {
            it.copy(
                showGatewaySettings = true,
                gatewaySettingsForm = form,
                gatewayTestResult = null,
                gatewayHandshakeProgress = null,
            )
        }
    }

    fun dismissGatewaySettings() {
        val form = gatewaySettingsStore.loadForForm()
        _uiState.update {
            it.copy(
                showGatewaySettings = false,
                gatewayTestResult = null,
                gatewayHandshakeProgress = null,
                gatewaySettingsForm = form,
            )
        }
    }

    fun updateGatewaySettingsForm(form: GatewayConnectionSettings) {
        _uiState.update {
            it.copy(
                gatewaySettingsForm = form,
                gatewayTestResult = null,
                gatewayHandshakeProgress = null,
            )
        }
    }

    fun testGatewayConnection() {
        val form = _uiState.value.gatewaySettingsForm
        if (form.host.isBlank()) {
            _uiState.update {
                it.copy(
                    gatewayTestResult = GatewayHandshakeTestResult(
                        success = false,
                        title = getApplication<Application>().getString(
                            com.littlehelper.R.string.settings_host_required
                        ),
                    ),
                )
            }
            return
        }
        if (gatewayConnection.openClawClient.connectionState.value == ConnectionState.ONLINE &&
            gatewayConnection.gatewaySettingsFormMatchesActive(form)
        ) {
            val deviceId = openClawIdentityStore.loadOrCreateIdentity().deviceId
            val progress = HandshakeProgressMapper.fromSuccess(
                previous = _uiState.value.gatewayHandshakeProgress,
                deviceId = deviceId,
            )
            _uiState.update {
                it.copy(
                    gatewaySettingsTesting = false,
                    gatewayHandshakeProgress = progress,
                    gatewayTestResult = GatewayHandshakeTestResult(
                        success = true,
                        title = getApplication<Application>().getString(
                            com.littlehelper.R.string.settings_test_matches_main_connection
                        ),
                    ),
                )
            }
            return
        }
        viewModelScope.launch {
            val saved = gatewaySettingsStore.load()
            if (!form.hasSameAuthCredentialsAs(saved)) {
                openClawIdentityStore.clearDeviceToken()
            }
            val previousProgress = _uiState.value.gatewayHandshakeProgress
            _uiState.update {
                it.copy(
                    gatewaySettingsTesting = true,
                    gatewayTestResult = null,
                    gatewayHandshakeProgress = HandshakeProgressMapper.onTestStarted(previousProgress),
                )
            }
            val deviceId = openClawIdentityStore.loadOrCreateIdentity().deviceId
            val result = GatewayConnectionTester.testWebSocketHandshake(
                settings = form,
                identityStore = openClawIdentityStore,
            )
            val testResult = result.fold(
                onSuccess = { message ->
                    val progress = HandshakeProgressMapper.fromSuccess(previousProgress, deviceId)
                    _uiState.update {
                        it.copy(gatewayHandshakeProgress = progress)
                    }
                    GatewayHandshakeTestResult(success = true, title = message)
                },
                onFailure = { error ->
                    val presentation = GatewayConnectErrorMapper.mapThrowable(error)
                    val progress = HandshakeProgressMapper.fromFailure(
                        previous = previousProgress,
                        kind = presentation.kind,
                        title = presentation.title,
                        detail = presentation.detail,
                        deviceId = deviceId,
                    )
                    _uiState.update {
                        it.copy(gatewayHandshakeProgress = progress)
                    }
                    toHandshakeTestResult(presentation, deviceId)
                },
            )
            _uiState.update {
                it.copy(
                    gatewaySettingsTesting = false,
                    gatewayTestResult = testResult,
                )
            }
        }
    }

    private fun toHandshakeTestResult(
        presentation: ConnectErrorPresentation,
        deviceId: String,
    ): GatewayHandshakeTestResult = GatewayHandshakeTestResult(
        success = false,
        title = presentation.title,
        gatewayCode = presentation.gatewayCode,
        detail = presentation.detail,
        kind = presentation.kind,
        deviceId = deviceId.takeIf { presentation.pairingRequired },
    )

    fun saveGatewaySettingsAndConnect() {
        val form = _uiState.value.gatewaySettingsForm
        if (!form.isConfigured) {
            _uiState.update {
                it.copy(
                    gatewayTestResult = GatewayHandshakeTestResult(
                        success = false,
                        title = getApplication<Application>().getString(
                            com.littlehelper.R.string.settings_host_required
                        ),
                    ),
                )
            }
            return
        }
        val previous = gatewaySettingsStore.load()
        if (!form.hasSameAuthCredentialsAs(previous)) {
            openClawIdentityStore.clearDeviceToken()
        }
        gatewaySettingsStore.save(form)
        gatewayConnection.applyGatewayConfig(form.toGatewayConfig())
        _uiState.update {
            it.copy(
                showGatewaySettings = false,
                gatewayTestResult = null,
                gatewaySettingsForm = form,
            )
        }
    }

    fun setPanelState(state: PanelState) {
        if (_uiState.value.shellMode == ShellMode.OPENCLAW) {
            gatewayConnection.sessionController.onUserPanelOverride(state)
            return
        }
        _uiState.update { it.copy(panelState = state) }
    }

    fun selectModalTab(id: String) {
        if (_uiState.value.shellMode != ShellMode.OPENCLAW) return
        gatewayConnection.sessionController.patch { shell ->
            val slots = ModalSlotReducer.selectTab(shell.modalSlots, id)
            if (!slots.slotMap.containsKey(id)) return@patch shell
            shell.copy(
                modalSlots = slots,
                modalState = shell.modalState.copy(
                    isOpen = true,
                    blocks = emptyList(),
                ),
            )
        }
    }

    fun closeModalTab(id: String) {
        if (_uiState.value.shellMode != ShellMode.OPENCLAW) return
        gatewayConnection.sessionController.patch { shell ->
            val slots = ModalSlotReducer.closeTab(shell.modalSlots, id)
            var next = shell.copy(modalSlots = slots)
            if (slots.isEmpty) {
                next = next.copy(
                    modalState = ModalState(
                        isOpen = false,
                        blocks = emptyList(),
                        loadRevision = shell.modalState.loadRevision,
                    ),
                    panelState = PanelState.COLLAPSED,
                    pendingPanelCommand = PanelCommand.COLLAPSED,
                )
            }
            next
        }
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
                    draftText = if (mode == InputMode.VOICE) "" else it.inputComposer.draftText,
                ),
            )
        }
    }

    fun updateComposerDraft(text: String) {
        _uiState.update { it.copy(inputComposer = it.inputComposer.copy(draftText = text)) }
    }

    fun onAttachmentPicked(bytes: ByteArray, fileName: String, mimeType: String) {
        if (!AttachmentSizeValidator.isWithinLimit(bytes.size, mimeType, fileName)) {
            Toast.makeText(
                getApplication(),
                AttachmentSizeValidator.oversizeMessage(mimeType, fileName),
                Toast.LENGTH_SHORT,
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
                    mimeType = mimeType,
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
                                thumbnailBytes = thumbnailBytes,
                            ),
                        ),
                    )
                }
            }.onFailure { error ->
                Toast.makeText(
                    getApplication(),
                    "文件上传失败：${error.message ?: "未知错误"}",
                    Toast.LENGTH_SHORT,
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
            runCatching { gatewayConnection.sessionController.sendTextMessage(wireText) }
                .onSuccess {
                    clearPendingUploadState()
                    _uiState.update { it.copy(inputComposer = InputComposerUiState(mode = InputMode.VOICE)) }
                }
                .onFailure { error -> showOpenClawError("发送失败: ${error.message}") }
        }
    }

    fun deleteChatMessage(messageId: String) {
        if (_uiState.value.shellMode == ShellMode.OPENCLAW) {
            gatewayConnection.sessionController.patch { shell ->
                shell.copy(messages = shell.messages.filterNot { it.id == messageId })
            }
            persistChatHistoryNow(gatewayConnection.sessionController.shellState.value.messages)
            return
        }
        _uiState.update { state ->
            state.copy(messages = state.messages.filterNot { it.id == messageId })
        }
        persistChatHistoryNow(_uiState.value.messages)
    }

    fun confirmClearAllRecords() {
        val doneMessage = getApplication<Application>().getString(
            com.littlehelper.R.string.clear_all_done_message,
        )
        if (_uiState.value.shellMode == ShellMode.OPENCLAW) {
            gatewayConnection.sessionController.patch { shell ->
                val welcome = shell.messages.firstOrNull()
                val newMessages = if (welcome != null) listOf(welcome) else emptyList()
                shell.copy(messages = newMessages)
            }
            persistChatHistoryNow(gatewayConnection.sessionController.shellState.value.messages)
        } else {
            _uiState.update { state ->
                state.copy(messages = listOf(ChatMessage.assistant(doneMessage)), phase = AppPhase.IDLE)
            }
            persistChatHistoryNow(_uiState.value.messages)
        }
        speakShellOrLocalText(doneMessage)
    }

    fun triggerClearAllConfirmation() {
        _clearAllConfirmation.tryEmit(Unit)
    }

    private fun commitOpenClawUserMessage(text: String) {
        gatewayConnection.sessionController.patch { shell ->
            shell.copy(messages = OpenClawUserMessageCommitter.appendUserMessage(shell.messages, text))
        }
    }

    private fun showOpenClawError(message: String) {
        val display = formatUserFacingError(message)
        gatewayConnection.sessionController.patch { shell ->
            shell.copy(
                messages = shell.messages.filterNot {
                    it.isPartial || it.id == ChatMessage.PARTIAL_USER_ID
                } + ChatMessage.assistant(display, isError = true),
                capturePhase = CapturePhase.IDLE,
                bannerError = display,
                awaitingAssistantReply = false,
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
        gatewayConnection.sessionController.patch { it.copy(speakingMessageId = messageId) }
        ttsManager?.speak(text) {
            gatewayConnection.sessionController.patch { shell ->
                if (shell.speakingMessageId == messageId) shell.copy(speakingMessageId = null)
                else shell
            }
        } ?: gatewayConnection.sessionController.patch { it.copy(speakingMessageId = null) }
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

    private fun handleOpenClawAssistantTts(
        previousMessages: List<ChatMessage>,
        nextMessages: List<ChatMessage>,
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
            gatewayConnection.sessionController.patch { it.copy(speakingMessageId = null) }
        } else {
            _uiState.update { it.copy(speakingMessageId = null) }
        }
    }
}
