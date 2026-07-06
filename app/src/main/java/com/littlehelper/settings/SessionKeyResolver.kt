package com.littlehelper.settings

/**
 * 组装 OpenClaw sessionKey：`agent:{agentName}:main`（rev3 统一策略）。
 */
object SessionKeyResolver {

    const val DEFAULT_AGENT_NAME = "main"
    const val DEFAULT_SESSION_ID = "main"

    private val AGENT_NAME_PATTERN = Regex("^[a-zA-Z0-9_]+$")

    fun normalizeAgentName(raw: String): String =
        raw.trim().ifBlank { DEFAULT_AGENT_NAME }

    fun isValidAgentName(name: String): Boolean =
        AGENT_NAME_PATTERN.matches(normalizeAgentName(name))

    fun resolve(agentName: String): String {
        val agent = normalizeAgentName(agentName)
        return "agent:$agent:$DEFAULT_SESSION_ID"
    }

    /** 从 `agent:{name}:main` 解析 agent 名（无法解析时返回 default）。 */
    fun parseAgentNameFromSessionKey(sessionKey: String): String {
        if (!sessionKey.startsWith("agent:")) return DEFAULT_AGENT_NAME
        val agent = sessionKey.removePrefix("agent:").substringBefore(':').trim()
        return agent.ifBlank { DEFAULT_AGENT_NAME }
    }

    /** 设置页摘要，如 `main · main` 或 `laoxia · main`。 */
    fun sessionKeyLabel(agentName: String): String {
        val agent = normalizeAgentName(agentName)
        return "$agent · $DEFAULT_SESSION_ID"
    }
}
