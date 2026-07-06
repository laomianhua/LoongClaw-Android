package com.littlehelper.shell.transport



import com.google.gson.JsonParser

import org.junit.Assert.assertEquals

import org.junit.Assert.assertFalse

import org.junit.Assert.assertTrue

import org.junit.Test

import java.net.SocketTimeoutException



class GatewayConnectErrorMapperTest {



    @Test

    fun mapGatewayError_pairingRequired() {

        val error = JsonParser.parseString(

            """{"message":"pairing required","details":{"code":"PAIRING_REQUIRED"}}"""

        ).asJsonObject

        val result = GatewayConnectErrorMapper.mapGatewayError(error, "abc123")

        assertTrue(result.pairingRequired)

        assertEquals(ConnectFailureKind.PAIRING_REQUIRED, result.kind)

        assertEquals("设备待配对", result.title)

        assertEquals("PAIRING_REQUIRED", result.gatewayCode)

        assertTrue(result.testResultMessage("abc123").contains("abc123"))

    }



    @Test

    fun mapGatewayError_metadataUpgrade_treatedAsPairing() {

        val error = JsonParser.parseString(

            """{"message":"metadata-upgrade-pending","details":{"code":"METADATA_UPGRADE"}}"""

        ).asJsonObject

        val result = GatewayConnectErrorMapper.mapGatewayError(error)

        assertTrue(result.pairingRequired)

        assertEquals(ConnectFailureKind.PAIRING_REQUIRED, result.kind)

    }



    @Test

    fun mapGatewayError_tokenMismatch() {

        val error = JsonParser.parseString(

            """{"message":"AUTH_TOKEN_MISMATCH"}"""

        ).asJsonObject

        val result = GatewayConnectErrorMapper.mapGatewayError(error)

        assertFalse(result.pairingRequired)

        assertEquals(ConnectFailureKind.BAD_SHARED_CREDENTIAL, result.kind)

        assertEquals("Token 与 Gateway 不一致", result.title)

    }



    @Test

    fun mapGatewayError_authRateLimited_notTokenMismatch() {

        val error = JsonParser.parseString(

            """{"message":"unauthorized","details":{"code":"AUTH_RATE_LIMITED","retryAfterMs":30000}}"""

        ).asJsonObject

        val result = GatewayConnectErrorMapper.mapGatewayError(error)

        assertEquals(ConnectFailureKind.RATE_LIMITED, result.kind)

        assertEquals("AUTH_RATE_LIMITED", result.gatewayCode)

        assertEquals("请求太频繁，请稍等 1–2 分钟", result.title)

        assertFalse(result.pairingRequired)

        assertTrue(result.detail.orEmpty().contains("Token 无需修改"))

        assertTrue(
            result.detail.orEmpty().contains("1–2 分钟") ||
                result.detail.orEmpty().contains("约 1 分钟")
        )

    }



    @Test

    fun mapGatewayError_authRateLimited_noUnderscoreVariant() {

        val error = JsonParser.parseString(

            """{"message":"unauthorized","details":{"code":"AUTHRATE_LIMITED"}}"""

        ).asJsonObject

        val result = GatewayConnectErrorMapper.mapGatewayError(error)

        assertEquals(ConnectFailureKind.RATE_LIMITED, result.kind)

        assertEquals("AUTHRATE_LIMITED", result.gatewayCode)

        assertFalse(result.pairingRequired)

    }



    @Test

    fun mapGatewayError_unauthorizedWithoutDeviceToken_isBadCredentialNotPairing() {

        val error = JsonParser.parseString(

            """{"message":"unauthorized"}"""

        ).asJsonObject

        val result = GatewayConnectErrorMapper.mapGatewayError(

            error = error,

            deviceId = "abc123",

            hasStoredDeviceToken = false,

        )

        assertFalse(result.pairingRequired)

        assertEquals(ConnectFailureKind.BAD_SHARED_CREDENTIAL, result.kind)

        assertEquals("Token 与 Gateway 不一致", result.title)

        assertEquals("unauthorized", result.gatewayCode)

    }



    @Test

    fun mapGatewayError_unauthorizedWithDeviceToken_isStaleBinding() {

        val error = JsonParser.parseString(

            """{"message":"unauthorized"}"""

        ).asJsonObject

        val result = GatewayConnectErrorMapper.mapGatewayError(

            error = error,

            hasStoredDeviceToken = true,

        )

        assertFalse(result.pairingRequired)

        assertEquals(ConnectFailureKind.STALE_DEVICE_BINDING, result.kind)

        assertEquals("设备配对记录已失效", result.title)

    }



    @Test

    fun mapGatewayError_deviceSignature() {

        val error = JsonParser.parseString(

            """{"message":"device-signature"}"""

        ).asJsonObject

        val result = GatewayConnectErrorMapper.mapGatewayError(error)

        assertEquals(ConnectFailureKind.DEVICE_SIGNATURE, result.kind)

        assertEquals("设备签名验证失败", result.title)

    }



    @Test

    fun mapGatewayError_unavailable() {

        val error = JsonParser.parseString(

            """{"message":"UNAVAILABLE","details":{"reason":"startup-sidecars"}}"""

        ).asJsonObject

        val result = GatewayConnectErrorMapper.mapGatewayError(error)

        assertEquals(ConnectFailureKind.GATEWAY_STARTING, result.kind)

        assertEquals("Gateway 正在启动", result.title)

    }



    @Test

    fun mapGatewayError_agentNotFound() {

        val error = JsonParser.parseString(

            """{"message":"agent not found","details":{"code":"AGENT_NOT_FOUND"}}"""

        ).asJsonObject

        val result = GatewayConnectErrorMapper.mapGatewayError(error)

        assertEquals(ConnectFailureKind.AGENT_NOT_FOUND, result.kind)

        assertEquals("该智能体不存在", result.title)

    }



    @Test

    fun mapThrowable_socketTimeout() {

        val result = GatewayConnectErrorMapper.mapThrowable(

            SocketTimeoutException("failed to connect after 10000ms")

        )

        assertEquals(ConnectFailureKind.NETWORK, result.kind)

        assertEquals("无法连接服务器（超时）", result.title)

    }



    @Test

    fun mapThrowable_failedToConnect() {

        val result = GatewayConnectErrorMapper.mapThrowable(

            RuntimeException("failed to connect to /100.112.96.116 (port 18789)")

        )

        assertEquals(ConnectFailureKind.NETWORK, result.kind)

        assertEquals("无法连接服务器", result.title)

    }

}


