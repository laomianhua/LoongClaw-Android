package com.littlehelper.shell.transport

import com.littlehelper.settings.GatewayConnectionSettings

/**
 * 设置页连接测试入口。
 */
object GatewayConnectionTester {

    suspend fun testWebSocketHandshake(
        settings: GatewayConnectionSettings,
        identityStore: OpenClawDeviceIdentityStore,
    ): Result<String> {
        val config = settings.toGatewayConfig()
        return GatewayHandshakeProbe.testWebSocketHandshake(
            config = config,
            identityStore = identityStore,
            useSharedCredentialOnly = true,
        )
    }
}
