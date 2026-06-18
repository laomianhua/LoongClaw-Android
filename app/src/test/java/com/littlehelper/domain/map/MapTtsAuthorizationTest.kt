package com.littlehelper.domain.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTtsAuthorizationTest {

    @Test
    fun isSdkDynamicTtsAuthorized_falseForNormalAiReply() {
        assertFalse(
            MapTtsAuthorization.isSdkDynamicTtsAuthorized(
                "明天中午从天通苑开车去天安门大约需要 1 小时 10 分钟。"
            )
        )
    }

    @Test
    fun isSdkDynamicTtsAuthorized_trueWhenPlaceholderPresent() {
        assertTrue(
            MapTtsAuthorization.isSdkDynamicTtsAuthorized(
                "正在为您计算路线，[CALCULATING]"
            )
        )
    }

    @Test
    fun mergeSdkResult_replacesPlaceholder() {
        val merged = MapTtsAuthorization.mergeSdkResult(
            "开车去天安门大约需要 [CALCULATING]。",
            "24 分钟"
        )
        assertEquals("开车去天安门大约需要 24 分钟。", merged)
    }
}
