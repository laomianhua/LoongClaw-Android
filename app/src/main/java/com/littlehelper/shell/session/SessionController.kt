package com.littlehelper.shell.session



import com.littlehelper.shell.model.ClawSessionEvent

import com.littlehelper.shell.model.ShellUiState

import com.littlehelper.shell.transport.GatewayConnectErrorMapper
import com.littlehelper.shell.transport.ConnectErrorPresentation
import com.littlehelper.shell.transport.OpenClawSessionClient

import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.update

import kotlinx.coroutines.launch



class SessionController(

    private val client: OpenClawSessionClient,

    private val scope: CoroutineScope,

    initialState: ShellUiState

) {

    private val _shellState = MutableStateFlow(initialState)

    val shellState: StateFlow<ShellUiState> = _shellState.asStateFlow()



    private var audioSeq = 0

    private var silentRecoveryActive = false



    init {

        scope.launch {

            client.events.collect { event ->

                apply(event)

            }

        }

    }



    fun isSilentRecoveryActive(): Boolean = silentRecoveryActive



    fun beginSilentRecovery() {

        silentRecoveryActive = true

        _shellState.update {

            it.copy(

                bannerError = null,

                bannerErrorDetail = null,

                connectFailureKind = null,

                connectGatewayCode = null,

                connectUserAction = null,

                pairingRequired = false,

                connectionBannerVisible = false,

                silentReconnectActive = true,

                connectionState = com.littlehelper.shell.model.ConnectionState.CONNECTING

            )

        }

    }



    fun endSilentRecovery(
        success: Boolean,
        error: ConnectErrorPresentation? = null,
    ) {

        silentRecoveryActive = false

        _shellState.update { state ->

            when {

                success -> state.copy(

                    connectionState = com.littlehelper.shell.model.ConnectionState.ONLINE,

                    bannerError = null,

                    bannerErrorDetail = null,

                    connectFailureKind = null,

                    connectGatewayCode = null,

                    connectUserAction = null,

                    pairingRequired = false,

                    connectionBannerVisible = false,

                    silentReconnectActive = false

                )



                error != null -> state.copy(

                    connectionState = com.littlehelper.shell.model.ConnectionState.DEGRADED,

                    bannerError = error.title,

                    bannerErrorDetail = error.detail,

                    connectFailureKind = error.kind,

                    connectGatewayCode = error.gatewayCode,

                    connectUserAction = error.userAction,

                    pairingRequired = error.pairingRequired,

                    connectionBannerVisible = true,

                    silentReconnectActive = false

                )



                else -> state.copy(
                    connectionBannerVisible = true,
                    silentReconnectActive = false
                )

            }

        }

    }



    fun apply(event: ClawSessionEvent) {

        if (silentRecoveryActive) {

            when (event) {

                is ClawSessionEvent.SessionError -> {

                    if (event.pairingRequired) {

                        silentRecoveryActive = false

                        _shellState.update {

                            SessionReducer.reduce(

                                it.copy(connectionBannerVisible = true),

                                event

                            )

                        }

                    }

                    return

                }



                is ClawSessionEvent.SessionOpened -> {

                    silentRecoveryActive = false

                    _shellState.update {

                        SessionReducer.reduce(

                            it.copy(connectionBannerVisible = false),

                            event

                        )

                    }

                    return

                }



                else -> Unit

            }

        }

        _shellState.update { SessionReducer.reduce(it, event) }

    }



    suspend fun retryConnect() {

        if (!silentRecoveryActive) {

            _shellState.update {

                it.copy(

                    connectionState = com.littlehelper.shell.model.ConnectionState.CONNECTING,

                    bannerError = null,

                    bannerErrorDetail = null,

                    connectFailureKind = null,

                    connectGatewayCode = null,

                    connectUserAction = null,

                    pairingRequired = false,

                    connectionBannerVisible = true

                )

            }

        }

        runCatching { client.connect() }

    }



    suspend fun ensureConnected() {

        if (client.connectionState.value == com.littlehelper.shell.model.ConnectionState.ONLINE) return

        client.connect()

    }



    suspend fun sendTextMessage(text: String) {

        ensureConnected()

        client.sendTextMessage(text)

        markAwaitingAssistantReply()

    }



    fun markAwaitingAssistantReply() {

        _shellState.update { it.copy(awaitingAssistantReply = true) }

    }



    fun clearAwaitingAssistantReply() {

        _shellState.update { it.copy(awaitingAssistantReply = false) }

    }



    suspend fun sendVoiceCommand(audioBytes: ByteArray, durationMs: Long) {

        val turnId = java.util.UUID.randomUUID().toString()

        audioSeq = 0

        ensureConnected()

        client.startTurn(turnId)



        val chunkSize = 4096

        var offset = 0

        while (offset < audioBytes.size) {

            val end = minOf(offset + chunkSize, audioBytes.size)

            client.sendAudioChunk(turnId, audioSeq++, audioBytes.copyOfRange(offset, end))

            offset = end

        }

        client.endTurn(turnId, durationMs)

    }



    fun onPanelCommandConsumed() {

        _shellState.update { SessionReducer.applyPanelCommandConsumed(it) }

    }



    fun onMapInstructionConsumed() {

        _shellState.update { SessionReducer.applyMapInstructionConsumed(it) }

    }



    fun onUserPanelOverride(panelState: com.littlehelper.PanelState) {

        _shellState.update { SessionReducer.applyUserPanelOverride(it, panelState) }

    }



    fun onUserModuleSelect(module: com.littlehelper.shell.model.ModuleId) {

        _shellState.update { SessionReducer.applyUserModuleSelect(it, module) }

    }



    fun setCapturePhase(phase: com.littlehelper.shell.model.CapturePhase) {

        _shellState.update { it.copy(capturePhase = phase) }

    }



    fun patch(transform: (ShellUiState) -> ShellUiState) {

        _shellState.update(transform)

    }

}


