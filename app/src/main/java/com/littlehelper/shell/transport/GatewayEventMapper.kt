package com.littlehelper.shell.transport

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.littlehelper.ChatRole
import com.littlehelper.settings.AssistantToneMessage
import com.littlehelper.shell.model.ClawIntent
import com.littlehelper.shell.model.ClawIntentPreload
import com.littlehelper.shell.model.ClawSessionEvent
import com.littlehelper.shell.model.ClawSessionResponse
import com.littlehelper.shell.model.ModuleId
import com.littlehelper.shell.model.PanelCommand
import com.littlehelper.shell.model.ShellNoteItem
import com.littlehelper.shell.model.ModulePayload
import com.littlehelper.shell.model.UiComponentDto
import com.littlehelper.shell.parser.MessageBlockParser

/**
 * Gateway 下行 `event` 帧 → Shell [ClawSessionEvent]。
 *
 * v4 协议事件：`chat.delta` / `session.message` / `session.operation` / `session.tool` 等。
 * `intent.preload` 不存在于 Gateway，由 `session.operation` 或 `session.tool` 启发式映射。
 */
object GatewayEventMapper {

    fun mapGatewayMessage(
        rawJson: String,
        sessionId: String
    ): ClawSessionEvent? {
        val root = runCatching { JsonParser.parseString(rawJson).asJsonObject }.getOrNull()
            ?: return null
        if (root.get("type")?.asString != "event") return null

        val eventName = root.get("event")?.asString ?: return null
        val payload = root.getAsJsonObject("payload") ?: JsonObject()

        return when (eventName) {
            "connect.challenge",
            "tick",
            "heartbeat",
            "presence",
            "sessions.changed",
            "node.pair.requested",
            "node.pair.resolved" -> null

            "chat.delta",
            "chat.inject" -> mapChatDelta(sessionId, payload)

            "session.message" -> mapSessionMessage(sessionId, payload)

            "session.operation" -> mapSessionOperation(sessionId, payload)

            "session.tool" -> mapSessionTool(sessionId, payload)

            // 保留 Mock / 未来 Gateway 扩展事件名
            "shell.chat.delta" -> mapLegacyChatDelta(sessionId, payload, appendDelta = false)
            "shell.chat.final" -> mapLegacyChatFinal(sessionId, payload)
            "shell.intent.preload" -> mapLegacyIntentPreload(sessionId, payload)
            "shell.intent.final" -> mapLegacyIntentFinal(sessionId, payload)

            else -> null
        }
    }

    private fun mapChatDelta(sessionId: String, payload: JsonObject): ClawSessionEvent? {
        val envelope = resolveMessageEnvelope(payload)
        val role = envelope.roleOrDefault()
        val deltaText = payload.stringOr("deltaText", payload.stringOr("text", ""))
        val normalized = if (role == ChatRole.ASSISTANT) {
            resolveAssistantWire(payload, envelope, deltaText)
        } else if (deltaText.isNotBlank()) {
            deltaText
        } else {
            extractTextContent(envelope)
        }
        if (normalized.isBlank()) return null
        val modalReplaceFrame = role == ChatRole.ASSISTANT &&
            MessageBlockParser.hasModalDirective(normalized)
        val isSnapshot = payload.get("snapshot")?.asBoolean == true
            || payload.get("cumulative")?.asBoolean == true
            || payload.get("replace")?.asBoolean == true
        return ClawSessionEvent.ChatDelta(
            sessionId = sessionId,
            turnId = turnIdFrom(payload, envelope),
            role = role,
            text = normalized,
            appendDelta = !isSnapshot &&
                !modalReplaceFrame &&
                !MessageBlockParser.hasModalDirective(normalized)
        )
    }

    /** payload.modal 与 deltaText 分轨时：短摘要可进 CHAT，表格类正文不进气泡。 */
    private fun resolveChatForModalWire(joinedChat: String, deltaText: String): String {
        if (joinedChat.isNotBlank()) return joinedChat
        if (deltaText.isBlank() || MessageBlockParser.hasModalDirective(deltaText)) return ""
        if (MessageBlockParser.looksLikeStructuredContentDump(deltaText)) return ""
        return MessageBlockParser.chatDisplayText(deltaText).ifBlank {
            deltaText.lineSequence().firstOrNull()?.trim().orEmpty()
        }
    }

    /** 与 [mapChatDelta] 对齐：session.message 也可能把 MODAL 放在 deltaText / payload.modal。 */
    private fun resolveAssistantWire(
        payload: JsonObject,
        envelope: JsonObject,
        deltaText: String
    ): String {
        val adapted = GatewayModalAdapter.normalizeAssistantMessage(payload, envelope)
        return when {
            adapted.modalJson != null -> {
                val chatForWire = resolveChatForModalWire(adapted.chatText, deltaText)
                GatewayModalAdapter.buildWireText(chatForWire, adapted.modalJson)
            }
            adapted.wireText.contains(MessageBlockParser.MARKER_MODAL) -> adapted.wireText
            MessageBlockParser.hasModalDirective(deltaText) -> deltaText
            deltaText.isNotBlank() -> deltaText
            adapted.wireText.isNotBlank() -> adapted.wireText
            else -> extractTextContent(envelope)
        }
    }

    private fun mapSessionMessage(sessionId: String, payload: JsonObject): ClawSessionEvent? {
        val envelope = resolveMessageEnvelope(payload)
        val role = envelope.roleOrDefault()
        val deltaText = payload.stringOr("deltaText", payload.stringOr("text", ""))
        val rawText = if (role == ChatRole.ASSISTANT) {
            resolveAssistantWire(payload, envelope, deltaText)
        } else {
            deltaText.takeIf { it.isNotBlank() } ?: extractTextContent(envelope)
        }
        val turnId = turnIdFrom(payload, envelope)
        if (rawText.isBlank()) {
            if (role == ChatRole.ASSISTANT && hasThinkingContent(envelope)) {
                return ClawSessionEvent.AssistantThinking(turnId = turnId)
            }
            return null
        }
        val text = if (role == ChatRole.USER) {
            AssistantToneMessage.stripEmbeddedTonePrefix(rawText)
        } else {
            rawText
        }
        val isFinal = payload.get("final")?.asBoolean
            ?: envelope.get("final")?.asBoolean
            ?: true

        return if (isFinal) {
            ClawSessionEvent.ChatFinal(
                sessionId = sessionId,
                turnId = turnId,
                role = role,
                text = text
            )
        } else {
            ClawSessionEvent.ChatDelta(
                sessionId = sessionId,
                turnId = turnId,
                role = role,
                text = text,
                appendDelta = false
            )
        }
    }

    private fun mapSessionOperation(sessionId: String, payload: JsonObject): ClawSessionEvent? {
        val moduleWire = payload.optString("module")
            ?: payload.optString("targetModule")
            ?: payload.getAsJsonObject("ui")?.optString("module")
        if (moduleWire == null) return null

        val turnId = turnIdFrom(payload)
        val drawer = payload.optString("drawerState")
            ?: payload.getAsJsonObject("ui")?.optString("drawerState")

        return ClawSessionEvent.IntentPreload(
            ClawIntentPreload(
                sessionId = sessionId,
                turnId = turnId,
                targetModule = ModuleId.fromWire(moduleWire),
                drawerState = drawer?.let(PanelCommand::fromWire)
            )
        )
    }

    private fun mapSessionTool(sessionId: String, payload: JsonObject): ClawSessionEvent? {
        val result = payload.getAsJsonObject("result")
            ?: payload.getAsJsonObject("output")
            ?: payload
        val moduleWire = result.optString("targetModule")
            ?: result.optString("module")
            ?: inferModuleFromToolName(payload.optString("name") ?: payload.optString("tool"))
        if (moduleWire == null) return null

        val turnId = turnIdFrom(payload)
        val text = result.stringOr("textContent", result.stringOr("text", result.stringOr("summary", "")))
        val intent = ClawIntent(
            action = payload.stringOr("name", payload.stringOr("tool", "tool")),
            targetModule = ModuleId.fromWire(moduleWire),
            drawerState = result.optString("drawerState")?.let(PanelCommand::fromWire),
            payload = mapModulePayload(result.getAsJsonObject("payload") ?: result, moduleWire)
        )

        return ClawSessionEvent.IntentFinal(
            ClawSessionResponse(
                sessionId = sessionId,
                turnId = turnId,
                textContent = text,
                intent = intent
            )
        )
    }

    private fun inferModuleFromToolName(toolName: String?): String? {
        if (toolName.isNullOrBlank()) return null
        val lower = toolName.lowercase()
        return when {
            "whiteboard" in lower || "board" in lower -> "WHITEBOARD"
            "map" in lower || "location" in lower -> "MAP"
            "note" in lower -> "NOTE"
            else -> null
        }
    }

    private fun mapLegacyChatDelta(
        sessionId: String,
        payload: JsonObject,
        appendDelta: Boolean
    ): ClawSessionEvent.ChatDelta = ClawSessionEvent.ChatDelta(
        sessionId = sessionId,
        turnId = turnIdFrom(payload),
        role = payload.roleOrDefault(),
        text = payload.stringOr("text", ""),
        appendDelta = appendDelta
    )

    private fun mapLegacyChatFinal(sessionId: String, payload: JsonObject): ClawSessionEvent.ChatFinal =
        ClawSessionEvent.ChatFinal(
            sessionId = sessionId,
            turnId = turnIdFrom(payload),
            role = payload.roleOrDefault(),
            text = payload.stringOr("text", "")
        )

    private fun mapLegacyIntentPreload(sessionId: String, payload: JsonObject): ClawSessionEvent.IntentPreload =
        ClawSessionEvent.IntentPreload(
            ClawIntentPreload(
                sessionId = sessionId,
                turnId = turnIdFrom(payload),
                targetModule = ModuleId.fromWire(payload.stringOr("targetModule", "NONE")),
                drawerState = payload.optString("drawerState")?.let(PanelCommand::fromWire)
            )
        )

    private fun mapLegacyIntentFinal(sessionId: String, payload: JsonObject): ClawSessionEvent.IntentFinal {
        val turnId = turnIdFrom(payload)
        val text = payload.stringOr("textContent", "")
        val intentObj = payload.getAsJsonObject("intent")

        val intent = intentObj?.let { obj ->
            ClawIntent(
                action = obj.stringOr("action", ""),
                targetModule = ModuleId.fromWire(obj.stringOr("targetModule", "NONE")),
                drawerState = obj.optString("drawerState")?.let(PanelCommand::fromWire),
                payload = mapModulePayload(obj.getAsJsonObject("payload"), obj.stringOr("targetModule", ""))
            )
        }

        return ClawSessionEvent.IntentFinal(
            ClawSessionResponse(
                sessionId = sessionId,
                turnId = turnId,
                textContent = text,
                intent = intent
            )
        )
    }

    private fun turnIdFrom(payload: JsonObject, envelope: JsonObject = resolveMessageEnvelope(payload)): String {
        payload.get("messageId")?.takeIf { !it.isJsonNull }?.asString?.let { return it }
        envelope.getAsJsonObject("__openclaw")?.get("id")?.takeIf { !it.isJsonNull }?.asString?.let { return it }
        return envelope.stringOr(
            "id",
            payload.stringOr("turnId", payload.stringOr("messageId", payload.stringOr("streamId", payload.stringOr("id", "turn"))))
        )
    }

    /** Gateway v4：`session.message` 正文在 `payload.message.content`（字符串或 content 块数组）。 */
    private fun resolveMessageEnvelope(payload: JsonObject): JsonObject =
        payload.getAsJsonObject("message") ?: payload

    internal fun extractTextContentForTest(message: JsonObject): String = extractTextContent(message)

    private fun extractTextContent(message: JsonObject): String {
        message.get("text")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        val content = message.get("content") ?: return ""
        if (content.isJsonPrimitive && content.asJsonPrimitive.isString) {
            return content.asString.trim()
        }
        if (!content.isJsonArray) return ""

        val parts = content.asJsonArray.mapNotNull { element ->
            if (!element.isJsonObject) {
                element.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                val part = element.asJsonObject
                when (part.get("type")?.asString?.lowercase()) {
                    "text", "output_text", "input_text" -> part.optString("text")?.trim()
                    "thinking", "reasoning" -> null
                    else -> part.optString("text")?.trim()
                        ?: part.optString("content")?.trim()
                }?.takeIf { it.isNotEmpty() }
            }
        }
        return parts.joinToString("\n")
    }

    private fun hasThinkingContent(message: JsonObject): Boolean {
        val content = message.get("content") ?: return false
        if (!content.isJsonArray) return false
        return content.asJsonArray.any { element ->
            if (!element.isJsonObject) return@any false
            when (element.asJsonObject.get("type")?.asString?.lowercase()) {
                "thinking", "reasoning" -> true
                else -> false
            }
        }
    }

    private fun mapModulePayload(payload: JsonObject?, targetModule: String): ModulePayload {
        if (payload == null) return ModulePayload.Empty
        return when (ModuleId.fromWire(targetModule)) {
            ModuleId.WHITEBOARD -> {
                val components = payload.getAsJsonArray("components")?.mapNotNull { element ->
                    val item = element.asJsonObject
                    UiComponentDto(
                        id = item.stringOr("id", java.util.UUID.randomUUID().toString()),
                        type = item.stringOr("type", "text"),
                        text = item.get("text")?.asString,
                        icon = item.get("icon")?.asString
                    )
                }.orEmpty()
                ModulePayload.Whiteboard(components)
            }

            ModuleId.NONE -> ModulePayload.Empty

            else -> {
                // NOTE: Gateway 仍可能发 NOTE 模块，用通用 Note 包装
                val items = payload.getAsJsonArray("items")?.mapNotNull { element ->
                    val item = element.asJsonObject
                    ShellNoteItem(
                        id = item.stringOr("id", java.util.UUID.randomUUID().toString()),
                        typeLabel = item.stringOr("typeLabel", "记事"),
                        timestamp = item.stringOr("timestamp", ""),
                        content = item.stringOr("content", ""),
                        done = item.get("done")?.asBoolean == true
                    )
                }.orEmpty()
                ModulePayload.Note(items)
            }
        }
    }

    private fun JsonObject.stringOr(key: String, default: String): String =
        get(key)?.takeIf { !it.isJsonNull }?.asString ?: default

    private fun JsonObject.optString(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.asString

    private fun JsonObject.roleOrDefault(): ChatRole {
        val raw = stringOr("role", "assistant").lowercase()
        return if (raw == "user") ChatRole.USER else ChatRole.ASSISTANT
    }
}
