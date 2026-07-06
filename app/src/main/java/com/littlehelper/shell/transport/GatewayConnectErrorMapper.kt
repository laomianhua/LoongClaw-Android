package com.littlehelper.shell.transport

import com.google.gson.JsonObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Gateway 握手/连接失败 → 结构化诊断 + 用户可读中文（主界面横幅、设置页测试握手共用）。
 */
object GatewayConnectErrorMapper {

    fun mapGatewayError(
        error: JsonObject?,
        deviceId: String? = null,
        hasStoredDeviceToken: Boolean = false,
        credentialUsed: CredentialKind = CredentialKind.SHARED,
    ): ConnectErrorPresentation {
        if (error == null) {
            return ConnectErrorPresentation(
                kind = ConnectFailureKind.UNKNOWN,
                title = TITLE_CONNECT_FAILED,
                detail = DETAIL_GENERIC_RETRY,
                userAction = ACTION_RETRY,
            )
        }

        val code = resolveErrorCode(error)
        val message = error.get("message")?.asString.orEmpty()
        val haystack = "${code.orEmpty()} $message".lowercase()
        val displayCode = code?.takeIf { it.isNotBlank() } ?: message.takeIf { it.isNotBlank() }
        val retryAfterMs = resolveRetryAfterMs(error)

        if (isRateLimited(error, haystack)) {
            return rateLimitedPresentation(displayCode, message, retryAfterMs)
        }

        if (isApprovalRequired(error)) {
            return ConnectErrorPresentation(
                kind = ConnectFailureKind.PAIRING_REQUIRED,
                title = TITLE_PAIRING_REQUIRED,
                detail = DETAIL_PAIRING_APPROVE,
                gatewayCode = displayCode,
                gatewayMessage = message.takeIf { it.isNotBlank() },
                userAction = ACTION_AFTER_PAIRING_RETRY,
            )
        }

        when {
            matchesDeviceTokenMismatch(haystack) ||
                (hasStoredDeviceToken && matchesTokenMismatch(haystack)) -> {
                return ConnectErrorPresentation(
                    kind = ConnectFailureKind.STALE_DEVICE_BINDING,
                    title = TITLE_STALE_DEVICE_BINDING,
                    detail = DETAIL_STALE_DEVICE_BINDING,
                    gatewayCode = displayCode,
                    gatewayMessage = message.takeIf { it.isNotBlank() },
                    userAction = ACTION_REVOKE_DEVICE,
                )
            }
            matchesTokenMismatch(haystack) -> return ConnectErrorPresentation(
                kind = ConnectFailureKind.BAD_SHARED_CREDENTIAL,
                title = TITLE_TOKEN_MISMATCH,
                detail = DETAIL_TOKEN_MISMATCH,
                gatewayCode = displayCode,
                gatewayMessage = message.takeIf { it.isNotBlank() },
                userAction = ACTION_FIX_TOKEN_IN_SETTINGS,
            )
            matchesPasswordMismatch(haystack) -> return ConnectErrorPresentation(
                kind = ConnectFailureKind.BAD_SHARED_CREDENTIAL,
                title = TITLE_PASSWORD_MISMATCH,
                detail = DETAIL_PASSWORD_MISMATCH,
                gatewayCode = displayCode,
                gatewayMessage = message.takeIf { it.isNotBlank() },
                userAction = ACTION_FIX_TOKEN_IN_SETTINGS,
            )
            matchesUnauthorized(haystack) && hasStoredDeviceToken -> return ConnectErrorPresentation(
                kind = ConnectFailureKind.STALE_DEVICE_BINDING,
                title = TITLE_STALE_DEVICE_BINDING,
                detail = DETAIL_STALE_DEVICE_BINDING,
                gatewayCode = displayCode ?: "unauthorized",
                gatewayMessage = message.takeIf { it.isNotBlank() },
                userAction = ACTION_REVOKE_DEVICE,
            )
            matchesUnauthorized(haystack) -> return ConnectErrorPresentation(
                kind = ConnectFailureKind.BAD_SHARED_CREDENTIAL,
                title = TITLE_TOKEN_MISMATCH,
                detail = DETAIL_UNAUTHORIZED_SHARED,
                gatewayCode = displayCode ?: "unauthorized",
                gatewayMessage = message.takeIf { it.isNotBlank() },
                userAction = ACTION_FIX_TOKEN_IN_SETTINGS,
            )
            matchesDeviceSignature(haystack) -> return ConnectErrorPresentation(
                kind = ConnectFailureKind.DEVICE_SIGNATURE,
                title = TITLE_DEVICE_SIGNATURE,
                detail = DETAIL_DEVICE_SIGNATURE,
                gatewayCode = displayCode,
                gatewayMessage = message.takeIf { it.isNotBlank() },
                userAction = ACTION_REVOKE_DEVICE,
            )
            matchesUnavailable(haystack) -> return ConnectErrorPresentation(
                kind = ConnectFailureKind.GATEWAY_STARTING,
                title = TITLE_GATEWAY_STARTING,
                detail = DETAIL_GATEWAY_STARTING,
                gatewayCode = displayCode,
                gatewayMessage = message.takeIf { it.isNotBlank() },
                userAction = ACTION_RETRY,
            )
            matchesAgentNotFound(haystack) -> return ConnectErrorPresentation(
                kind = ConnectFailureKind.AGENT_NOT_FOUND,
                title = TITLE_AGENT_NOT_FOUND,
                detail = DETAIL_AGENT_NOT_FOUND,
                gatewayCode = displayCode,
                gatewayMessage = message.takeIf { it.isNotBlank() },
                userAction = ACTION_FIX_AGENT_IN_SETTINGS,
            )
        }

        val fallback = message.takeIf { it.isNotBlank() } ?: error.toString()
        if (!code.isNullOrBlank()) {
            return ConnectErrorPresentation(
                kind = ConnectFailureKind.UNKNOWN,
                title = "连接失败（$displayCode）",
                detail = shortenTechnical(fallback),
                gatewayCode = displayCode,
                gatewayMessage = message.takeIf { it.isNotBlank() },
                userAction = ACTION_RETRY,
            )
        }
        return ConnectErrorPresentation(
            kind = ConnectFailureKind.UNKNOWN,
            title = TITLE_CONNECT_FAILED,
            detail = shortenTechnical(fallback),
            gatewayCode = displayCode,
            gatewayMessage = message.takeIf { it.isNotBlank() },
            userAction = ACTION_RETRY,
        )
    }

    fun mapThrowable(throwable: Throwable): ConnectErrorPresentation {
        if (throwable is ConnectFailedException) return throwable.presentation
        val root = generateSequence(throwable) { it.cause }.last()
        val message = root.message.orEmpty()
        val haystack = message.lowercase()

        when {
            root is SocketTimeoutException ||
                haystack.contains("timeout") ||
                haystack.contains("timed out") -> return ConnectErrorPresentation(
                kind = ConnectFailureKind.NETWORK,
                title = TITLE_NETWORK_TIMEOUT,
                detail = DETAIL_NETWORK_CHECK,
                userAction = ACTION_CHECK_NETWORK,
            )
            root is UnknownHostException ||
                haystack.contains("unable to resolve host") -> return ConnectErrorPresentation(
                kind = ConnectFailureKind.NETWORK,
                title = TITLE_NETWORK_HOST,
                detail = DETAIL_NETWORK_CHECK,
                userAction = ACTION_CHECK_NETWORK,
            )
            root is ConnectException ||
                haystack.contains("failed to connect") ||
                haystack.contains("connection refused") -> return ConnectErrorPresentation(
                kind = ConnectFailureKind.NETWORK,
                title = TITLE_NETWORK_UNREACHABLE,
                detail = DETAIL_NETWORK_CHECK,
                userAction = ACTION_CHECK_NETWORK,
            )
            root is SSLException ||
                haystack.contains("ssl") ||
                haystack.contains("certificate") -> return ConnectErrorPresentation(
                kind = ConnectFailureKind.NETWORK,
                title = TITLE_TLS_FAILED,
                detail = DETAIL_TLS_CHECK,
                userAction = ACTION_CHECK_NETWORK,
            )
            root is IOException && haystack.contains("websocket") -> return ConnectErrorPresentation(
                kind = ConnectFailureKind.NETWORK,
                title = TITLE_CONNECT_FAILED,
                detail = DETAIL_NETWORK_CHECK,
                userAction = ACTION_CHECK_NETWORK,
            )
        }

        if (message.isNotBlank()) {
            return ConnectErrorPresentation(
                kind = ConnectFailureKind.UNKNOWN,
                title = TITLE_CONNECT_FAILED,
                detail = shortenTechnical(message),
                userAction = ACTION_RETRY,
            )
        }
        return ConnectErrorPresentation(
            kind = ConnectFailureKind.UNKNOWN,
            title = TITLE_CONNECT_FAILED,
            detail = DETAIL_GENERIC_RETRY,
            userAction = ACTION_RETRY,
        )
    }

    fun isApprovalRequired(error: JsonObject?): Boolean {
        if (error == null) return false
        val message = error.get("message")?.asString.orEmpty()
        val code = resolveErrorCode(error).orEmpty()
        val haystack = "$code $message".lowercase()
        return code.contains("PAIR", ignoreCase = true) ||
            code == "NOT_PAIRED" ||
            haystack.contains("pairing required") ||
            haystack.contains("not approved") ||
            haystack.contains("not_paired") ||
            haystack.contains("metadata-upgrade") ||
            haystack.contains("scope-upgrade") ||
            haystack.contains("role-upgrade")
    }

    private fun isRateLimited(error: JsonObject, haystack: String): Boolean {
        if (matchesRateLimited(haystack)) return true
        return isRateLimitedCode(resolveErrorCode(error))
    }

    private fun isRateLimitedCode(code: String?): Boolean {
        if (code.isNullOrBlank()) return false
        val normalized = code.uppercase().replace("_", "").replace("-", "")
        return normalized == "AUTHRATELIMITED" || normalized.contains("RATELIMIT")
    }

    private fun rateLimitedPresentation(
        displayCode: String?,
        message: String,
        retryAfterMs: Long?,
    ): ConnectErrorPresentation = ConnectErrorPresentation(
        kind = ConnectFailureKind.RATE_LIMITED,
        title = TITLE_RATE_LIMITED,
        detail = formatRateLimitDetail(retryAfterMs),
        gatewayCode = displayCode ?: "AUTH_RATE_LIMITED",
        gatewayMessage = message.takeIf { it.isNotBlank() },
        userAction = ACTION_WAIT_AND_RETRY,
        retryAfterMs = retryAfterMs,
    )

    private fun resolveErrorCode(error: JsonObject): String? =
        error.getAsJsonObject("details")?.get("code")?.asString
            ?: error.get("code")?.asString

    private fun resolveRetryAfterMs(error: JsonObject): Long? {
        val details = error.getAsJsonObject("details")
        return details?.get("retryAfterMs")?.asLong
            ?: error.get("retryAfterMs")?.asLong
    }

    private fun matchesRateLimited(haystack: String): Boolean =
        haystack.contains("auth_rate_limited") ||
            haystack.contains("authrate_limited") ||
            haystack.contains("rate_limited") ||
            haystack.contains("rate limit") ||
            haystack.contains("too many requests")

    private fun matchesDeviceTokenMismatch(haystack: String): Boolean =
        haystack.contains("device_token_mismatch")

    private fun matchesTokenMismatch(haystack: String): Boolean =
        haystack.contains("auth_token_mismatch") ||
            haystack.contains("token_mismatch") ||
            haystack.contains("auth token mismatch")

    private fun matchesPasswordMismatch(haystack: String): Boolean =
        haystack.contains("auth_password_mismatch") ||
            haystack.contains("password_mismatch")

    private fun matchesUnauthorized(haystack: String): Boolean =
        haystack.contains("unauthorized") ||
            haystack.contains("auth_unauthorized") ||
            haystack.contains("auth_required")

    private fun matchesDeviceSignature(haystack: String): Boolean =
        haystack.contains("device-signature") ||
            haystack.contains("device_signature") ||
            haystack.contains("device-nonce") ||
            haystack.contains("device_nonce") ||
            haystack.contains("device_auth")

    private fun matchesUnavailable(haystack: String): Boolean =
        haystack.contains("unavailable") || haystack.contains("startup-sidecars")

    private fun matchesAgentNotFound(haystack: String): Boolean =
        haystack.contains("agent not found") ||
            haystack.contains("unknown agent") ||
            haystack.contains("agent does not exist") ||
            haystack.contains("invalid agent") ||
            haystack.contains("no such agent")

    private fun shortenTechnical(raw: String, maxLen: Int = 160): String {
        val oneLine = raw.replace('\n', ' ').trim()
        if (oneLine.length <= maxLen) return oneLine
        return oneLine.take(maxLen - 1) + "…"
    }

    private fun formatRateLimitDetail(retryAfterMs: Long?): String {
        val waitHint = when {
            retryAfterMs == null -> "1–2 分钟"
            retryAfterMs < 90_000L -> "约 1 分钟"
            retryAfterMs < 150_000L -> "1–2 分钟"
            else -> "约 ${((retryAfterMs / 1_000L) + 59) / 60} 分钟"
        }
        return "Gateway 暂时限制了连接频率（与 Token 是否正确无关）。" +
            "请稍等 ${waitHint} 后重试，勿连续点击测试握手；Token 无需修改。"
    }

    private const val TITLE_PAIRING_REQUIRED = "设备待配对"
    private const val DETAIL_PAIRING_APPROVE =
        "请在 Gateway Control UI → Devices 批准与本机设备 ID 一致的条目。"

    private const val TITLE_TOKEN_MISMATCH = "Token 与 Gateway 不一致"
    private const val DETAIL_TOKEN_MISMATCH =
        "请核对设置中的 Token 是否与 openclaw.json 里 gateway.auth.token 相同。"
    private const val DETAIL_UNAUTHORIZED_SHARED =
        "Gateway 拒绝了当前 Token（unauthorized），请核对设置中的 Token。"

    private const val TITLE_STALE_DEVICE_BINDING = "设备配对记录已失效"
    private const val DETAIL_STALE_DEVICE_BINDING =
        "请在 Control UI 撤销本设备后，返回设置保存并连接，再重新批准。"

    private const val TITLE_PASSWORD_MISMATCH = "密码与 Gateway 不一致"
    private const val DETAIL_PASSWORD_MISMATCH =
        "请核对设置中的密码是否与 openclaw.json 里 gateway.auth.password 相同。"

    private const val TITLE_DEVICE_SIGNATURE = "设备签名验证失败"
    private const val DETAIL_DEVICE_SIGNATURE =
        "请在 Control UI 撤销本设备；必要时清除 App 数据后重新连接。"

    private const val TITLE_GATEWAY_STARTING = "Gateway 正在启动"
    private const val DETAIL_GATEWAY_STARTING = "服务尚未就绪，请稍等几秒后重试。"

    private const val TITLE_RATE_LIMITED = "请求太频繁，请稍等 1–2 分钟"
    private const val TITLE_NETWORK_TIMEOUT = "无法连接服务器（超时）"
    private const val TITLE_NETWORK_HOST = "无法解析服务器地址"
    private const val TITLE_NETWORK_UNREACHABLE = "无法连接服务器"
    private const val DETAIL_NETWORK_CHECK =
        "请确认手机与 Gateway 网络互通、地址与端口正确。"

    private const val TITLE_TLS_FAILED = "安全连接失败"
    private const val DETAIL_TLS_CHECK = "若使用 wss://，请确认证书与反向代理配置正确。"

    private const val TITLE_CONNECT_FAILED = "连接失败"
    private const val DETAIL_GENERIC_RETRY = "请检查网络与 Gateway 设置后重试。"

    private const val TITLE_AGENT_NOT_FOUND = "该智能体不存在"
    private const val DETAIL_AGENT_NOT_FOUND =
        "请确认智能体名称与服务器 agents.list 配置一致，或联系管理员创建该智能体。"

    private const val ACTION_AFTER_PAIRING_RETRY = "批准后点上方重试"
    private const val ACTION_WAIT_AND_RETRY = "请等待 1–2 分钟后重试"
    private const val ACTION_FIX_TOKEN_IN_SETTINGS = "请在设置中修改 Token 后点「保存并连接」"
    private const val ACTION_FIX_AGENT_IN_SETTINGS = "请在设置中修改智能体名称后重试"
    private const val ACTION_REVOKE_DEVICE = "请在 Control UI 撤销本设备后重试"
    private const val ACTION_RETRY = "点上方重试"
    private const val ACTION_CHECK_NETWORK = "检查网络与地址后重试"
}

class ConnectFailedException(
    val presentation: ConnectErrorPresentation,
    deviceId: String? = null,
) : IllegalStateException(
    presentation.testResultMessage(deviceId)
)
