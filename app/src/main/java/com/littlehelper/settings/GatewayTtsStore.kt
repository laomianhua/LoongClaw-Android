package com.littlehelper.settings

import android.content.Context

class GatewayTtsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): Boolean = prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun save(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "littlehelper_gateway_tts"
        private const val KEY_ENABLED = "enabled"
        const val DEFAULT_ENABLED = true
    }
}
