package com.littlehelper.domain.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapProtocolTest {

    @Test
    fun mapRouteMode_fromWire() {
        assertEquals(MapRouteMode.TRANSIT, MapRouteMode.fromWire("TRANSIT"))
        assertEquals(MapRouteMode.WALKING, MapRouteMode.fromWire("WALKING"))
        assertEquals(MapRouteMode.DRIVING, MapRouteMode.fromWire(null))
    }

    @Test
    fun mapQueryType_fromWire() {
        assertEquals(MapQueryType.DISTANCE, MapQueryType.fromWire("DISTANCE"))
        assertEquals(MapQueryType.ROUTE_DETAIL, MapQueryType.fromWire("ROUTE_DETAIL"))
        assertEquals(MapQueryType.DURATION, MapQueryType.fromWire(null))
    }

    @Test
    fun mapControlQueryType_fromWire() {
        assertEquals(MapControlQueryType.LOCATION, MapControlQueryType.fromWire("LOCATION"))
        assertEquals(MapControlQueryType.CLEAR, MapControlQueryType.fromWire("CLEAR"))
        assertEquals(MapControlQueryType.POI_SEARCH, MapControlQueryType.fromWire("POI_SEARCH"))
    }

    @Test
    fun mapAction_includesMapControl() {
        assertEquals(MapAction.MAP_CONTROL, MapAction.fromWire("MAP_CONTROL"))
    }

    @Test
    fun mapLayerType_toMapType() {
        assertEquals(MapType.SATELLITE, MapLayerType.SATELLITE.toMapType())
        assertEquals(MapType.STANDARD, MapLayerType.fromWire("STANDARD")?.toMapType())
    }
}
