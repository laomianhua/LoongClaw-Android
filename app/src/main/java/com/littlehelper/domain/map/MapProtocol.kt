package com.littlehelper.domain.map

/**
 * MAP payload 法定枚举，与高德 RouteSearch / GeocodeSearch / MapType 原子能力对齐。
 * 云端 JSON 必须使用 [wireValue]；App 仅做 fromWire 解析与 SDK 映射。
 */

/** 路径规划模式 → RouteSearch.calculate*RouteAsyn */
enum class MapRouteMode(val wireValue: String) {
    DRIVING("DRIVING"),
    WALKING("WALKING"),
    BICYCLING("BICYCLING"),
    TRANSIT("TRANSIT");

    companion object {
        fun fromWire(value: String?): MapRouteMode =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }
                ?: DRIVING
    }
}

/** 用户关心维度；App 仅用于 [CALCULATING] 占位符补数，默认不写聊天气泡。 */
enum class MapQueryType(val wireValue: String) {
    DURATION("DURATION"),
    DISTANCE("DISTANCE"),
    ROUTE_PLAN("ROUTE_PLAN"),
    /** 公交/地铁换乘详情：端侧从高德 BusRouteResult 提取并在地图抽屉展示 */
    ROUTE_DETAIL("ROUTE_DETAIL");

    companion object {
        fun fromWire(value: String?): MapQueryType =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }
                ?: DURATION
    }
}

/** [MapAction.MAP_CONTROL] 专用 query_type；与路径规划 query_type 互斥，由 action 字段区分。 */
enum class MapControlQueryType(val wireValue: String) {
    LOCATION("LOCATION"),
    CLEAR("CLEAR"),
    POI_SEARCH("POI_SEARCH");

    companion object {
        fun fromWire(value: String?): MapControlQueryType? =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }
    }
}

/** 地图图层 → AMap.mapType */
enum class MapLayerType(val wireValue: String) {
    STANDARD("STANDARD"),
    SATELLITE("SATELLITE");

    fun toMapType(): MapType = when (this) {
        STANDARD -> MapType.STANDARD
        SATELLITE -> MapType.SATELLITE
    }

    companion object {
        fun fromWire(value: String?): MapLayerType? =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }
    }
}

/** payload.origin 法定常量 */
object MapOrigin {
    const val CURRENT_LOCATION = "CURRENT_LOCATION"
}
