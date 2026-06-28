package com.littlehelper.shell.model

/** Gateway 意图携带的多模态载荷（领域层 sealed，禁止 Map<String, Any> 泄漏至 UI）。 */
sealed class ModulePayload {
    data object Empty : ModulePayload()

    data class Note(
        val items: List<ShellNoteItem> = emptyList()
    ) : ModulePayload()

    data class Whiteboard(
        val components: List<UiComponentDto> = emptyList()
    ) : ModulePayload()

    data class Stock(
        val symbol: String,
        val displayName: String,
        val priceText: String? = null
    ) : ModulePayload()

    data class Web(
        val url: String,
        val title: String? = null
    ) : ModulePayload()
}

/** OpenClaw 下发的记事本快照行（非 Room 真相源）。 */
data class ShellNoteItem(
    val id: String,
    val typeLabel: String,
    val timestamp: String,
    val content: String,
    val done: Boolean = false
)
