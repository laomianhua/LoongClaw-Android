package com.littlehelper.network

import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekServiceTest {

    @Test
    fun secretarySystemPrompt_containsRequiredSections() {
        val prompt = DeepSeekService.SECRETARY_SYSTEM_PROMPT
        assertTrue(prompt.contains("【强制 JSON 格式规则】"))
        assertTrue(prompt.contains("\"status\": \"success\""))
        assertTrue(prompt.contains("\"status\": \"ignore\""))
        assertTrue(prompt.contains("text_too_vague_or_no_intent"))
        assertTrue(prompt.contains("没听懂您想记什么"))
        assertTrue(prompt.contains("随身语音记事秘书"))
        assertTrue(prompt.contains("___DB_OPS_START___"))
        assertTrue(prompt.contains("___DB_OPS_END___"))
        assertTrue(prompt.contains("\"op\": \"insert\""))
        assertTrue(prompt.contains("formatted_date_for_alarm"))
        assertTrue(prompt.contains("clear_all"))
        assertTrue(prompt.contains("相对时间词"))
        assertTrue(prompt.contains("同音不同字"))
        assertTrue(prompt.contains("王纲"))
        assertTrue(prompt.contains("首轮直接写入"))
        assertTrue(prompt.contains("多事件拆分示例"))
        assertTrue(prompt.contains("后天上午去商场下午去医院"))
        assertTrue(prompt.contains("2026-06-16"))
        assertTrue(prompt.contains("仅以下三种情况才允许追问"))
        assertTrue(prompt.contains("相对时刻提醒"))
        assertTrue(prompt.contains("event_time"))
        assertTrue(prompt.contains("【category 必填规则 – 铁律】"))
        assertTrue(prompt.contains("车停在三里屯地下 B2"))
        assertTrue(prompt.contains("parking"))
    }
}
