package com.littlehelper.shell.model

/**
 * Gateway 聚合响应（通常由 `intent.final` 事件携带完整版）。
 * 流式阶段可仅有 [textContent]；意图预加载由 [ClawIntentPreload] 先行下发。
 */
data class ClawSessionResponse(
    val sessionId: String,
    val turnId: String,
    val textContent: String,
    val intent: ClawIntent? = null
)

/** 意图预加载：在文本流式完成前抢先切换模块槽位与抽屉状态（时序防抖）。 */
data class ClawIntentPreload(
    val sessionId: String,
    val turnId: String,
    val targetModule: ModuleId,
    val drawerState: PanelCommand? = null
)

data class ClawIntent(
    val action: String,
    val targetModule: ModuleId,
    val drawerState: PanelCommand?,
    val payload: ModulePayload
)
