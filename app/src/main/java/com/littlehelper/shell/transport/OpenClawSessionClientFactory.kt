package com.littlehelper.shell.transport

import android.content.Context
import com.littlehelper.BuildConfig
import com.littlehelper.settings.AssistantToneStore
import kotlinx.coroutines.CoroutineScope

object OpenClawSessionClientFactory {

    fun create(context: Context, scope: CoroutineScope): OpenClawSessionClient {
        return if (BuildConfig.USE_OPENCLAW_MOCK) {
            MockOpenClawSessionClient(scope)
        } else {
            WebSocketOpenClawSessionClient(
                config = GatewayConfig.fromBuildConfig(),
                identityStore = OpenClawDeviceIdentityStore(context),
                toneStore = AssistantToneStore(context),
                scope = scope
            )
        }
    }
}
