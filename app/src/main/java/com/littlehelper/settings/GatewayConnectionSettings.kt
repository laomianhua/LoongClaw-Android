package com.littlehelper.settings

import com.littlehelper.shell.transport.GatewayConfig

data class GatewayConnectionSettings(
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    val authMode: GatewayAuthMode = GatewayAuthMode.TOKEN,
    val plainToken: String = "",
    val plainPassword: String = "",
    val agentName: String = SessionKeyResolver.DEFAULT_AGENT_NAME,
) {
    val isConfigured: Boolean
        get() = host.trim().isNotEmpty() && port in 1..65535

    val isValidAgentName: Boolean
        get() = SessionKeyResolver.isValidAgentName(agentName)

    fun hasSameConnectionParamsAs(other: GatewayConnectionSettings): Boolean =
        host.trim() == other.host.trim() &&
            port == other.port &&
            authMode == other.authMode &&
            plainToken.trim() == other.plainToken.trim() &&
            plainPassword == other.plainPassword &&
            SessionKeyResolver.normalizeAgentName(agentName) ==
            SessionKeyResolver.normalizeAgentName(other.agentName)

    fun hasSameAuthCredentialsAs(other: GatewayConnectionSettings): Boolean =
        authMode == other.authMode &&
            plainToken.trim() == other.plainToken.trim() &&
            plainPassword == other.plainPassword

    fun toGatewayConfig(): GatewayConfig = GatewayConfig(
        host = host.trim(),
        port = port,
        gatewayToken = if (authMode == GatewayAuthMode.TOKEN) plainToken.trim() else "",
        password = if (authMode == GatewayAuthMode.PASSWORD) plainPassword else "",
        authMode = authMode,
        mainSessionKey = AgentSessionPolicy.productSessionKey(),
    )

    /** 首次填写设置页时的 UI 默认值（不触发连接、不含服务器地址/凭据）。 */
    fun withFormDefaults(): GatewayConnectionSettings = copy(
        port = port.takeIf { it in 1..65535 } ?: DEFAULT_PORT,
        agentName = AgentSessionPolicy.PRODUCT_AGENT_NAME,
    )

    companion object {
        const val DEFAULT_PORT = 18789

        fun formDefaults(): GatewayConnectionSettings = GatewayConnectionSettings(
            host = "",
            port = DEFAULT_PORT,
            authMode = GatewayAuthMode.TOKEN,
            plainToken = "",
            plainPassword = "",
            agentName = SessionKeyResolver.DEFAULT_AGENT_NAME,
        )

        fun fromGatewayConfig(config: GatewayConfig): GatewayConnectionSettings =
            GatewayConnectionSettings(
                host = config.host,
                port = config.port,
                authMode = config.authMode,
                agentName = SessionKeyResolver.parseAgentNameFromSessionKey(config.mainSessionKey),
                plainToken = if (config.authMode == GatewayAuthMode.TOKEN) {
                    config.gatewayToken
                } else {
                    ""
                },
                plainPassword = if (config.authMode == GatewayAuthMode.PASSWORD) {
                    config.password
                } else {
                    ""
                },
            )
    }
}
