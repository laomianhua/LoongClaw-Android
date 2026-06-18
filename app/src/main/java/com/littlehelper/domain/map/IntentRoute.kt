package com.littlehelper.domain.map

/** DB_OPS 顶层意图路由枚举。 */
enum class IntentRoute(val wireValue: String) {
    MEMO("MEMO"),
    NOTEBOOK("NOTEBOOK"),
    MAP("MAP"),
    WEATHER("WEATHER"),
    STOCK("STOCK");

    companion object {
        fun fromWire(value: String?): IntentRoute? =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }
    }
}

enum class MapAction(val wireValue: String) {
    VIEW_LOCATION("VIEW_LOCATION"),
    NAVIGATE("NAVIGATE"),
    /** 地图控制：定位 / 清图 / 周边 POI（query_type 见 payload） */
    MAP_CONTROL("MAP_CONTROL");

    companion object {
        fun fromWire(value: String?): MapAction? =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }
    }
}
