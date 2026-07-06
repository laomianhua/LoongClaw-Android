package com.littlehelper.gateway

import android.app.Application
import com.littlehelper.settings.GatewayConnectionSettings
import com.littlehelper.settings.GatewaySettingsStore
import com.littlehelper.shell.model.ConnectionState
import com.littlehelper.shell.model.ShellMode
import com.littlehelper.shell.model.ShellUiState
import com.littlehelper.shell.session.SessionController
import com.littlehelper.shell.transport.ConnectErrorPresentation
import com.littlehelper.shell.transport.ConnectFailureKind
import com.littlehelper.shell.transport.GatewayConfig
import com.littlehelper.shell.transport.GatewayConfigProvider
import com.littlehelper.shell.transport.GatewayConnectErrorMapper
import com.littlehelper.shell.transport.GatewayRuntime
import com.littlehelper.shell.transport.OpenClawDeviceIdentityStore
import com.littlehelper.shell.transport.OpenClawSessionClient
import com.littlehelper.shell.transport.OpenClawSessionClientFactory
import com.littlehelper.shell.transport.WebSocketOpenClawSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 进程级 Gateway 主连接（WebSocket + 静默重连）。
 * Phase A：自 [com.littlehelper.viewmodel.MainViewModel] 原样迁入，行为不变。
 * Phase B：Gateway 已配置时启 [GatewayConnectionService] 前台保活（不改重连数字）。
 */
class GatewayConnectionManager(
    private val application: Application,
    private val appScope: CoroutineScope,
    initialShellState: ShellUiState,
) {
    companion object {
        private const val OPENCLAW_RECONNECT_GRACE_MS = 60_000L
        private const val OPENCLAW_RECONNECT_INTERVAL_MS = 2_000L
        private const val OPENCLAW_PAIRING_RETRY_MS = 3_000L
        private const val OPENCLAW_RATE_LIMIT_RETRY_MS = 90_000L
    }

    private val gatewaySettingsStore = GatewaySettingsStore(application)
    private val openClawIdentityStore = OpenClawDeviceIdentityStore(application)

    private var gatewayConfig: GatewayConfig = GatewayConfigProvider.resolve(application).also { config ->
        GatewayRuntime.setConfig(config)
    }

    val openClawClient: OpenClawSessionClient =
        OpenClawSessionClientFactory.create(application, appScope)

    val sessionController: SessionController = SessionController(
        client = openClawClient,
        scope = appScope,
        initialState = initialShellState,
    )

    val connectionState: StateFlow<ConnectionState> = openClawClient.connectionState

    private var openClawReconnectJob: Job? = null
    private var lastOpenClawConnectionState = ConnectionState.DISCONNECTED
    private var foregroundServiceActive = false

    private var onGatewayConfigApplied: ((GatewayConfig) -> Unit)? = null
    private var shellModeProvider: () -> ShellMode = { ShellMode.OPENCLAW }

    fun setOnGatewayConfigApplied(listener: (GatewayConfig) -> Unit) {
        onGatewayConfigApplied = listener
    }

    fun setShellModeProvider(provider: () -> ShellMode) {
        shellModeProvider = provider
    }

    fun currentGatewayConfig(): GatewayConfig = gatewayConfig

    fun gatewaySettingsFormMatchesActive(form: GatewayConnectionSettings): Boolean =
        form.hasSameConnectionParamsAs(GatewayConnectionSettings.fromGatewayConfig(gatewayConfig)) &&
            gatewayConfig.isConnectable

    fun start() {
        appScope.launch {
            sessionController.patch {
                it.copy(
                    deviceId = openClawIdentityStore.loadOrCreateIdentity().deviceId,
                    connectionBannerVisible = false,
                )
            }
            if (gatewaySettingsStore.isConfigured()) {
            syncForegroundService(ConnectionState.CONNECTING)
            scheduleSilentOpenClawReconnect()
        }
        }
        appScope.launch {
            openClawClient.connectionState.collect {
                if (shellModeProvider() != ShellMode.OPENCLAW) return@collect
                updateForegroundNotification()
                val state = openClawClient.connectionState.value
                if (lastOpenClawConnectionState == ConnectionState.ONLINE &&
                    state != ConnectionState.ONLINE &&
                    openClawReconnectJob?.isActive != true &&
                    gatewaySettingsStore.isConfigured()
                ) {
                    scheduleSilentOpenClawReconnect()
                }
                lastOpenClawConnectionState = state
            }
        }
        appScope.launch {
            sessionController.shellState.collect {
                if (shellModeProvider() != ShellMode.OPENCLAW) return@collect
                updateForegroundNotification()
            }
        }
    }

    fun onAppResumed() {
        if (shellModeProvider() != ShellMode.OPENCLAW) return
        if (!gatewaySettingsStore.isConfigured()) return
        syncForegroundService(notificationConnectionState())
        if (openClawClient.connectionState.value == ConnectionState.ONLINE) return
        if (openClawReconnectJob?.isActive == true) return
        scheduleSilentOpenClawReconnect()
    }

    fun retryOpenClawConnect() {
        if (shellModeProvider() != ShellMode.OPENCLAW) return
        if (!gatewaySettingsStore.isConfigured()) return
        openClawReconnectJob?.cancel()
        scheduleSilentOpenClawReconnect()
    }

    fun applyGatewayConfig(config: GatewayConfig) {
        gatewayConfig = config
        GatewayRuntime.setConfig(config)
        (openClawClient as? WebSocketOpenClawSessionClient)?.updateConfig(config)
        onGatewayConfigApplied?.invoke(config)
        if (shellModeProvider() != ShellMode.OPENCLAW) return
        if (gatewaySettingsStore.isConfigured()) {
            syncForegroundService(openClawClient.connectionState.value)
        } else {
            stopForegroundService()
        }
        appScope.launch {
            openClawReconnectJob?.cancel()
            runCatching { openClawClient.disconnect() }
            scheduleSilentOpenClawReconnect()
        }
    }

    private fun scheduleSilentOpenClawReconnect() {
        if (shellModeProvider() != ShellMode.OPENCLAW) return
        if (!gatewaySettingsStore.isConfigured()) return
        if (openClawReconnectJob?.isActive == true) return
        openClawReconnectJob?.cancel()
        openClawReconnectJob = appScope.launch {
            sessionController.beginSilentRecovery()
            updateForegroundNotification()
            val deadline = System.currentTimeMillis() + OPENCLAW_RECONNECT_GRACE_MS
            var lastPresentation: ConnectErrorPresentation? = null
            while (isActive && System.currentTimeMillis() < deadline) {
                if (openClawClient.connectionState.value == ConnectionState.ONLINE) {
                    sessionController.endSilentRecovery(success = true)
                    return@launch
                }
                val result = runCatching { sessionController.retryConnect() }
                if (openClawClient.connectionState.value == ConnectionState.ONLINE) {
                    sessionController.endSilentRecovery(success = true)
                    return@launch
                }
                when (sessionController.shellState.value.connectFailureKind) {
                    ConnectFailureKind.RATE_LIMITED -> {
                        delay(OPENCLAW_RATE_LIMIT_RETRY_MS)
                        continue
                    }
                    ConnectFailureKind.PAIRING_REQUIRED -> {
                        delay(OPENCLAW_PAIRING_RETRY_MS)
                        continue
                    }
                    else -> if (sessionController.shellState.value.pairingRequired) {
                        delay(OPENCLAW_PAIRING_RETRY_MS)
                        continue
                    }
                }
                result.exceptionOrNull()?.let { error ->
                    lastPresentation = GatewayConnectErrorMapper.mapThrowable(error)
                }
                delay(OPENCLAW_RECONNECT_INTERVAL_MS)
            }
            sessionController.endSilentRecovery(
                success = false,
                error = lastPresentation ?: ConnectErrorPresentation(
                    kind = ConnectFailureKind.UNKNOWN,
                    title = "无法连接 OpenClaw Gateway",
                    detail = "请检查网络与 Gateway 设置后重试",
                ),
            )
        }
    }

    private fun syncForegroundService(state: ConnectionState) {
        if (!gatewaySettingsStore.isConfigured() || shellModeProvider() != ShellMode.OPENCLAW) {
            stopForegroundService()
            return
        }
        val effective = if (state == ConnectionState.DISCONNECTED) notificationConnectionState() else state
        if (!foregroundServiceActive) {
            GatewayConnectionService.start(application, effective)
            foregroundServiceActive = true
        } else {
            GatewayConnectionService.updateState(application, effective)
        }
    }

    private fun updateForegroundNotification() {
        if (!foregroundServiceActive) return
        GatewayConnectionService.updateState(application, notificationConnectionState())
    }

    /**
     * 与 [com.littlehelper.shell.projection.ShellUiProjector.gatewayConnectionState] 一致：
     * 静默重连期间通知显示「正在连接」，而非底层 WS 尚未拨号时的 DISCONNECTED。
     */
    private fun notificationConnectionState(): ConnectionState {
        if (openClawReconnectJob?.isActive == true || sessionController.isSilentRecoveryActive()) {
            return ConnectionState.CONNECTING
        }
        return openClawClient.connectionState.value
    }

    private fun stopForegroundService() {
        if (!foregroundServiceActive) return
        GatewayConnectionService.stop(application)
        foregroundServiceActive = false
    }
}
