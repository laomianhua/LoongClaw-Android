package com.littlehelper.domain.map

import android.content.Context
import android.view.View

/**
 * 地图服务抽象接口。
 * 主程序仅依赖此接口；具体 SDK 实现见 [com.littlehelper.data.map.AMapServiceImpl]。
 */
interface IMapService {
    fun initialize(context: Context, apiKey: String)

    fun getMapView(): View

    fun setCenterLocation(latitude: Double, longitude: Double)

    fun switchMapType(type: MapType)

    /**
     * 执行地图 AI 指令（VIEW_LOCATION / NAVIGATE / MAP_CONTROL）。
     * 仅负责地图图层绘制；返回的耗时/距离文案**默认不得**写入聊天气泡。
     * 仅当 AI 回复含 `[CALCULATING]` 占位符时，宿主才用返回值替换占位符并播报。
     */
    suspend fun executeInstruction(
        context: Context,
        action: String?,
        payload: com.littlehelper.domain.map.MapInstructionPayload?,
        supplementTts: Boolean = false
    ): MapExecuteResult?

    /** 开启「我的位置」蓝点与定位跟踪（需已授予定位权限）。 */
    fun startMyLocation(context: Context)

    /** 关闭定位蓝点并释放定位监听。 */
    fun stopMyLocation()

    /** 地图卡片进入前台时调用（对应 MapView.onResume）。 */
    fun onResume()

    /** 地图卡片进入后台时调用（对应 MapView.onPause，省电）。 */
    fun onPause()

    /** 关闭后，定位蓝点不再自动把镜头拉回用户位置（查看指定地点时保持 POI 居中）。 */
    fun setAutoCenterOnUserEnabled(enabled: Boolean)

    /**
     * 地图卡片可见时，根据已解析 POI 重绘大头针（解决 MapView 未 attach 时标注丢失）。
     * @return 是否成功添加至少一个大头针
     */
    fun displayPoiMarkers(pois: List<MapPoiResult>, focusZoom: Float = 16f): Boolean

    fun onDestroy()
}
