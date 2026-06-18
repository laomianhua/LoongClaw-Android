package com.littlehelper.data.map

import com.littlehelper.domain.map.IMapService

/** 创建地图服务实例；UI 层仅依赖 [IMapService] 接口。 */
object MapServiceFactory {
    fun create(): IMapService = AMapServiceImpl()
}
