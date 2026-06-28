package com.littlehelper.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 火山引擎流式语音识别 v2 WebSocket 二进制协议。
 * 文档：https://www.volcengine.com/docs/6561/80818
 */
internal object VolcAsrBinaryProtocol {

    private const val PROTOCOL_VERSION = 0b0001
    private const val HEADER_SIZE = 0b0001

    const val MSG_FULL_CLIENT_REQUEST = 0b0001
    const val MSG_AUDIO_ONLY_REQUEST = 0b0010
    const val MSG_SERVER_FULL_RESPONSE = 0b1001
    const val MSG_SERVER_ERROR = 0b1111

    const val FLAG_NO_SEQUENCE = 0b0000
    const val FLAG_POS_SEQUENCE = 0b0001
    const val FLAG_NEG_SEQUENCE = 0b0010
    const val FLAG_NEG_WITH_SEQUENCE = 0b0011
    /** 服务端事件字段（4 字节），跳过后再读 payload。 */
    const val FLAG_EVENT = 0b0100

    const val SERIAL_JSON = 0b0001
    const val SERIAL_NONE = 0b0000
    const val COMPRESS_GZIP = 0b0001
    const val COMPRESS_NONE = 0b0000

    private val gson = Gson()

    fun buildFullClientRequest(
        appId: String,
        token: String,
        cluster: String,
        reqId: String,
        uid: String,
        sequence: Int = 1
    ): ByteArray {
        val payload = JsonObject().apply {
            add("app", JsonObject().apply {
                addProperty("appid", appId)
                addProperty("token", token)
                addProperty("cluster", cluster)
            })
            add("user", JsonObject().apply {
                addProperty("uid", uid)
            })
            add("audio", JsonObject().apply {
                addProperty("format", "pcm")
                addProperty("codec", "raw")
                addProperty("rate", 16000)
                addProperty("bits", 16)
                addProperty("channel", 1)
                addProperty("language", "zh-CN")
            })
            add("request", JsonObject().apply {
                addProperty("reqid", reqId)
                addProperty("workflow", "audio_in,resample,partition,vad,fe,decode,itn")
                addProperty("sequence", sequence)
                addProperty("nbest", 1)
                addProperty("show_utterances", true)
            })
        }
        val compressed = gzip(payload.toString().toByteArray(Charsets.UTF_8))
        return frame(
            messageType = MSG_FULL_CLIENT_REQUEST,
            flags = FLAG_POS_SEQUENCE,
            serialization = SERIAL_JSON,
            compression = COMPRESS_GZIP,
            sequence = sequence,
            payload = compressed
        )
    }

    /** 大模型流式 v3：鉴权在 Header，payload 用 model_name，不用 cluster。 */
    fun buildV3FullClientRequest(
        uid: String,
        sequence: Int = 1
    ): ByteArray {
        val payload = JsonObject().apply {
            add("user", JsonObject().apply {
                addProperty("uid", uid)
            })
            add("audio", JsonObject().apply {
                addProperty("format", "pcm")
                addProperty("codec", "raw")
                addProperty("rate", 16000)
                addProperty("bits", 16)
                addProperty("channel", 1)
            })
            add("request", JsonObject().apply {
                addProperty("model_name", "bigmodel")
                addProperty("enable_nonstream", true)
                addProperty("enable_itn", true)
                addProperty("enable_punc", true)
                addProperty("show_utterances", true)
                addProperty("result_type", "full")
            })
        }
        val compressed = gzip(payload.toString().toByteArray(Charsets.UTF_8))
        return frame(
            messageType = MSG_FULL_CLIENT_REQUEST,
            flags = FLAG_POS_SEQUENCE,
            serialization = SERIAL_JSON,
            compression = COMPRESS_GZIP,
            sequence = sequence,
            payload = compressed
        )
    }

    internal fun isSuccessCode(code: Int?): Boolean =
        code == null || code == 1000 || code == 20_000_000

    fun buildAudioRequest(
        pcm: ByteArray,
        sequence: Int,
        isLast: Boolean
    ): ByteArray {
        val compressed = gzip(pcm)
        val flags = when {
            isLast -> FLAG_NEG_WITH_SEQUENCE
            else -> FLAG_POS_SEQUENCE
        }
        val seq = if (isLast) -sequence else sequence
        return frame(
            messageType = MSG_AUDIO_ONLY_REQUEST,
            flags = flags,
            serialization = SERIAL_NONE,
            compression = COMPRESS_GZIP,
            sequence = seq,
            payload = compressed
        )
    }

    fun parseServerMessage(data: ByteArray): VolcAsrServerMessage {
        if (data.size < 4) {
            throw IllegalArgumentException("ASR 响应过短")
        }
        val headerWords = data[0].toInt() and 0x0F
        val headerBytes = headerWords * 4
        if (data.size < headerBytes) {
            throw IllegalArgumentException("ASR 响应头不完整")
        }

        val messageType = (data[1].toInt() ushr 4) and 0x0F
        val flags = data[1].toInt() and 0x0F
        val serialization = (data[2].toInt() ushr 4) and 0x0F
        val compression = data[2].toInt() and 0x0F

        var payload = data.copyOfRange(headerBytes, data.size)
        var isLastPackage = false

        // Sequence number is present iff bit-0 (POS_SEQUENCE) is set.
        // FLAG_NEG_SEQUENCE (0b0010) means last-packet only, no sequence number.
        if (flags and FLAG_POS_SEQUENCE != 0) {
            if (payload.size < 4) return VolcAsrServerMessage(messageType, isLastPackage, null, null)
            payload = payload.copyOfRange(4, payload.size)
        }
        // Last packet when bit-1 (NEG) is set — covers both 0b0010 and 0b0011.
        if (flags and FLAG_NEG_SEQUENCE != 0) {
            isLastPackage = true
        }
        if (flags and FLAG_EVENT != 0) {
            if (payload.size < 4) {
                return VolcAsrServerMessage(messageType, isLastPackage, null, null)
            }
            payload = payload.copyOfRange(4, payload.size)
        }

        val payloadJson: JsonObject? = when (messageType) {
            MSG_SERVER_FULL_RESPONSE -> {
                if (payload.size < 4) return VolcAsrServerMessage(messageType, isLastPackage, null, null)
                val size = readInt32(payload, 0)
                var body = payload.copyOfRange(4, minOf(payload.size, 4 + size))
                if (compression == COMPRESS_GZIP) body = gunzip(body)
                if (serialization == SERIAL_JSON) {
                    gson.fromJson(body.toString(Charsets.UTF_8), JsonObject::class.java)
                } else {
                    null
                }
            }
            MSG_SERVER_ERROR -> {
                if (payload.size < 8) return VolcAsrServerMessage(messageType, true, null, "ASR 服务端错误")
                val code = readInt32(payload, 0)
                val size = readInt32(payload, 4)
                var body = payload.copyOfRange(8, minOf(payload.size, 8 + size))
                if (compression == COMPRESS_GZIP) body = gunzip(body)
                val msg = body.toString(Charsets.UTF_8)
                throw IllegalStateException("ASR 错误(code=$code): $msg")
            }
            else -> null
        }

        return VolcAsrServerMessage(messageType, isLastPackage, payloadJson, null)
    }

    fun extractText(payload: JsonObject?): String {
        if (payload == null) return ""
        payload.get("text")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val resultElement = payload.get("result") ?: return ""
        return when {
            resultElement.isJsonObject -> extractFromResultObject(resultElement.asJsonObject)
            resultElement.isJsonArray -> {
                resultElement.asJsonArray.mapNotNull { element ->
                    if (element.isJsonObject) extractFromResultObject(element.asJsonObject) else null
                }.filter { it.isNotBlank() }.joinToString(separator = "").trim()
            }
            else -> ""
        }
    }

    /** v2 返回 result[]，v3/大模型返回 result{}；partial 可能在 utterances/words 里。 */
    internal fun extractFromResultObject(obj: JsonObject): String {
        obj.getAsJsonArray("utterances")?.let { utterances ->
            val definiteText = utterances.mapNotNull { element ->
                element.takeIf { it.isJsonObject }?.asJsonObject?.let { utt ->
                    if (utt.get("definite")?.asBoolean == true) {
                        utt.get("text")?.asString?.trim()?.takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }
                }
            }.joinToString(separator = "")
            if (definiteText.isNotBlank()) return definiteText
        }

        obj.get("text")?.asString?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        val utterances = obj.getAsJsonArray("utterances") ?: return ""
        val utteranceText = utterances.mapNotNull { element ->
            element.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("text")
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }.joinToString(separator = "")
        if (utteranceText.isNotBlank()) return utteranceText

        val lastUtterance = utterances.lastOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return ""
        val words = lastUtterance.getAsJsonArray("words") ?: return ""
        return words.mapNotNull { word ->
            word.takeIf { it.isJsonObject }?.asJsonObject?.get("text")?.asString
        }.joinToString(separator = "").trim()
    }

    internal fun mergeStreamingText(current: String, incoming: String): String {
        val cur = current.trim()
        val inc = incoming.trim()
        if (cur.isEmpty()) return inc
        if (inc.isEmpty()) return cur
        if (inc.length >= cur.length && (inc.startsWith(cur) || cur.startsWith(inc))) return inc
        if (cur.length > inc.length && cur.startsWith(inc)) return cur
        return inc
    }

    private fun frame(
        messageType: Int,
        flags: Int,
        serialization: Int,
        compression: Int,
        sequence: Int?,
        payload: ByteArray
    ): ByteArray {
        val header = byteArrayOf(
            ((PROTOCOL_VERSION shl 4) or HEADER_SIZE).toByte(),
            ((messageType shl 4) or flags).toByte(),
            ((serialization shl 4) or compression).toByte(),
            0x00
        )
        val out = ByteArrayOutputStream(header.size + 4 + 4 + payload.size)
        out.write(header)
        if (sequence != null) {
            out.write(int32(sequence))
        }
        out.write(int32(payload.size))
        out.write(payload)
        return out.toByteArray()
    }

    private fun int32(value: Int): ByteArray =
        ByteBuffer.allocate(4).putInt(value).array()

    private fun readInt32(data: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(data, offset, 4).int

    private fun gzip(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(input.size)
        GZIPOutputStream(out).use { it.write(input) }
        return out.toByteArray()
    }

    private fun gunzip(input: ByteArray): ByteArray {
        GZIPInputStream(input.inputStream()).use { return it.readBytes() }
    }
}

internal data class VolcAsrServerMessage(
    val messageType: Int,
    val isLastPackage: Boolean,
    val payload: JsonObject?,
    val error: String?
)
