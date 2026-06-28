package com.littlehelper.settings

import android.content.Context

class AssistantToneStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AssistantTone =
        AssistantTone.fromWire(prefs.getString(KEY_TONE, AssistantTone.FRIEND.wire))

    fun save(tone: AssistantTone) {
        prefs.edit().putString(KEY_TONE, tone.wire).apply()
    }

    fun systemText(): String = load().systemText()

    /** patch 不可用时：语气指令与用户原话合并为一条 send，避免 Gateway 单独确认。 */
    fun messageWithUserContent(userText: String): String {
        val body = userText.trim()
        val instructions = systemText()
        return buildString {
            append(TONE_PREFIX)
            append(' ')
            append(instructions)
            append("（按此风格回复下面这句话，不必单独确认。）")
            append("\n\n")
            append(body)
        }
    }

    companion object {
        const val TONE_PREFIX = "[助手语气设置]"
        private const val PREFS_NAME = "littlehelper_assistant_tone"
        private const val KEY_TONE = "tone"
    }
}
