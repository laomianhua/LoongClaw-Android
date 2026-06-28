package com.littlehelper.shell.modules.renderers

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.littlehelper.shell.modal.ModalBlock

internal data class WebViewLayoutConfig(
    /** 固定视口高度；为 null 时表示由父布局撑满可用高度。 */
    val fixedHeight: Dp?,
    val scrollable: Boolean,
    val fillAvailableHeight: Boolean
)

internal fun parseWebViewLayoutConfig(data: JsonObject): WebViewLayoutConfig {
    val heightDp = readHeightDp(data)
    val scrollable = data.get("scrollable")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
    val fillHeightExplicit = data.get("fillHeight")?.takeIf { !it.isJsonNull }?.asBoolean
    val fillAvailableHeight = fillHeightExplicit ?: (scrollable && heightDp == null)
    val fixedHeight = when {
        fillAvailableHeight -> null
        heightDp != null -> heightDp
        else -> 420.dp
    }
    return WebViewLayoutConfig(
        fixedHeight = fixedHeight,
        scrollable = scrollable,
        fillAvailableHeight = fillAvailableHeight
    )
}

private fun readHeightDp(data: JsonObject): Dp? {
    val raw = data.get("heightDp")?.takeIf { !it.isJsonNull }?.asDouble
        ?: data.get("height")?.takeIf { !it.isJsonNull }?.asDouble
    return raw?.toFloat()?.dp
}

internal fun modalBlockUsesFillHeightWebView(block: ModalBlock): Boolean =
    block.type.equals("webview", ignoreCase = true) &&
        parseWebViewLayoutConfig(block.data).fillAvailableHeight
