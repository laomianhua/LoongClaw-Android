package com.littlehelper.shell.transport



import android.content.Context

import com.littlehelper.settings.AgentSessionPolicy
import com.littlehelper.settings.GatewaySettingsStore



object GatewayConfigProvider {



    fun resolve(context: Context): GatewayConfig {

        val saved = GatewaySettingsStore(context).load()

        return if (saved.isConfigured) {

            saved.toGatewayConfig()

        } else {

            GatewayConfig.unconfigured(AgentSessionPolicy.productSessionKey())

        }

    }



    fun needsFirstTimeSetup(context: Context): Boolean =

        !GatewaySettingsStore(context).isConfigured()



    fun isConfigured(context: Context): Boolean =

        !needsFirstTimeSetup(context)

}

