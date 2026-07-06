package com.littlehelper.shell.transport

/**
 * 进程内当前 Gateway 连接配置（由 [com.littlehelper.viewmodel.MainViewModel] 在启动与设置变更时更新）。
 * 供 SessionReducer、Canvas、下载等无法直接注入 Context 的模块读取。
 */
object GatewayRuntime {

    @Volatile
    private var current: GatewayConfig? = null

    fun setConfig(config: GatewayConfig) {
        current = config
    }

    fun config(): GatewayConfig = current ?: error("GatewayRuntime 未初始化")

    fun configOrNull(): GatewayConfig? = current

    fun httpBaseUrl(): String {
        val cfg = current ?: return ""
        return if (cfg.isConnectable) cfg.httpBaseUrl() else ""
    }

    fun uploadHost(): String {
        val cfg = current ?: return ""
        return if (cfg.isConnectable) cfg.host else ""
    }
}
