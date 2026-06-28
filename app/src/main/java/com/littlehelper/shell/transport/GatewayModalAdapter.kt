package com.littlehelper.shell.transport

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.littlehelper.shell.parser.MessageBlockParser

/**
 * 从 Gateway `session.message` 载荷中提取 modal 指令，并规范化为 App 可解析的 wire 文本。
 */
object GatewayModalAdapter {

    data class AssistantWire(
        val chatText: String,
        val modalJson: String?,
        val wireText: String
    )

    fun normalizeAssistantMessage(payload: JsonObject, envelope: JsonObject): AssistantWire {
        val chatParts = mutableListOf<String>()
        var modalJson: String? = null

        modalJson = extractModalFromPayload(payload) ?: extractModalFromPayload(envelope)

        val content = envelope.get("content")
        if (content != null && content.isJsonArray) {
            content.asJsonArray.forEach { element ->
                if (!element.isJsonObject) {
                    element.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
                        ?.let { chatParts.add(it) }
                    return@forEach
                }
                val part = element.asJsonObject
                when (part.get("type")?.asString?.lowercase()) {
                    "text", "output_text", "input_text" -> {
                        part.optString("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { chatParts.add(it) }
                    }
                    "modal", "canvas", "agent_canvas", "whiteboard", "ui" -> {
                        modalJson = modalJson ?: extractModalJsonFromPart(part)
                    }
                    else -> {
                        val asModal = extractModalJsonFromPart(part)
                        if (asModal != null) {
                            modalJson = modalJson ?: asModal
                        } else {
                            part.optString("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { chatParts.add(it) }
                        }
                    }
                }
            }
        } else {
            envelope.optString("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { chatParts.add(it) }
        }

        val joinedChat = chatParts.joinToString("\n").trim()
        val wireText = when {
            joinedChat.contains(MessageBlockParser.MARKER_MODAL) -> joinedChat
            modalJson != null -> buildWire(joinedChat, modalJson!!)
            else -> joinedChat
        }
        return AssistantWire(
            chatText = joinedChat,
            modalJson = modalJson,
            wireText = wireText
        )
    }

    fun buildWireText(chatText: String, modalJson: String): String = buildWire(chatText, modalJson)

    private fun buildWire(chatText: String, modalJson: String): String = buildString {
        append(MessageBlockParser.MARKER_CHAT).append('\n').append(chatText).append("\n\n")
        append(MessageBlockParser.MARKER_MODAL).append('\n').append(modalJson.trim()).append('\n')
        append(MessageBlockParser.MARKER_END)
    }

    private fun extractModalFromPayload(payload: JsonObject): String? {
        payload.get("modal")?.let { element ->
            if (element.isJsonObject) return element.asJsonObject.toString()
            if (element.isJsonPrimitive) {
                val raw = element.asString.trim()
                if (MessageBlockParser.looksLikeModalDirective(raw)) return raw
            }
        }
        payload.getAsJsonObject("ui")?.get("modal")?.asJsonObject?.toString()?.let { return it }
        payload.getAsJsonObject("canvas")?.toString()?.let {
            if (MessageBlockParser.looksLikeModalDirective(it)) return it
        }
        return null
    }

    private fun extractModalJsonFromPart(part: JsonObject): String? {
        part.getAsJsonObject("modal")?.toString()?.let { if (MessageBlockParser.looksLikeModalDirective(it)) return it }
        part.getAsJsonObject("data")?.takeIf { it.has("action") && it.has("blocks") }?.toString()?.let { return it }
        part.optString("json")?.trim()?.takeIf { MessageBlockParser.looksLikeModalDirective(it) }?.let { return it }
        part.optString("text")?.trim()?.let { text ->
            MessageBlockParser.findModalJsonCandidate(text)?.let { return it }
            if (MessageBlockParser.looksLikeModalDirective(text)) return text
        }
        if (part.has("action") && part.has("blocks")) return part.toString()
        return null
    }

    private fun JsonObject.optString(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.asString
}
