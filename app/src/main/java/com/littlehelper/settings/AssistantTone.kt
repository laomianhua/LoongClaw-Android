package com.littlehelper.settings

/**
 * 用户可选的助手语气；映射为 Gateway `sessions.send` 的隐身 `systemText`。
 */
enum class AssistantTone(
    val wire: String,
    val label: String,
    val subtitle: String
) {
    FRIEND(
        wire = "friend",
        label = "像朋友",
        subtitle = "温暖口语，像身边朋友帮忙"
    ),
    PROFESSIONAL(
        wire = "professional",
        label = "专业助手",
        subtitle = "礼貌清晰，少说术语"
    ),
    CONCISE(
        wire = "concise",
        label = "简洁高效",
        subtitle = "短句直说，不啰嗦"
    );

    fun systemText(): String = when (this) {
        FRIEND -> """
            你正在通过手机 App 服务一位普通用户（日常消费者，不是工程师或开发者）。
            请像可信赖的朋友兼生活助手那样说话：温暖、自然、有共情，句子不要太长。
            不要使用技术术语、架构名词、调试信息或「作为 AI」式套话。
            直接回应用户刚才说的话，帮他把事情办明白。
        """.trimIndent().replace("\n", " ")

        PROFESSIONAL -> """
            你正在服务一位普通手机用户。请用专业但平易的生活助理语气：礼貌、清楚、有条理。
            避免技术黑话和冗长列表，优先给出可执行的建议。
        """.trimIndent().replace("\n", " ")

        CONCISE -> """
            你正在服务一位普通手机用户。请极简回答：能一句话说清就不要两句。
            不要技术术语，不要寒暄废话，保留必要信息即可。
        """.trimIndent().replace("\n", " ")
    }

    companion object {
        fun fromWire(value: String?): AssistantTone =
            entries.firstOrNull { it.wire == value } ?: FRIEND
    }
}
