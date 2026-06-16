package com.littlehelper.network

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SystemTimeContextTest {

    @Test
    fun buildTimeBaselinePrefix_containsFixedDateAndExampleIso() {
        val fixed = LocalDateTime.of(2026, 6, 14, 10, 30)
        val prefix = SystemTimeContext.buildTimeBaselinePrefix(fixed)
        assertTrue(prefix.contains("【重要时间基准】"))
        assertTrue(prefix.contains("2026年6月14日"))
        assertTrue(prefix.contains("10:30"))
        assertTrue(prefix.contains("星期"))
        assertTrue(prefix.contains("今天"))
        assertTrue(prefix.contains("后天"))
        assertTrue(prefix.contains("2026-06-16"))
        assertTrue(prefix.contains("event_time"))
        assertTrue(prefix.contains("几点"))
        assertTrue(prefix.contains("formatted_date_for_alarm"))
    }

    @Test
    fun prependToSystemPrompt_putsBaselineFirst() {
        val fixed = LocalDateTime.of(2026, 1, 1, 8, 0)
        val combined = SystemTimeContext.prependToSystemPrompt("基础提示词", fixed)
        assertTrue(combined.startsWith("【重要时间基准】"))
        assertTrue(combined.contains("基础提示词"))
    }
}
