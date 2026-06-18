package com.littlehelper.network

import com.littlehelper.util.DebugLog
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.littlehelper.data.LlmOpsResponse
import com.littlehelper.domain.todo.NotebookAction
import com.littlehelper.domain.todo.TodoActionPayload

object LlmResponseParser {
    const val SAVE_START = "___SAVE_START___"
    const val SAVE_END = "___SAVE_END___"
    const val DELETE_START = "___DELETE_START___"
    const val DELETE_END = "___DELETE_END___"
    const val DB_OPS_START = "___DB_OPS_START___"
    const val DB_OPS_END = "___DB_OPS_END___"

    private val gson = Gson()

    data class ParsedResponse(
        val reply: String,
        val savePayload: SavePayload?,
        val deletePayload: DeletePayload?,
        val dbOpsPayload: LlmOpsResponse?
    )

    fun parse(content: String): ParsedResponse {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return ParsedResponse(reply = "", savePayload = null, deletePayload = null, dbOpsPayload = null)
        }

        val saveStart = trimmed.indexOf(SAVE_START)
        val deleteStart = trimmed.indexOf(DELETE_START)
        val dbOpsStart = trimmed.indexOf(DB_OPS_START)
        val firstMarker = listOf(saveStart, deleteStart, dbOpsStart).filter { it >= 0 }.minOrNull()

        val reply = if (firstMarker != null) {
            trimmed.substring(0, firstMarker).trim()
        } else {
            trimmed
        }

        val savePayload = if (saveStart >= 0) parseSaveBlock(trimmed, saveStart) else null
        val deletePayload = if (deleteStart >= 0) parseDeleteBlock(trimmed, deleteStart) else null
        val dbOpsPayload = if (dbOpsStart >= 0) parseDbOpsBlock(trimmed, dbOpsStart) else null

        return ParsedResponse(
            reply = reply.ifBlank { trimmed },
            savePayload = savePayload,
            deletePayload = deletePayload,
            dbOpsPayload = dbOpsPayload
        )
    }

    private fun parseSaveBlock(content: String, startIndex: Int): SavePayload? {
        val endIndex = content.indexOf(SAVE_END, startIndex)
        if (endIndex < 0) return null
        val jsonBlock = content.substring(startIndex + SAVE_START.length, endIndex).trim()
        return parseJson<SavePayload>(jsonBlock)
    }

    private fun parseDeleteBlock(content: String, startIndex: Int): DeletePayload? {
        val endIndex = content.indexOf(DELETE_END, startIndex)
        if (endIndex < 0) return null
        val jsonBlock = content.substring(startIndex + DELETE_START.length, endIndex).trim()
        return parseJson<DeletePayload>(jsonBlock)
    }

    private fun parseDbOpsBlock(content: String, startIndex: Int): LlmOpsResponse? {
        val endIndex = content.indexOf(DB_OPS_END, startIndex)
        if (endIndex < 0) return null
        val jsonBlock = content.substring(startIndex + DB_OPS_START.length, endIndex).trim()
        DebugLog.d("LLM_PROTOCOL", "云端下发的原始JSON为: $jsonBlock")
        val raw = parseJson<RawDbOpsJson>(jsonBlock) ?: return null
        val todoPayload = parseTodoPayload(jsonBlock, raw.action)
        val status = resolveDbOpsStatus(raw)
        if (status == "ignore") {
            return LlmOpsResponse(
                status = "ignore",
                reason = raw.reason ?: "text_too_vague_or_no_intent",
                intentRoute = raw.intentRoute,
                action = raw.action,
                payload = raw.payload,
                todoPayload = todoPayload,
                operations = emptyList()
            )
        }
        return LlmOpsResponse(
            status = "success",
            reason = null,
            intentRoute = raw.intentRoute,
            action = raw.action,
            payload = raw.payload,
            todoPayload = todoPayload,
            operations = raw.operations.orEmpty()
        )
    }

    private fun parseTodoPayload(jsonBlock: String, action: String?): TodoActionPayload? {
        if (!NotebookAction.isTodoAction(action)) return null
        return try {
            val root = gson.fromJson(jsonBlock, JsonObject::class.java) ?: return null
            val payloadElement = root.get("payload") ?: return null
            gson.fromJson(payloadElement, TodoActionPayload::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    private fun resolveDbOpsStatus(raw: RawDbOpsJson): String {
        if (!raw.status.isNullOrBlank()) return raw.status
        if (!raw.operations.isNullOrEmpty()) return "success"
        if (com.littlehelper.domain.map.IntentRoute.fromWire(raw.intentRoute) ==
            com.littlehelper.domain.map.IntentRoute.MAP &&
            !raw.action.isNullOrBlank()
        ) {
            return "success"
        }
        if (NotebookAction.isTodoAction(raw.action)) {
            return "success"
        }
        return "ignore"
    }

    private data class RawDbOpsJson(
        val status: String? = null,
        val reason: String? = null,
        @com.google.gson.annotations.SerializedName("intent_route")
        val intentRoute: String? = null,
        val action: String? = null,
        val payload: com.littlehelper.domain.map.MapInstructionPayload? = null,
        val operations: List<com.littlehelper.data.MemoryOperation>? = null
    )

    private inline fun <reified T> parseJson(jsonBlock: String): T? {
        if (jsonBlock.isBlank()) return null
        return try {
            gson.fromJson(jsonBlock, T::class.java)
        } catch (_: JsonSyntaxException) {
            extractJson(jsonBlock)?.let { json ->
                try {
                    gson.fromJson(json, T::class.java)
                } catch (_: JsonSyntaxException) {
                    null
                }
            }
        }
    }

    private fun extractJson(content: String): String? {
        val trimmed = content.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        return null
    }
}
