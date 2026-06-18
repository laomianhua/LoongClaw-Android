package com.littlehelper.data.map

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.services.geocoder.GeocodeQuery
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.amap.api.services.route.BusRouteResult
import com.amap.api.services.route.DriveRouteResult
import com.amap.api.services.route.RouteSearch
import com.amap.api.services.route.WalkRouteResult
import com.littlehelper.domain.map.IMapService
import com.littlehelper.domain.map.MapAction
import com.littlehelper.domain.map.MapControlQueryType
import com.littlehelper.domain.map.MapExecuteResult
import com.littlehelper.domain.map.MapPoiRelevance
import com.littlehelper.domain.map.MapPoiResult
import com.littlehelper.domain.map.MapInstructionPayload
import com.littlehelper.domain.map.MapLayerType
import com.littlehelper.domain.map.MapOrigin
import com.littlehelper.domain.map.MapQueryType
import com.littlehelper.domain.map.MapRouteMode
import com.littlehelper.domain.map.MapType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * 高德地图 SDK 唯一实现类（项目内唯一允许 import com.amap.* 的文件）。
 */
class AMapServiceImpl : IMapService {

    private var mapView: MapView? = null
    private var aMap: AMap? = null
    private var initialized = false
    private var myLocationActive = false
    private var centeredOnUser = false
    private var lastKnownLat: Double? = null
    private var lastKnownLng: Double? = null
    private var routePolyline: Polyline? = null
    private var autoCenterOnUserEnabled = true

    override fun setAutoCenterOnUserEnabled(enabled: Boolean) {
        if (autoCenterOnUserEnabled == enabled) return
        autoCenterOnUserEnabled = enabled
        centeredOnUser = !enabled
        applyMyLocationStyleIfActive()
    }

    override fun initialize(context: Context, apiKey: String) {
        if (initialized) return

        val appContext = context.applicationContext
        ensurePrivacyCompliance(appContext)

        if (apiKey.isNotBlank()) {
            MapsInitializer.setApiKey(apiKey)
        }

        val view = MapView(context)
        view.onCreate(null)
        mapView = view

        aMap = view.map?.apply {
            mapType = AMap.MAP_TYPE_NORMAL
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isCompassEnabled = true
            uiSettings.isScaleControlsEnabled = true
            uiSettings.isMyLocationButtonEnabled = false
        }

        initialized = true
    }

    override fun getMapView(): View {
        val view = mapView ?: error("MapService not initialized. Call initialize() first.")
        view.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                event.actionMasked == MotionEvent.ACTION_MOVE
            ) {
                v.parent?.requestDisallowInterceptTouchEvent(true)
            }
            false
        }
        return view
    }

    override fun setCenterLocation(latitude: Double, longitude: Double) {
        aMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), DEFAULT_ZOOM)
        )
    }

    override fun switchMapType(type: MapType) {
        aMap?.mapType = when (type) {
            MapType.STANDARD -> AMap.MAP_TYPE_NORMAL
            MapType.SATELLITE -> AMap.MAP_TYPE_SATELLITE
        }
    }

    override suspend fun executeInstruction(
        context: Context,
        action: String?,
        payload: MapInstructionPayload?,
        supplementTts: Boolean
    ): MapExecuteResult? {
        val mapAction = MapAction.fromWire(action) ?: return null
        if (mapAction == MapAction.MAP_CONTROL) {
            return executeMapControl(context, payload ?: MapInstructionPayload())
        }
        if (payload == null) return null
        MapLayerType.fromWire(payload.layerType)?.toMapType()?.let { switchMapType(it) }
        return when (mapAction) {
            MapAction.VIEW_LOCATION -> drawViewLocation(context, payload)
            MapAction.NAVIGATE -> navigate(context, payload, supplementTts)
            MapAction.MAP_CONTROL -> null
        }
    }

    private suspend fun executeMapControl(
        context: Context,
        payload: MapInstructionPayload
    ): MapExecuteResult? {
        return when (MapControlQueryType.fromWire(payload.queryType)) {
            MapControlQueryType.LOCATION -> showCurrentLocation(context)
            MapControlQueryType.CLEAR -> clearMapToInitialState()
            MapControlQueryType.POI_SEARCH -> searchNearbyPoi(
                context,
                payload.keywords?.trim().orEmpty().ifBlank { "美食" }
            )
            null -> null
        }
    }

    private suspend fun showCurrentLocation(context: Context): MapExecuteResult? {
        setAutoCenterOnUserEnabled(true)
        startMyLocation(context)
        val point = resolveOriginPoint(context, MapOrigin.CURRENT_LOCATION) ?: return null
        aMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(point.latitude, point.longitude),
                LOCATION_ZOOM
            )
        )
        return MapExecuteResult(locationAnnouncement = "已经定位到您当前位置")
    }

    private fun clearMapToInitialState(): MapExecuteResult {
        setAutoCenterOnUserEnabled(true)
        clearMapOverlaysForNewQuery()
        return MapExecuteResult(mapCleared = true)
    }

    private suspend fun searchNearbyPoi(context: Context, keywords: String): MapExecuteResult? {
        setAutoCenterOnUserEnabled(true)
        val center = resolveOriginPoint(context, MapOrigin.CURRENT_LOCATION) ?: return null
        val pois = searchNearbyPoiItems(context, keywords, center).take(POI_DISPLAY_LIMIT)
        if (pois.isEmpty()) return null

        clearMapOverlaysForNewQuery()

        val results = pois.mapNotNull { poi ->
            val latLon = poi.latLonPoint ?: return@mapNotNull null
            aMap?.addMarker(
                MarkerOptions()
                    .position(LatLng(latLon.latitude, latLon.longitude))
                    .title(poi.title)
                    .snippet(formatPoiDistance(poi.distance))
            )
            MapPoiResult(
                name = poi.title.orEmpty().ifBlank { "未知地点" },
                distanceMeters = poi.distance,
                latitude = latLon.latitude,
                longitude = latLon.longitude
            )
        }

        if (results.isEmpty()) return null

        val boundsBuilder = com.amap.api.maps.model.LatLngBounds.builder()
        boundsBuilder.include(LatLng(center.latitude, center.longitude))
        results.forEach { r ->
            boundsBuilder.include(LatLng(r.latitude, r.longitude))
        }
        aMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))

        return MapExecuteResult(poiResults = results)
    }

    private suspend fun searchNearbyPoiItems(
        context: Context,
        keyword: String,
        center: LatLonPoint
    ): List<PoiItem> = suspendCancellableCoroutine { cont ->
        try {
            val query = PoiSearch.Query(keyword, "", "")
            query.pageSize = POI_DISPLAY_LIMIT
            query.pageNum = 1
            val search = PoiSearch(context.applicationContext, query)
            search.bound = PoiSearch.SearchBound(center, POI_SEARCH_RADIUS_M)
            search.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                override fun onPoiSearched(result: PoiResult?, code: Int) {
                    val items = if (code == AMapException.CODE_AMAP_SUCCESS) {
                        result?.pois.orEmpty()
                    } else {
                        emptyList()
                    }
                    cont.resume(items)
                }

                override fun onPoiItemSearched(item: PoiItem?, code: Int) = Unit
            })
            search.searchPOIAsyn()
        } catch (_: Exception) {
            cont.resume(emptyList())
        }
    }

    private fun formatPoiDistance(distanceM: Int): String = when {
        distanceM < 1000 -> "${distanceM}米"
        else -> String.format("%.1f公里", distanceM / 1000f)
    }

    override fun startMyLocation(context: Context) {
        val map = aMap ?: return
        if (myLocationActive) {
            applyMyLocationStyleIfActive()
            return
        }

        applyMyLocationStyle(map)
        map.isMyLocationEnabled = true
        map.setOnMyLocationChangeListener { location ->
            if (location != null) {
                lastKnownLat = location.latitude
                lastKnownLng = location.longitude
            }
            if (!autoCenterOnUserEnabled) return@setOnMyLocationChangeListener
            if (!centeredOnUser && location != null) {
                setCenterLocation(location.latitude, location.longitude)
                centeredOnUser = true
            }
        }
        myLocationActive = true
    }

    private fun applyMyLocationStyleIfActive() {
        val map = aMap ?: return
        if (!myLocationActive) return
        applyMyLocationStyle(map)
    }

    /**
     * ROTATE 会让 SDK 持续把镜头跟到 GPS；查看远方 POI 时改用 SHOW，只画蓝点不抢镜头。
     */
    private fun applyMyLocationStyle(map: AMap) {
        val style = MyLocationStyle().apply {
            myLocationType(
                if (autoCenterOnUserEnabled) {
                    MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE
                } else {
                    MyLocationStyle.LOCATION_TYPE_SHOW
                }
            )
            interval(2000)
            showMyLocation(true)
        }
        map.myLocationStyle = style
    }

    override fun stopMyLocation() {
        if (!myLocationActive && !centeredOnUser) return
        aMap?.isMyLocationEnabled = false
        aMap?.setOnMyLocationChangeListener(null)
        myLocationActive = false
        centeredOnUser = false
    }

    override fun onResume() {
        mapView?.onResume()
    }

    override fun onPause() {
        stopMyLocation()
        mapView?.onPause()
    }

    override fun displayPoiMarkers(pois: List<MapPoiResult>, focusZoom: Float): Boolean {
        if (pois.isEmpty()) return false
        val map = aMap ?: return false

        clearMapOverlaysForNewQuery()

        var firstMarker: com.amap.api.maps.model.Marker? = null
        pois.forEach { poi ->
            val latLng = LatLng(poi.latitude, poi.longitude)
            val marker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(poi.name)
                    .snippet(poi.name)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .zIndex(20f)
                    .draggable(false)
            )
            if (firstMarker == null) {
                firstMarker = marker
            }
        }
        firstMarker?.showInfoWindow()

        if (pois.size == 1) {
            val poi = pois.first()
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(poi.latitude, poi.longitude),
                    focusZoom
                )
            )
        }
        return firstMarker != null
    }

    override fun onDestroy() {
        stopMyLocation()
        clearRouteOverlay()
        mapView?.onPause()
        mapView?.onDestroy()
        mapView = null
        aMap = null
        initialized = false
    }

    private suspend fun drawViewLocation(
        context: Context,
        payload: MapInstructionPayload
    ): MapExecuteResult {
        val keyword = payload.keywords?.trim().orEmpty()
        if (keyword.isEmpty()) {
            return viewLocationFailure("这个地方")
        }
        if (aMap == null) {
            return viewLocationFailure(keyword)
        }

        val resolved = resolveViewLocationPoint(context, keyword, payload.city)
            ?: return viewLocationFailure(keyword)

        val (point, displayName) = resolved
        val zoom = payload.zoomLevel?.toFloat() ?: VIEW_LOCATION_ZOOM
        setAutoCenterOnUserEnabled(false)
        val poiResult = MapPoiResult(
            name = displayName,
            distanceMeters = 0,
            latitude = point.latitude,
            longitude = point.longitude
        )

        if (!displayPoiMarkers(listOf(poiResult), zoom)) {
            return viewLocationFailure(keyword)
        }
        return MapExecuteResult(
            poiResults = listOf(poiResult),
            locationAnnouncement = "已经为您在地图上标出$displayName"
        )
    }

    /**
     * VIEW_LOCATION 只信任 POI 命中；仅「路/街/号」类地址才允许地理编码兜底，避免乱编地名被 geocode 误匹配。
     */
    private suspend fun resolveViewLocationPoint(
        context: Context,
        keyword: String,
        city: String?
    ): Pair<LatLonPoint, String>? {
        val poi = searchPoi(context, keyword, city)
        poi?.latLonPoint?.let { point ->
            val name = poi.title?.trim().orEmpty().ifBlank { keyword }
            if (!MapPoiRelevance.isRelevant(keyword, name)) {
                return null
            }
            return point to name
        }
        if (!looksLikeStreetAddress(keyword)) {
            return null
        }
        val geocode = geocodeAddressDetailed(context, keyword, city) ?: return null
        if (!isGeocodeRelevant(keyword, geocode.formatAddress)) {
            return null
        }
        return geocode.point to geocode.formatAddress
    }

    private fun looksLikeStreetAddress(keyword: String): Boolean {
        return keyword.any { it in "路街巷号弄栋村镇乡" }
    }

    private fun isGeocodeRelevant(keyword: String, formatAddress: String): Boolean {
        val key = keyword.filterNot { it.isWhitespace() }
        val addr = formatAddress.filterNot { it.isWhitespace() }
        if (key.length < 2 || addr.isEmpty()) return false
        if (addr.contains(key)) return true
        val probe = key.take((key.length * 0.6).toInt().coerceAtLeast(2))
        return probe.length >= 2 && addr.contains(probe)
    }

    private data class GeocodeHit(
        val point: LatLonPoint,
        val formatAddress: String
    )

    private fun viewLocationFailure(keyword: String): MapExecuteResult {
        return MapExecuteResult(
            failureMessage = "地图上没有找到「$keyword」，请换个说法再试。"
        )
    }

    private fun clearMapOverlaysForNewQuery() {
        val reenableLocation = myLocationActive
        routePolyline = null
        aMap?.clear()
        if (reenableLocation) {
            aMap?.isMyLocationEnabled = true
        }
    }

    private suspend fun navigate(
        context: Context,
        payload: MapInstructionPayload,
        supplementTts: Boolean
    ): MapExecuteResult? {
        val destination = payload.destination?.trim().orEmpty()
        if (destination.isEmpty()) return null

        val destPoint = resolveTextToPoint(context, destination, payload.city) ?: return null
        val originPoint = resolveOriginPoint(context, payload.origin) ?: return null

        clearMapRouteLayers()

        val routeMode = MapRouteMode.fromWire(payload.mode)
        val queryType = MapQueryType.fromWire(payload.queryType)

        return when (routeMode) {
            MapRouteMode.TRANSIT ->
                planBusRoute(context, originPoint, destPoint, payload.city, supplementTts, queryType)
            MapRouteMode.WALKING ->
                planWalkRoute(context, originPoint, destPoint, supplementTts, queryType)
            MapRouteMode.BICYCLING ->
                planRideRoute(context, originPoint, destPoint, supplementTts, queryType)
            MapRouteMode.DRIVING ->
                planDriveRoute(context, originPoint, destPoint, supplementTts, queryType)
        }
    }

    private suspend fun resolveOriginPoint(context: Context, origin: String?): LatLonPoint? {
        if (!origin.isNullOrBlank() &&
            !origin.equals(MapOrigin.CURRENT_LOCATION, ignoreCase = true)
        ) {
            return resolveTextToPoint(context, origin, null)
        }
        lastKnownLat?.let { lat ->
            lastKnownLng?.let { lng -> return LatLonPoint(lat, lng) }
        }
        return fetchCurrentLocation(context)
    }

    private suspend fun fetchCurrentLocation(context: Context): LatLonPoint? =
        suspendCancellableCoroutine { cont ->
            val client = AMapLocationClient(context.applicationContext)
            val option = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isOnceLocation = true
                isNeedAddress = false
            }
            client.setLocationOption(option)
            client.setLocationListener { location: AMapLocation? ->
                client.onDestroy()
                if (location != null && location.errorCode == 0) {
                    lastKnownLat = location.latitude
                    lastKnownLng = location.longitude
                    cont.resume(LatLonPoint(location.latitude, location.longitude))
                } else {
                    cont.resume(null)
                }
            }
            client.startLocation()
            cont.invokeOnCancellation { client.onDestroy() }
        }

    /** PoiSearch 优先，失败则 GeocodeSearch（与高德 SDK 原子能力对齐）。 */
    private suspend fun resolveTextToPoint(
        context: Context,
        address: String,
        city: String?
    ): LatLonPoint? {
        searchPoi(context, address, city)?.latLonPoint?.let { return it }
        return geocodeAddress(context, address, city)
    }

    private suspend fun geocodeAddress(
        context: Context,
        address: String,
        city: String?
    ): LatLonPoint? = geocodeAddressDetailed(context, address, city)?.point

    private suspend fun geocodeAddressDetailed(
        context: Context,
        address: String,
        city: String?
    ): GeocodeHit? = suspendCancellableCoroutine { cont ->
        try {
            val geocoder = GeocodeSearch(context.applicationContext)
            geocoder.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                override fun onGeocodeSearched(result: GeocodeResult?, code: Int) {
                    val hit = if (code == AMapException.CODE_AMAP_SUCCESS) {
                        result?.geocodeAddressList?.firstOrNull()?.let { item ->
                            item.latLonPoint?.let { point ->
                                GeocodeHit(
                                    point = point,
                                    formatAddress = item.formatAddress.orEmpty()
                                )
                            }
                        }
                    } else {
                        null
                    }
                    cont.resume(hit)
                }

                override fun onRegeocodeSearched(result: RegeocodeResult?, code: Int) = Unit
            })
            geocoder.getFromLocationNameAsyn(GeocodeQuery(address, city?.trim().orEmpty()))
        } catch (_: Exception) {
            cont.resume(null)
        }
    }

    private suspend fun searchPoi(context: Context, keyword: String, city: String?): PoiItem? =
        suspendCancellableCoroutine { cont ->
            try {
                val query = PoiSearch.Query(keyword, "", city?.trim().orEmpty())
                query.pageSize = 1
                val search = PoiSearch(context.applicationContext, query)
                search.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                    override fun onPoiSearched(result: PoiResult?, code: Int) {
                        val poi = if (code == AMapException.CODE_AMAP_SUCCESS) {
                            result?.pois?.firstOrNull()
                        } else {
                            null
                        }
                        cont.resume(poi)
                    }

                    override fun onPoiItemSearched(item: PoiItem?, code: Int) = Unit
                })
                search.searchPOIAsyn()
            } catch (_: Exception) {
                cont.resume(null)
            }
        }

    private suspend fun planDriveRoute(
        context: Context,
        origin: LatLonPoint,
        dest: LatLonPoint,
        supplementTts: Boolean,
        queryType: MapQueryType
    ): MapExecuteResult? {
        val result = suspendCancellableCoroutine { cont ->
            try {
                val routeSearch = RouteSearch(context.applicationContext)
                routeSearch.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
                    override fun onDriveRouteSearched(result: DriveRouteResult?, code: Int) {
                        cont.resume(if (code == AMapException.CODE_AMAP_SUCCESS) result else null)
                    }

                    override fun onWalkRouteSearched(result: WalkRouteResult?, code: Int) = Unit
                    override fun onBusRouteSearched(
                        result: com.amap.api.services.route.BusRouteResult?,
                        code: Int
                    ) = Unit

                    override fun onRideRouteSearched(
                        result: com.amap.api.services.route.RideRouteResult?,
                        code: Int
                    ) = Unit
                })
                val fromAndTo = RouteSearch.FromAndTo(origin, dest)
                val query = RouteSearch.DriveRouteQuery(
                    fromAndTo,
                    RouteSearch.DRIVING_SINGLE_DEFAULT,
                    null,
                    null,
                    ""
                )
                routeSearch.calculateDriveRouteAsyn(query)
            } catch (_: Exception) {
                cont.resume(null)
            }
        }

        val path = result?.paths?.firstOrNull()
        if (path != null) {
            val polyline = path.polyline
            if (polyline.isNotEmpty()) {
                drawRoutePolyline(polyline)
            } else {
                aMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(dest.latitude, dest.longitude), DEFAULT_ZOOM)
                )
            }
        } else {
            aMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(dest.latitude, dest.longitude), DEFAULT_ZOOM)
            )
        }
        return buildRouteResult(
            supplementTts = supplementTts,
            durationSec = path?.duration,
            distanceM = path?.distance,
            queryType = queryType,
            mode = MapRouteMode.DRIVING
        )
    }

    private suspend fun planBusRoute(
        context: Context,
        origin: LatLonPoint,
        dest: LatLonPoint,
        city: String?,
        supplementTts: Boolean,
        queryType: MapQueryType
    ): MapExecuteResult? {
        val cityParam = city?.trim().takeIf { !it.isNullOrEmpty() } ?: ""
        val result = suspendCancellableCoroutine { cont ->
            try {
                val routeSearch = RouteSearch(context.applicationContext)
                routeSearch.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
                    override fun onBusRouteSearched(result: BusRouteResult?, code: Int) {
                        cont.resume(if (code == AMapException.CODE_AMAP_SUCCESS) result else null)
                    }

                    override fun onDriveRouteSearched(result: DriveRouteResult?, code: Int) = Unit
                    override fun onWalkRouteSearched(result: WalkRouteResult?, code: Int) = Unit
                    override fun onRideRouteSearched(
                        result: com.amap.api.services.route.RideRouteResult?,
                        code: Int
                    ) = Unit
                })
                val fromAndTo = RouteSearch.FromAndTo(origin, dest)
                val query = RouteSearch.BusRouteQuery(fromAndTo, RouteSearch.BUS_DEFAULT, cityParam, 0)
                routeSearch.calculateBusRouteAsyn(query)
            } catch (_: Exception) {
                cont.resume(null)
            }
        }

        val path = result?.paths?.firstOrNull()
        val polyline = path?.let { extractBusPolylines(it) }.orEmpty()
        if (polyline.isNotEmpty()) {
            drawRoutePolyline(polyline)
        } else {
            aMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(dest.latitude, dest.longitude), DEFAULT_ZOOM)
            )
        }
        val transitDetail = if (queryType == MapQueryType.ROUTE_DETAIL && path != null) {
            BusTransitDetailExtractor.extract(path).takeIf { it.isNotEmpty() }
        } else {
            null
        }
        return buildRouteResult(
            supplementTts = supplementTts,
            durationSec = path?.duration,
            distanceM = path?.distance,
            queryType = queryType,
            mode = MapRouteMode.TRANSIT,
            transitDetail = transitDetail
        )
    }

    private suspend fun planRideRoute(
        context: Context,
        origin: LatLonPoint,
        dest: LatLonPoint,
        supplementTts: Boolean,
        queryType: MapQueryType
    ): MapExecuteResult? {
        val result = suspendCancellableCoroutine { cont ->
            try {
                val routeSearch = RouteSearch(context.applicationContext)
                routeSearch.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
                    override fun onRideRouteSearched(
                        result: com.amap.api.services.route.RideRouteResult?,
                        code: Int
                    ) {
                        cont.resume(if (code == AMapException.CODE_AMAP_SUCCESS) result else null)
                    }

                    override fun onDriveRouteSearched(result: DriveRouteResult?, code: Int) = Unit
                    override fun onWalkRouteSearched(result: WalkRouteResult?, code: Int) = Unit
                    override fun onBusRouteSearched(result: BusRouteResult?, code: Int) = Unit
                })
                val fromAndTo = RouteSearch.FromAndTo(origin, dest)
                val query = RouteSearch.RideRouteQuery(fromAndTo, RouteSearch.RIDING_DEFAULT)
                routeSearch.calculateRideRouteAsyn(query)
            } catch (_: Exception) {
                cont.resume(null)
            }
        }

        val path = result?.paths?.firstOrNull()
        path?.polyline?.takeIf { it.isNotEmpty() }?.let { drawRoutePolyline(it) }
            ?: aMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(dest.latitude, dest.longitude), DEFAULT_ZOOM)
            )
        return buildRouteResult(
            supplementTts = supplementTts,
            durationSec = path?.duration,
            distanceM = path?.distance,
            queryType = queryType,
            mode = MapRouteMode.BICYCLING
        )
    }

    private fun extractBusPolylines(path: com.amap.api.services.route.BusPath): List<LatLonPoint> {
        val points = mutableListOf<LatLonPoint>()
        path.steps.orEmpty().forEach { step ->
            step.walk?.steps.orEmpty().forEach { walkStep ->
                walkStep.polyline.orEmpty().forEach { appendRoutePoint(points, it) }
            }
            val linePolyline = step.busLines?.firstOrNull()?.polyline
                ?: step.busLine?.polyline
            linePolyline.orEmpty().forEach { appendRoutePoint(points, it) }
        }
        return points
    }

    private fun appendRoutePoint(points: MutableList<LatLonPoint>, point: LatLonPoint) {
        val last = points.lastOrNull()
        if (last != null &&
            last.latitude == point.latitude &&
            last.longitude == point.longitude
        ) {
            return
        }
        points.add(point)
    }

    private suspend fun planWalkRoute(
        context: Context,
        origin: LatLonPoint,
        dest: LatLonPoint,
        supplementTts: Boolean,
        queryType: MapQueryType
    ): MapExecuteResult? {
        val result = suspendCancellableCoroutine { cont ->
            try {
                val routeSearch = RouteSearch(context.applicationContext)
                routeSearch.setRouteSearchListener(object : RouteSearch.OnRouteSearchListener {
                    override fun onWalkRouteSearched(result: WalkRouteResult?, code: Int) {
                        cont.resume(if (code == AMapException.CODE_AMAP_SUCCESS) result else null)
                    }

                    override fun onDriveRouteSearched(result: DriveRouteResult?, code: Int) = Unit
                    override fun onBusRouteSearched(
                        result: com.amap.api.services.route.BusRouteResult?,
                        code: Int
                    ) = Unit

                    override fun onRideRouteSearched(
                        result: com.amap.api.services.route.RideRouteResult?,
                        code: Int
                    ) = Unit
                })
                val fromAndTo = RouteSearch.FromAndTo(origin, dest)
                val query = RouteSearch.WalkRouteQuery(fromAndTo, RouteSearch.WALK_DEFAULT)
                routeSearch.calculateWalkRouteAsyn(query)
            } catch (_: Exception) {
                cont.resume(null)
            }
        }

        val path = result?.paths?.firstOrNull()
        path?.polyline?.takeIf { it.isNotEmpty() }?.let { drawRoutePolyline(it) }
            ?: aMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(dest.latitude, dest.longitude), DEFAULT_ZOOM)
            )
        return buildRouteResult(
            supplementTts = supplementTts,
            durationSec = path?.duration,
            distanceM = path?.distance,
            queryType = queryType,
            mode = MapRouteMode.WALKING
        )
    }

    private fun buildRouteResult(
        supplementTts: Boolean,
        durationSec: Number?,
        distanceM: Number?,
        queryType: MapQueryType,
        mode: MapRouteMode,
        transitDetail: String? = null
    ): MapExecuteResult? {
        val supplement = if (supplementTts) {
            formatCalculatingHint(durationSec, distanceM, queryType)
        } else {
            null
        }
        val detail = transitDetail?.trim()?.takeIf { it.isNotEmpty() }
        val durationAnnouncement = formatDurationAnnouncement(mode, durationSec)
        if (supplement == null && detail == null && durationAnnouncement == null) return null
        return MapExecuteResult(
            supplementText = supplement,
            durationAnnouncement = durationAnnouncement,
            transitDetail = detail
        )
    }

    private fun formatDurationAnnouncement(mode: MapRouteMode, durationSec: Number?): String? {
        val seconds = durationSec?.toFloat() ?: return null
        if (seconds <= 0f) return null
        val minutes = (seconds / 60f).roundToInt().coerceAtLeast(1)
        return when (mode) {
            MapRouteMode.DRIVING -> "当前开车大约需要 $minutes 分钟"
            MapRouteMode.TRANSIT -> "当前乘坐公共交通大约需要 $minutes 分钟"
            MapRouteMode.WALKING -> "当前步行大约需要 $minutes 分钟"
            MapRouteMode.BICYCLING -> "当前骑行大约需要 $minutes 分钟"
        }
    }

    /** 仅填充 `[CALCULATING]` 占位符，不含目的地/模式等语义（由 AI 话术承载）。 */
    private fun formatCalculatingHint(
        durationSec: Number?,
        distanceM: Number?,
        queryType: MapQueryType
    ): String {
        return when (queryType) {
            MapQueryType.DISTANCE -> {
                val km = (distanceM?.toFloat() ?: 0f) / 1000f
                "${km.roundToInt()} 公里"
            }
            MapQueryType.ROUTE_PLAN, MapQueryType.ROUTE_DETAIL -> "路线已规划"
            MapQueryType.DURATION -> {
                val minutes = ((durationSec?.toFloat() ?: 0f) / 60f).roundToInt().coerceAtLeast(1)
                "$minutes 分钟"
            }
        }
    }

    private fun clearMapRouteLayers() {
        clearMapOverlaysForNewQuery()
    }

    private fun clearRouteOverlay() {
        routePolyline?.remove()
        routePolyline = null
    }

    private fun drawRoutePolyline(points: List<LatLonPoint>) {
        clearMapRouteLayers()
        if (points.isEmpty()) return
        val latLngs = points.map { LatLng(it.latitude, it.longitude) }
        routePolyline = aMap?.addPolyline(
            PolylineOptions()
                .addAll(latLngs)
                .width(16f)
                .color(Color.parseColor("#1A73E8"))
        )
        drawRouteBounds(points)
    }

    private fun drawRouteBounds(points: List<LatLonPoint>) {
        if (points.isEmpty()) return
        val boundsBuilder = com.amap.api.maps.model.LatLngBounds.builder()
        points.forEach { p -> boundsBuilder.include(LatLng(p.latitude, p.longitude)) }
        aMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80))
    }

    companion object {
        private const val DEFAULT_ZOOM = 16f
        private const val VIEW_LOCATION_ZOOM = 16f
        private const val LOCATION_ZOOM = 16.5f
        private const val POI_DISPLAY_LIMIT = 3
        private const val POI_SEARCH_RADIUS_M = 3000

        @Volatile
        private var privacyConfigured = false

        private fun ensurePrivacyCompliance(context: Context) {
            if (privacyConfigured) return
            synchronized(AMapServiceImpl::class.java) {
                if (privacyConfigured) return
                MapsInitializer.updatePrivacyShow(context, true, true)
                MapsInitializer.updatePrivacyAgree(context, true)
                AMapLocationClient.updatePrivacyShow(context, true, true)
                AMapLocationClient.updatePrivacyAgree(context, true)
                ServiceSettings.updatePrivacyShow(context, true, true)
                ServiceSettings.updatePrivacyAgree(context, true)
                privacyConfigured = true
            }
        }
    }
}
