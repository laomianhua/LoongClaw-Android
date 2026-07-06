package com.littlehelper.settings

import android.content.Context

class AppUiLanguageStore(private val appContext: Context) {

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppUiLanguage = AppLanguageContext.loadLanguage(appContext)

    fun save(language: AppUiLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.wire).apply()
    }

    companion object {
        const val PREFS_NAME = "littlehelper_ui_language"
        const val KEY_LANGUAGE = "language"
    }
}
