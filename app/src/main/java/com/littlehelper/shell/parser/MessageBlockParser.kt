package com.littlehelper.shell.parser

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.littlehelper.shell.modal.ModalAction
import com.littlehelper.shell.modal.ModalBlock

/**
 * 解析 Agent 多模态消息，兼容多种 OpenClaw/Gateway 下发格式：
 * 1. 标准：`===CHAT===` + `===MODAL===` JSON + 可选 `===END===`
 * 2. `===CHAT===` 后直接跟裸 JSON（无 `===MODAL===` 标记）
 * 3. 整条消息为裸 JSON：`{"action":"open","blocks":[...]}`
 */
object MessageBlockParser {

    const val MARKER_CHAT = "===CHAT==="
    const val MARKER_MODAL = "===MODAL==="
    const val MARKER_END = "===END==="

    data class ParseResult(
        val chatText: String,
        val modalAction: ModalAction? = null,
        val modalBlocks: List<ModalBlock> = emptyList(),
        val modalParseError: String? = null
    )

    fun parse(raw: String): ParseResult {
        val normalized = stripEndMarker(raw)
        val chatIndex = normalized.indexOf(MARKER_CHAT)
        val modalIndex = normalized.indexOf(MARKER_MODAL)

        if (chatIndex < 0 && modalIndex < 0) {
            tryParseBareModalJson(normalized)?.let { return it }
            return ParseResult(chatText = normalized.trim())
        }

        val chatText = when {
            chatIndex >= 0 && modalIndex >= 0 && chatIndex < modalIndex ->
                normalized.substring(chatIndex + MARKER_CHAT.length, modalIndex).trim()
            chatIndex >= 0 ->
                normalized.substring(chatIndex + MARKER_CHAT.length).trim()
            else -> ""
        }

        if (modalIndex < 0) {
            if (chatIndex >= 0) {
                tryParseBareModalJson(chatText)?.let { bare ->
                    return bare.copy(
                        chatText = bare.chatText.ifBlank {
                            chatTextBeforeJson(chatText, bare)
                        }
                    )
                }
                return ParseResult(chatText = chatText)
            }
            return ParseResult(chatText = chatText.ifBlank { normalized.trim() })
        }

        val modalJson = normalized.substring(modalIndex + MARKER_MODAL.length).trim()
        val modal = parseModalJson(modalJson)
        return ParseResult(
            chatText = chatText,
            modalAction = modal.action,
            modalBlocks = modal.blocks,
            modalParseError = modal.error
        )
    }

    /** 将 Gateway 分离的 chat 与 modal JSON 拼成统一 wire 格式再解析。 */
    fun parseFromParts(chatText: String, modalJson: String?): ParseResult {
        if (modalJson.isNullOrBlank()) {
            return parse(chatText)
        }
        return parse(
            buildString {
                append(MARKER_CHAT).append('\n').append(chatText.trim()).append("\n\n")
                append(MARKER_MODAL).append('\n').append(modalJson.trim()).append('\n')
                append(MARKER_END)
            }
        )
    }

    internal fun tryParseBareModalJson(text: String): ParseResult? {
        val candidate = findModalJsonCandidate(text) ?: return null
        val modal = parseModalJson(candidate)
        if (modal.error != null || modal.action == null) return null
        val chatOnly = text.replace(candidate, "").trim()
        return ParseResult(
            chatText = chatOnly,
            modalAction = modal.action,
            modalBlocks = modal.blocks,
            modalParseError = null
        )
    }

    private fun chatTextBeforeJson(fullAfterChat: String, parsed: ParseResult): String {
        val candidate = findModalJsonCandidate(fullAfterChat) ?: return fullAfterChat.trim()
        return fullAfterChat.replace(candidate, "").trim()
    }

    internal fun findModalJsonCandidate(text: String): String? {
        var searchFrom = 0
        while (searchFrom < text.length) {
            val start = text.indexOf('{', searchFrom)
            if (start < 0) return null
            val json = extractBalancedJson(text, start) ?: run {
                searchFrom = start + 1
                continue
            }
            if (looksLikeModalDirective(json)) {
                return json
            }
            searchFrom = start + 1
        }
        return null
    }

    internal fun looksLikeModalDirective(json: String): Boolean {
        if (!json.contains("\"action\"") || !json.contains("\"blocks\"")) return false
        return runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            root.has("action") && root.get("blocks")?.isJsonArray == true
        }.getOrDefault(false)
    }

    internal fun extractBalancedJson(text: String, start: Int): String? {
        if (start < 0 || start >= text.length || text[start] != '{') return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val ch = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else when (ch) {
                    '\\' -> escaped = true
                    '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun stripEndMarker(text: String): String {
        val endIndex = text.indexOf(MARKER_END)
        return if (endIndex >= 0) text.substring(0, endIndex) else text
    }

    private data class ModalJsonResult(
        val action: ModalAction?,
        val blocks: List<ModalBlock>,
        val error: String?
    )

    private fun parseModalJson(jsonText: String): ModalJsonResult {
        if (jsonText.isBlank()) {
            return ModalJsonResult(ModalAction.NOOP, emptyList(), null)
        }
        return try {
            val root = JsonParser.parseString(jsonText.trim()).asJsonObject
            val action = ModalAction.fromWire(root.get("action")?.asString)
            val blocks = root.getAsJsonArray("blocks")?.mapNotNull { element ->
                parseBlock(element.asJsonObject)
            }.orEmpty()
            ModalJsonResult(action, blocks, null)
        } catch (e: Exception) {
            ModalJsonResult(null, emptyList(), e.message ?: "modal json parse failed")
        }
    }

    private fun parseBlock(obj: JsonObject): ModalBlock? {
        val id = obj.get("id")?.asString ?: return null
        val type = obj.get("type")?.asString ?: return null
        val title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString
        val data = obj.getAsJsonObject("data") ?: JsonObject()
        return ModalBlock(id = id, type = type, title = title, data = data)
    }

    private fun JsonArray.mapNotNull(transform: (com.google.gson.JsonElement) -> ModalBlock?): List<ModalBlock> {
        val result = mutableListOf<ModalBlock>()
        forEach { element ->
            if (element.isJsonObject) {
                transform(element.asJsonObject)?.let { result.add(it) }
            }
        }
        return result
    }

    /** 聊天气泡展示用正文：不含 wire 标记、MODAL JSON、===END===。 */
    fun chatDisplayText(raw: String): String {
        if (raw.isBlank()) return ""
        val parsed = parse(raw)
        val candidate = when {
            // 已解析出 MODAL 时只用 ===CHAT=== 段；勿把线框前的 Markdown 表格带进气泡。
            parsed.modalAction != null -> parsed.chatText
            parsed.chatText.isNotBlank() -> parsed.chatText
            hasModalDirective(raw) -> extractStreamingChatPreview(raw)
            else -> raw.trim()
        }
        return scrubWireArtifacts(candidate).trim()
    }

    fun extractStreamingChatPreview(raw: String): String {
        val text = stripEndMarker(raw)
        val modalIndex = text.indexOf(MARKER_MODAL)
        val chatIndex = text.indexOf(MARKER_CHAT)
        return when {
            chatIndex >= 0 && modalIndex >= 0 && chatIndex < modalIndex ->
                text.substring(chatIndex + MARKER_CHAT.length, modalIndex).trim()
            chatIndex >= 0 && modalIndex < 0 -> {
                val afterChat = text.substring(chatIndex + MARKER_CHAT.length)
                tryParseBareModalJson(afterChat)?.let { bare ->
                    return bare.chatText.ifBlank { chatTextBeforeJson(afterChat.trim(), bare) }
                }
                scrubWireArtifacts(afterChat.trim())
            }
            modalIndex >= 0 && chatIndex < 0 -> {
                val prefix = text.substring(0, modalIndex).trim()
                if (prefix.startsWith(MARKER_CHAT)) {
                    prefix.removePrefix(MARKER_CHAT).trim()
                } else {
                    ""
                }
            }
            else -> {
                tryParseBareModalJson(text)?.chatText?.takeIf { it.isNotBlank() }
                    ?: if (hasModalDirective(text)) "" else text.trim()
            }
        }
    }

    internal fun scrubWireArtifacts(text: String): String {
        var cleaned = text
            .replace(MARKER_CHAT, "")
            .replace(MARKER_MODAL, "")
            .replace(MARKER_END, "")
        findModalJsonCandidate(cleaned)?.let { json ->
            cleaned = cleaned.replace(json, "")
        }
        return cleaned.trim()
    }

    fun hasModalDirective(raw: String): Boolean {
        if (raw.contains(MARKER_MODAL)) return true
        return findModalJsonCandidate(raw) != null
    }

    /** 结构化数据（Markdown 表格等）应走 MODAL，不应作为聊天气泡正文。 */
    internal fun looksLikeStructuredContentDump(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (looksLikeModalDirective(trimmed)) return false
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val pipeRows = lines.count { it.contains('|') }
        if (pipeRows >= 2) return true
        if (lines.size >= 3 && lines.any { it.matches(Regex("^[-|:\\s]+$")) }) return true
        return false
    }
}

/** 流式防抖：累积 delta，仅在 END 标记或 finalize 时解析 MODAL。 */
class StreamingMessageBlockParser {

    private val buffer = StringBuilder()

    val accumulatedText: String get() = buffer.toString()

    fun reset() {
        buffer.clear()
    }

    fun append(chunk: String, appendDelta: Boolean) {
        if (appendDelta) {
            buffer.append(chunk)
        } else {
            buffer.clear()
            buffer.append(chunk)
        }
    }

    fun partialChatPreview(): String? {
        val text = buffer.toString()
        if (!text.contains(MessageBlockParser.MARKER_END) &&
            !MessageBlockParser.hasModalDirective(text)
        ) {
            return null
        }
        return MessageBlockParser.parse(text).chatText.takeIf { it.isNotBlank() }
    }

    fun finalize(): MessageBlockParser.ParseResult? {
        if (buffer.isEmpty()) return null
        val result = MessageBlockParser.parse(buffer.toString())
        buffer.clear()
        return result
    }

    fun tryParseIfComplete(): MessageBlockParser.ParseResult? {
        val text = buffer.toString()
        if (text.contains(MessageBlockParser.MARKER_END)) {
            return finalize()
        }
        if (!MessageBlockParser.hasModalDirective(text)) return null
        val parsed = MessageBlockParser.parse(text)
        if (parsed.modalParseError != null || parsed.modalAction == null) return null
        buffer.clear()
        return parsed
    }
}
