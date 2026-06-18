package com.littlehelper.domain.map

/** 周边 POI 检索结果（适老化地图抽屉展示，最多 3 条）。 */
data class MapPoiResult(
    val name: String,
    val distanceMeters: Int,
    val latitude: Double,
    val longitude: Double
)
