package com.littlehelper.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class GatewaySettingsStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun load(): GatewayConnectionSettings = GatewayConnectionSettings(
        host = prefs.getString(KEY_HOST, "").orEmpty(),
        port = prefs.getInt(KEY_PORT, GatewayConnectionSettings.DEFAULT_PORT),
        authMode = GatewayAuthMode.fromWire(prefs.getInt(KEY_AUTH_MODE, GatewayAuthMode.TOKEN.wire)),
        plainToken = prefs.getString(KEY_TOKEN, "").orEmpty(),
        plainPassword = prefs.getString(KEY_PASSWORD, "").orEmpty(),
        agentName = AgentSessionPolicy.PRODUCT_AGENT_NAME,
    )

    /** 设置页表单展示：未保存过配置时返回 UI 默认值（端口 18789、Token 模式等）。 */
    fun loadForForm(): GatewayConnectionSettings {
        if (!prefs.contains(KEY_HOST)) {
            return GatewayConnectionSettings.formDefaults()
        }
        return load().withFormDefaults()
    }

    fun save(settings: GatewayConnectionSettings) {
        prefs.edit()
            .putString(KEY_HOST, settings.host.trim())
            .putInt(KEY_PORT, settings.port)
            .putInt(KEY_AUTH_MODE, settings.authMode.wire)
            .putString(KEY_TOKEN, settings.plainToken)
            .putString(KEY_PASSWORD, settings.plainPassword)
            .putString(KEY_AGENT_NAME, AgentSessionPolicy.PRODUCT_AGENT_NAME)
            .apply()
    }

    fun isConfigured(): Boolean = load().isConfigured

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "littlehelper_gateway_settings"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_AUTH_MODE = "auth_mode"
        private const val KEY_TOKEN = "token"
        private const val KEY_PASSWORD = "password"
        private const val KEY_AGENT_NAME = "agent_name"
    }
}
