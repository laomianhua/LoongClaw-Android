package com.littlehelper.settings

import com.littlehelper.shell.transport.ConnectFailureKind

/** 设置页「测试握手」结果（与主连接态分离）。 */
data class GatewayHandshakeTestResult(
    val success: Boolean,
    val title: String,
    val gatewayCode: String? = null,
    val detail: String? = null,
    val kind: ConnectFailureKind? = null,
    val deviceId: String? = null,
) {
    val pairingRequired: Boolean
        get() = kind == ConnectFailureKind.PAIRING_REQUIRED

    val displayMessage: String
        get() = buildString {
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
