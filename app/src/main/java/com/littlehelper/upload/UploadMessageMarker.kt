package com.littlehelper.upload

/**
 * Gateway  wire 消息中的 `[upload:fileId:fileName]` 标记。
 * 用户界面与聊天气泡不展示该标记，仅发送给 Gateway。
 */
object UploadMessageMarker {

    private val MARKER_REGEX = Regex("""\s*\[upload:[^:\]]+:[^\]]+\]""")
    private val FILE_NAME_REGEX = Regex("""\[upload:[^:\]]+:([^\]]+)\]""")

    fun append(userText: String, fileId: String, fileName: String): String {
        val marker = "[upload:$fileId:$fileName]"
        val trimmed = userText.trim()
        return if (trimmed.isBlank()) marker else "$trimmed $marker"
    }

    fun stripForDisplay(text: String): String = MARKER_REGEX.replace(text, "").trim()

    /** 聊天气泡展示：去掉 wire 标记；纯附件消息则显示文件名。 */
    fun displayTextForChat(text: String): String {
        val stripped = stripForDisplay(text)
        if (stripped.isNotBlank()) return stripped
        return FILE_NAME_REGEX.find(text)?.groupValues?.get(1).orEmpty()
    }
}
