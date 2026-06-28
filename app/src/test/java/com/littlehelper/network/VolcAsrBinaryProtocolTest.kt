package com.littlehelper.network

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolcAsrBinaryProtocolTest {

    @Test
    fun buildFullClientRequest_startsWithProtocolHeader() {
        val frame = VolcAsrBinaryProtocol.buildFullClientRequest(
            appId = "8480355746",
            token = "test-token",
            cluster = "volcengine_streaming_common",
            reqId = "req-1",
            uid = "littlehelper"
        )
        assertTrue(frame.size > 12)
        assertEquals(0x11.toByte(), frame[0])
        assertEquals(0x11.toByte(), frame[1])
        assertEquals(0x11.toByte(), frame[2])
    }

    @Test
    fun buildV3FullClientRequest_startsWithProtocolHeader() {
        val frame = VolcAsrBinaryProtocol.buildV3FullClientRequest(uid = "littlehelper")
        assertTrue(frame.size > 12)
        assertEquals(0x11.toByte(), frame[0])
        assertEquals(0x11.toByte(), frame[1])
        assertEquals(0x11.toByte(), frame[2])
    }

    @Test
    fun isSuccessCode_acceptsV2AndV3Codes() {
        assertTrue(VolcAsrBinaryProtocol.isSuccessCode(1000))
        assertTrue(VolcAsrBinaryProtocol.isSuccessCode(20_000_000))
        assertTrue(VolcAsrBinaryProtocol.isSuccessCode(null))
    }

    @Test
    fun buildAudioRequest_lastPacketUsesNegativeSequenceFlag() {
        val frame = VolcAsrBinaryProtocol.buildAudioRequest(
            pcm = byteArrayOf(1, 2, 3, 4),
            sequence = 5,
            isLast = true
        )
        val flags = frame[1].toInt() and 0x0F
        assertEquals(VolcAsrBinaryProtocol.FLAG_NEG_WITH_SEQUENCE, flags)
    }

    @Test
    fun extractText_readsV2ArrayResult() {
        val payload = JsonObject().apply {
            add("result", com.google.gson.JsonArray().apply {
                add(JsonObject().apply { addProperty("text", "你好世界") })
            })
        }
        assertEquals("你好世界", VolcAsrBinaryProtocol.extractText(payload))
    }

    @Test
    fun extractText_readsV3ObjectResultAndUtterances() {
        val payload = JsonObject().apply {
            add("result", JsonObject().apply {
                addProperty("text", "整句结果")
                add("utterances", com.google.gson.JsonArray().apply {
                    add(JsonObject().apply { addProperty("text", "分句一") })
                    add(JsonObject().apply { addProperty("text", "分句二") })
                })
            })
        }
        assertEquals("整句结果", VolcAsrBinaryProtocol.extractText(payload))
    }

    @Test
    fun extractText_fallsBackToUtterancesWhenTopTextMissing() {
        val payload = JsonObject().apply {
            add("result", JsonObject().apply {
                add("utterances", com.google.gson.JsonArray().apply {
                    add(JsonObject().apply { addProperty("text", "今天") })
                })
            })
        }
        assertEquals("今天", VolcAsrBinaryProtocol.extractText(payload))
    }

    @Test
    fun extractText_prefersDefiniteUtterancesForNonstreamPass() {
        val payload = JsonObject().apply {
            add("result", JsonObject().apply {
                addProperty("text", "流式猜测结果")
                add("utterances", com.google.gson.JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("text", "流式猜测结果")
                        addProperty("definite", false)
                    })
                    add(JsonObject().apply {
                        addProperty("text", "复核后的准确结果")
                        addProperty("definite", true)
                    })
                })
            })
        }
        assertEquals("复核后的准确结果", VolcAsrBinaryProtocol.extractText(payload))
    }

    @Test
    fun mergeStreamingText_prefersLongerPrefixSnapshot() {
        assertEquals(
            "今天天气",
            VolcAsrBinaryProtocol.mergeStreamingText("今天", "今天天气")
        )
        assertEquals(
            "今天天气",
            VolcAsrBinaryProtocol.mergeStreamingText("今天天气", "今天")
        )
    }
}
