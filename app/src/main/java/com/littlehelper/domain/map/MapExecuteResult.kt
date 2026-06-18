package com.littlehelper.domain.map

/**
 * [IMapService.executeInstruction] 执行结果。
 * - [supplementText]：`[CALCULATING]` 占位符替换片段
 * - [durationAnnouncement]：高德实时算路后的完整播报句（如「当前开车大约需要 X 分钟」）
 * - [transitDetail]：`ROUTE_DETAIL` 时作为永久助手消息追加至聊天区
 */
data class MapExecuteResult(
    val supplementText: String? = null,
    val durationAnnouncement: String? = null,
    val transitDetail: String? = null,
    /** MAP_CONTROL / POI_SEARCH：地图抽屉大字列表 + 大头针 */
    val poiResults: List<MapPoiResult>? = null,
    /** MAP_CONTROL / CLEAR：宿主应清除聊天区临时路径/换乘文本 */
    val mapCleared: Boolean = false,
    /** MAP_CONTROL / LOCATION：可选 TTS */
    val locationAnnouncement: String? = null,
    /** VIEW_LOCATION / POI 解析失败：宿主追加聊天气泡并 TTS */
    val failureMessage: String? = null
)
