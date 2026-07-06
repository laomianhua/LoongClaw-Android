package com.littlehelper.shell.transport

import android.content.Context
import com.littlehelper.BuildConfig
import kotlinx.coroutines.CoroutineScope

object OpenClawSessionClientFactory {

    fun create(context: Context, scope: CoroutineScope): OpenClawSessionClient {
        return if (BuildConfig.USE_OPENCLAW_MOCK) {
            MockOpenClawSessionClient(scope)
        } else {
            WebSocketOpenClawSessionClient(
                initialConfig = GatewayConfigProvider.resolve(context),
                identityStore = OpenClawDeviceIdentityStore(context),
                scope = scope
            )
        }
    }
}
