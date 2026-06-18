package com.littlehelper.domain.map

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * `intent_route == "MAP"` 时 payload 原子字段契约（云端 ↔ App ↔ 高德 SDK）。
 *
 * | Key | SDK 映射 |
 * |-----|----------|
 * | origin | `"CURRENT_LOCATION"` → 蓝点 GPS；否则 GeocodeSearch/PoiSearch |
 * | destination | PoiSearch → GeocodeSearch → LatLonPoint |
 * | mode | [MapRouteMode] → RouteSearch.calculate*RouteAsyn |
 * | query_type | [MapQueryType]；`ROUTE_DETAIL` 时换乘详情追加至聊天区 |
 * | layer_type | [MapLayerType] → AMap.mapType |
 * | keywords | VIEW_LOCATION：PoiSearch/GeocodeSearch |
 * | city | GeocodeSearch/PoiSearch 城市限定 |
 * | zoom_level | VIEW_LOCATION 镜头缩放 |
 */
@Keep
data class MapInstructionPayload(
    val keywords: String? = null,
    val city: String? = null,
    @SerializedName(value = "zoom_level", alternate = ["zoomLevel"])
    val zoomLevel: Int? = null,
    val origin: String? = null,
    val destination: String? = null,
    /** [MapRouteMode.wireValue] */
    val mode: String? = null,
    @SerializedName("query_type")
    val queryType: String? = null,
    @SerializedName("layer_type")
    val layerType: String? = null
)
