package com.littlehelper.settings

/**
 * 产品层会话策略：Release 固定连接 Gateway 默认 agent `main`（`agent:main:main`）。
 *
 * 多 Agent 组装逻辑保留在 [SessionKeyResolver]；若需连接其他 agent，可：
 * 1. 修改 [PRODUCT_AGENT_NAME] 并重新编译；或
 * 2. 恢复 [com.littlehelper.ui.settings.GatewaySettingsSheet] 中的智能体名称输入框。
 *
 * 详见仓库 README「进阶：多 Agent（代码级）」。
 */
object AgentSessionPolicy {

    /** 当前产品固定使用的 agent id（对应 sessionKey `agent:main:main`）。 */
    const val PRODUCT_AGENT_NAME: String = SessionKeyResolver.DEFAULT_AGENT_NAME

    fun productSessionKey(): String = SessionKeyResolver.resolve(PRODUCT_AGENT_NAME)
}
