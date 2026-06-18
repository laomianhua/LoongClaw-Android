package com.littlehelper.domain.map

/**
 * 地图查询 TTS/聊天气泡授权策略：默认以 AI 回复为准，SDK 结果仅用于地图绘制。
 */
object MapTtsAuthorization {

    const val CALCULATING_PLACEHOLDER = "[CALCULATING]"

    /** AI 显式授权宿主用高德实时结果替换占位符时，才允许 SDK 改写聊天气泡。 */
    fun isSdkDynamicTtsAuthorized(aiReply: String): Boolean =
        aiReply.contains(CALCULATING_PLACEHOLDER)

    fun mergeSdkResult(aiReply: String, sdkResult: String): String =
        aiReply.replace(CALCULATING_PLACEHOLDER, sdkResult.trim())
}
