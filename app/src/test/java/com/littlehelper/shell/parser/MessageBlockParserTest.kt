package com.littlehelper.shell.parser

import com.littlehelper.shell.demo.HoldingsModalDemo
import com.littlehelper.shell.modal.ModalAction
import com.littlehelper.shell.transport.GatewayModalAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.google.gson.JsonParser

class MessageBlockParserTest {

    @Test
    fun parse_holdingsDemo_extractsChatAndModal() {
        val result = MessageBlockParser.parse(HoldingsModalDemo.AGENT_RESPONSE)

        assertTrue(result.chatText.contains("196.8 万"))
        assertEquals(ModalAction.OPEN, result.modalAction)
        assertEquals(2, result.modalBlocks.size)
        assertEquals("holdings-table", result.modalBlocks[0].id)
        assertEquals("table", result.modalBlocks[0].type)
        assertEquals("daily-chart", result.modalBlocks[1].id)
        assertEquals("chart/line", result.modalBlocks[1].type)
        assertNull(result.modalParseError)
    }

    @Test
    fun parse_plainText_returnsWholeStringAsChat() {
        val result = MessageBlockParser.parse("你好，今天天气不错。")
        assertEquals("你好，今天天气不错。", result.chatText)
        assertNull(result.modalAction)
    }

    @Test
    fun chatDisplayText_stripsEndMarkerAndModalJson() {
        val raw = """
===CHAT===
今日持仓如下。

===MODAL===
{"action":"open","blocks":[{"id":"t1","type":"table","data":{}}]}
===END===
        """.trimIndent()
        val display = MessageBlockParser.chatDisplayText(raw)
        assertEquals("今日持仓如下。", display)
        assertTrue(display.contains("===MODAL===").not())
        assertTrue(display.contains("===END===").not())
        assertTrue(display.contains("\"action\"").not())
    }

    @Test
    fun chatDisplayText_bareJsonModal_doesNotLeakJsonIntoChat() {
        val raw = """
好的，请看白板。

{"action":"open","blocks":[{"id":"b1","type":"table","data":{}}]}
        """.trimIndent()
        val display = MessageBlockParser.chatDisplayText(raw)
        assertTrue(display.contains("白板"))
        assertTrue(display.contains("\"action\"").not())
    }

    @Test
    fun chatDisplayText_modalOnly_returnsEmptyNotWire() {
        val raw = """
===MODAL===
{"action":"open","blocks":[{"id":"b1","type":"table","data":{}}]}
===END===
        """.trimIndent()
        assertEquals("", MessageBlockParser.chatDisplayText(raw))
    }

    @Test
    fun parse_invalidModalJson_setsWarningPath() {
        val raw = """
===CHAT===
仅聊天

===MODAL===
{ broken json
===END===
        """.trimIndent()
        val result = MessageBlockParser.parse(raw)
        assertEquals("仅聊天", result.chatText)
        assertNull(result.modalAction)
        assertTrue(result.modalParseError != null)
    }

    @Test
    fun streamingParser_waitsForEndBeforeFinalize() {
        val parser = StreamingMessageBlockParser()
        parser.append(HoldingsModalDemo.AGENT_RESPONSE.substring(0, 80), appendDelta = false)
        assertNull(parser.tryParseIfComplete())

        parser.append(HoldingsModalDemo.AGENT_RESPONSE.substring(80), appendDelta = true)
        val result = parser.finalize()
        requireNotNull(result)
        assertEquals(ModalAction.OPEN, result.modalAction)
        assertEquals(2, result.modalBlocks.size)
    }

    @Test
    fun parse_bareJsonModal_withoutMarkers() {
        val raw = """
好的，以下是今天的持仓情况。

{"action":"open","blocks":[{"id":"t1","type":"table","data":{}}]}
        """.trimIndent()
        val result = MessageBlockParser.parse(raw)
        assertEquals(ModalAction.OPEN, result.modalAction)
        assertEquals(1, result.modalBlocks.size)
        assertTrue(result.chatText.contains("持仓"))
    }

    @Test
    fun parse_chatMarkerWithBareJsonModal() {
        val raw = """
===CHAT===
今天走势如下。

{"action":"open","blocks":[{"id":"c1","type":"chart/line","data":{}}]}
        """.trimIndent()
        val result = MessageBlockParser.parse(raw)
        assertEquals(ModalAction.OPEN, result.modalAction)
        assertEquals("今天走势如下。", result.chatText)
    }

    @Test
    fun chatDisplayText_junkBeforeModalMarker_doesNotLeakIntoChat() {
        val raw = """
| 基金 | 金额 |
| 纳指 | 35万 |

===MODAL===
{"action":"open","blocks":[{"id":"t1","type":"table","data":{}}]}
===END===
        """.trimIndent()
        assertEquals("", MessageBlockParser.chatDisplayText(raw))
    }

    @Test
    fun looksLikeStructuredContentDump_detectsMarkdownTable() {
        val table = """
| 基金 | 金额 |
|------|------|
| 纳指 | 35万 |
        """.trimIndent()
        assertTrue(MessageBlockParser.looksLikeStructuredContentDump(table))
    }

    @Test
    fun gatewayAdapter_extractsModalFromPayloadField() {
        val payload = com.google.gson.JsonParser.parseString(
            """
            {
              "messageId":"m1",
              "message":{
                "role":"assistant",
                "content":[{"type":"text","text":"请看白板"}]
              },
              "modal":{"action":"open","blocks":[{"id":"b1","type":"table","data":{}}]}
            }
            """.trimIndent()
        ).asJsonObject
        val envelope = payload.getAsJsonObject("message")
        val wire = GatewayModalAdapter.normalizeAssistantMessage(payload, envelope)
        val parsed = MessageBlockParser.parse(wire.wireText)
        assertEquals(ModalAction.OPEN, parsed.modalAction)
        assertEquals("请看白板", parsed.chatText)
    }
}
