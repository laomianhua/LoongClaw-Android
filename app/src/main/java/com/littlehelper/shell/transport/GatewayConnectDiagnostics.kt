package com.littlehelper.shell.transport

/**
 * Gateway 连接失败分类（主连接与设置页测试握手共用）。
 * UI 只根据 [ConnectFailureKind] 选文案，不自行猜测 Gateway 语义。
 */
enum class ConnectFailureKind {
    /** 共享 Token / 密码与 Gateway 不一致，或 unauthorized 且无 deviceToken */
    BAD_SHARED_CREDENTIAL,
    /** 本地 deviceToken 与 Gateway 当前绑定不一致（含 AUTH_TOKEN_MISMATCH + 已配对） */
    STALE_DEVICE_BINDING,
    /** Gateway 明确返回待批准 / pairing */
    PAIRING_REQUIRED,
    /** 认证尝试过于频繁（AUTH_RATE_LIMITED） */
    RATE_LIMITED,
    DEVICE_SIGNATURE,
    GATEWAY_STARTING,
    NETWORK,
    /** agents.list 中不存在该 agent id（chat.send 阶段） */
    AGENT_NOT_FOUND,
    UNKNOWN,
}

enum class CredentialKind {
    SHARED,
    DEVICE,
    NONE,
}

data class ConnectErrorPresentation(
    val kind: ConnectFailureKind,
    val title: String,
    val detail: String? = null,
    val gatewayCode: String? = null,
    val gatewayMessage: String? = null,
    val userAction: String? = null,
    val retryAfterMs: Long? = null,
) {
    val pairingRequired: Boolean
        get() = kind == ConnectFailureKind.PAIRING_REQUIRED

    fun toSessionError(turnId: String? = null): com.littlehelper.shell.model.ClawSessionEvent.SessionError =
        com.littlehelper.shell.model.ClawSessionEvent.SessionError(
            message = title,
            detail = detail,
            turnId = turnId,
            pairingRequired = pairingRequired,
            failureKind = kind,
            gatewayCode = gatewayCode,
            userAction = userAction,
        )

    fun bannerSubtitle(deviceId: String? = null): String = buildString {
        gatewayCode?.takeIf { it.isNotBlank() }?.let { code ->
            append("错误码：$code")
        }
        detail?.takeIf { it.isNotBlank() }?.let { d ->
            if (isNotEmpty()) append('\n')
            append(d)
        }
        if (pairingRequired) {
            deviceId?.takeIf { it.isNotBlank() }?.let { id ->
                append('\n')
                append("设备 ID：$id")
            }
        }
    }

    fun testResultMessage(deviceId: String? = null): String = buildString {
        append(title)
        gatewayCode?.takeIf { it.isNotBlank() }?.let {
            append('\n')
            append("错误码：$it")
        }
        detail?.takeIf { it.isNotBlank() }?.let {
            append('\n')
            append(it)
        }
        if (pairingRequired) {
            deviceId?.takeIf { it.isNotBlank() }?.let {
                append('\n')
                append("设备 ID：$it")
            }
        }
    }
}
