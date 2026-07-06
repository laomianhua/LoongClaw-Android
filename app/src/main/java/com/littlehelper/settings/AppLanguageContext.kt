package com.littlehelper.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object AppLanguageContext {

    private fun prefs(context: Context) =
        context.getSharedPreferences(AppUiLanguageStore.PREFS_NAME, Context.MODE_PRIVATE)

    fun loadLanguage(context: Context): AppUiLanguage =
        AppUiLanguage.fromWire(
            prefs(context).getString(AppUiLanguageStore.KEY_LANGUAGE, AppUiLanguage.ZH.wire),
        )

    fun wrap(context: Context): Context {
        val locale = when (loadLanguage(context)) {
            AppUiLanguage.EN -> Locale.ENGLISH
            AppUiLanguage.ZH -> Locale.SIMPLIFIED_CHINESE
        }
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
