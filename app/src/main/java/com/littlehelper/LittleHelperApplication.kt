package com.littlehelper

import android.app.Application
import com.littlehelper.chat.ChatHistoryStore
import com.littlehelper.gateway.GatewayConnectionManager
import com.littlehelper.shell.model.ShellUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LittleHelperApplication : Application() {

    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var gatewayConnectionManager: GatewayConnectionManager
        private set

    override fun onCreate() {
        super.onCreate()
        val chatHistoryStore = ChatHistoryStore(this)
        val welcomeMessage = ChatMessage.assistant(getString(R.string.welcome_message))
        val loadedChatHistory = chatHistoryStore.load()
        val initialShellState = if (loadedChatHistory.isNotEmpty()) {
            ShellUiState.initial(welcomeMessage = null).copy(messages = loadedChatHistory)
        } else {
            ShellUiState.initial(welcomeMessage)
        }
        gatewayConnectionManager = GatewayConnectionManager(
            application = this,
            appScope = applicationScope,
            initialShellState = initialShellState,
        )
        gatewayConnectionManager.start()
    }
}
