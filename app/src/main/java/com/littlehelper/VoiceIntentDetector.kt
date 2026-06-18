package com.littlehelper

/**
 * 语音动作路由：仅读取 UI 层已建立的 [FollowUpContext] 协议状态。
 * 不对 ASR 文本、不对助手/用户话术做汉字意图猜测；语义由云端 DB_OPS JSON 负责。
 */
object VoiceIntentDetector {

    fun detect(followUpContext: FollowUpContext = FollowUpContext.NONE): VoiceAction =
        when (followUpContext) {
            FollowUpContext.QUERY -> VoiceAction.QUERY
            FollowUpContext.DELETE,
            FollowUpContext.SAVE,
            FollowUpContext.TODO_DISAMBIGUATION,
            FollowUpContext.NONE -> VoiceAction.SAVE
        }
}
