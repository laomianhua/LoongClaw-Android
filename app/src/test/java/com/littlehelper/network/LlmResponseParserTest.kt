package com.littlehelper.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmResponseParserTest {

    @Test
    fun parse_replyOnlyWhenNoSaveBlock() {
        val result = LlmResponseParser.parse("您想记什么呢？请再说具体一点。")
        assertEquals("您想记什么呢？请再说具体一点。", result.reply)
        assertNull(result.savePayload)
        assertNull(result.deletePayload)
        assertNull(result.dbOpsPayload)
    }

    @Test
    fun parse_extractsReplyAndSavePayload() {
        val content = """
            好的，已经为您听懂并修正了内容。我帮您记下夏子杭的生日是6月8号。
            ___SAVE_START___
            {
              "summary": "夏子杭的生日是6月8号",
              "raw_text": "夏子杭的生日是6月8号",
              "tags": ["夏子杭", "生日"],
              "has_todo": false
            }
            ___SAVE_END___
        """.trimIndent()

        val result = LlmResponseParser.parse(content)
        assertEquals("好的，已经为您听懂并修正了内容。我帮您记下夏子杭的生日是6月8号。", result.reply)
        assertNotNull(result.savePayload)
        assertEquals("夏子杭的生日是6月8号", result.savePayload?.summary)
        assertEquals("夏子杭的生日是6月8号", result.savePayload?.rawText)
        assertEquals(listOf("夏子杭", "生日"), result.savePayload?.tags)
        assertEquals(false, result.savePayload?.hasTodo)
        assertNull(result.deletePayload)
    }

    @Test
    fun parse_extractsReplyAndDeletePayload() {
        val content = """
            好的，我帮您删掉误记的「夏子涵」生日信息。
            ___DELETE_START___
            {
              "target": "夏子涵的生日",
              "tags": ["夏子涵", "生日"],
              "reason": "听写错误"
            }
            ___DELETE_END___
        """.trimIndent()

        val result = LlmResponseParser.parse(content)
        assertEquals("好的，我帮您删掉误记的「夏子涵」生日信息。", result.reply)
        assertNull(result.savePayload)
        assertNotNull(result.deletePayload)
        assertEquals("夏子涵的生日", result.deletePayload?.target)
        assertEquals(listOf("夏子涵", "生日"), result.deletePayload?.tags)
    }

    @Test
    fun parse_returnsReplyWhenSaveEndMissing() {
        val content = "好的，记下了。\n___SAVE_START___\n{\"summary\":\"test\"}"
        val result = LlmResponseParser.parse(content)
        assertEquals("好的，记下了。", result.reply)
        assertNull(result.savePayload)
        assertNull(result.deletePayload)
        assertNull(result.dbOpsPayload)
    }

    @Test
    fun parse_extractsDbOpsPayload() {
        val content = """
            好的，我帮您记下王纲的生日。
            ___DB_OPS_START___
            {
              "status": "success",
              "operations": [
                {
                  "op": "insert",
                  "record": {
                    "person": "王纲",
                    "person_pinyin": "wanggang",
                    "category": "birthday",
                    "event_date": "1月1日",
                    "summary": "王纲的生日是1月1日"
                  }
                }
              ]
            }
            ___DB_OPS_END___
        """.trimIndent()

        val result = LlmResponseParser.parse(content)
        assertEquals("好的，我帮您记下王纲的生日。", result.reply)
        assertNotNull(result.dbOpsPayload)
        assertEquals("success", result.dbOpsPayload?.status)
        assertEquals(1, result.dbOpsPayload?.operations?.size)
        assertEquals("insert", result.dbOpsPayload?.operations?.first()?.op)
        assertEquals("王纲", result.dbOpsPayload?.operations?.first()?.record?.person)
    }

    @Test
    fun parse_backwardCompat_defaultsStatusSuccessWhenMissing() {
        val content = """
            记下了。
            ___DB_OPS_START___
            {
              "operations": [
                {"op": "insert", "record": {"summary": "test"}}
              ]
            }
            ___DB_OPS_END___
        """.trimIndent()

        val result = LlmResponseParser.parse(content)
        assertNotNull(result.dbOpsPayload)
        assertEquals("success", result.dbOpsPayload?.status)
        assertEquals(1, result.dbOpsPayload?.operations?.size)
    }

    @Test
    fun parse_ignoreStatus_returnsEmptyOpsWithReason() {
        val content = """
            没听懂您想记什么，请您再清楚地说一遍好吗？
            ___DB_OPS_START___
            {
              "status": "ignore",
              "reason": "text_too_vague_or_no_intent",
              "operations": []
            }
            ___DB_OPS_END___
        """.trimIndent()

        val result = LlmResponseParser.parse(content)
        assertEquals("没听懂您想记什么，请您再清楚地说一遍好吗？", result.reply)
        assertNotNull(result.dbOpsPayload)
        assertEquals("ignore", result.dbOpsPayload?.status)
        assertEquals("text_too_vague_or_no_intent", result.dbOpsPayload?.reason)
        assertTrue(result.dbOpsPayload?.operations?.isEmpty() == true)
    }

    @Test
    fun parse_ignoreStatus_defaultsReasonWhenMissing() {
        val content = """
            没听懂您想记什么，请您再清楚地说一遍好吗？
            ___DB_OPS_START___
            {"status": "ignore", "operations": []}
            ___DB_OPS_END___
        """.trimIndent()

        val result = LlmResponseParser.parse(content)
        assertNotNull(result.dbOpsPayload)
        assertEquals("ignore", result.dbOpsPayload?.status)
        assertEquals("text_too_vague_or_no_intent", result.dbOpsPayload?.reason)
        assertTrue(result.dbOpsPayload?.operations?.isEmpty() == true)
    }
}
