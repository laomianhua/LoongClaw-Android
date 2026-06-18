package com.littlehelper.presentation.mapview



import android.Manifest

import android.content.pm.PackageManager

import android.view.ViewGroup

import android.widget.Toast

import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.Text

import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable

import androidx.compose.runtime.DisposableEffect

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import androidx.compose.ui.viewinterop.AndroidView

import androidx.core.content.ContextCompat

import androidx.lifecycle.Lifecycle

import androidx.lifecycle.compose.LocalLifecycleOwner

import androidx.lifecycle.LifecycleEventObserver

import com.littlehelper.BuildConfig

import com.littlehelper.domain.map.IMapService

import com.littlehelper.domain.map.MapPoiResult
import com.littlehelper.domain.map.MapType

import com.littlehelper.presentation.stack.DrawerCard



@Composable

fun MapCard(

    currentCard: DrawerCard,

    mapService: IMapService,

    poiResults: List<MapPoiResult> = emptyList(),

    modifier: Modifier = Modifier

) {

    val context = LocalContext.current

    var mapType by remember { mutableStateOf(MapType.STANDARD) }

    val lifecycleOwner = LocalLifecycleOwner.current

    val isMapVisible = currentCard == DrawerCard.MAP
    val viewingNamedPlace = poiResults.isNotEmpty() && poiResults.all { it.distanceMeters == 0 }

    var hasLocationPermission by remember {

        mutableStateOf(isLocationGranted(context))

    }



    val locationPermissionLauncher = rememberLauncherForActivityResult(

        ActivityResultContracts.RequestMultiplePermissions()

    ) { results ->

        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||

            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        hasLocationPermission = granted

        if (!granted) {

            Toast.makeText(

                context,

                "未授予定位权限，地图将不显示您的当前位置",

                Toast.LENGTH_LONG

            ).show()

        }

    }



    LaunchedEffect(isMapVisible) {

        if (isMapVisible && !hasLocationPermission) {

            locationPermissionLauncher.launch(

                arrayOf(

                    Manifest.permission.ACCESS_FINE_LOCATION,

                    Manifest.permission.ACCESS_COARSE_LOCATION

                )

            )

        }

    }



    DisposableEffect(isMapVisible, hasLocationPermission, mapService, viewingNamedPlace) {

        if (isMapVisible) {
            mapService.setAutoCenterOnUserEnabled(!viewingNamedPlace)
            mapService.onResume()

            if (hasLocationPermission) {

                mapService.startMyLocation(context)

            }

        } else {

            mapService.stopMyLocation()

            mapService.onPause()

        }

        onDispose {

            mapService.stopMyLocation()

            if (!isMapVisible) {

                mapService.onPause()

            }

        }

    }



    DisposableEffect(lifecycleOwner, mapService, isMapVisible, viewingNamedPlace, hasLocationPermission) {

        val observer = LifecycleEventObserver { _, event ->

            if (!isMapVisible) return@LifecycleEventObserver

            when (event) {

                Lifecycle.Event.ON_RESUME -> {
                    mapService.setAutoCenterOnUserEnabled(!viewingNamedPlace)
                    mapService.onResume()

                    if (hasLocationPermission) {

                        mapService.startMyLocation(context)

                    }

                }

                Lifecycle.Event.ON_PAUSE -> {

                    mapService.stopMyLocation()

                    mapService.onPause()

                }

                else -> Unit

            }

        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {

            lifecycleOwner.lifecycle.removeObserver(observer)

        }

    }



    LaunchedEffect(isMapVisible, poiResults, viewingNamedPlace) {
        mapService.setAutoCenterOnUserEnabled(!viewingNamedPlace)
        if (!isMapVisible || poiResults.isEmpty()) return@LaunchedEffect
        mapService.displayPoiMarkers(poiResults)
    }

    val showNearbyPanel = poiResults.any { it.distanceMeters > 0 }

    Box(modifier = modifier.fillMaxSize()) {

        AndroidView(

            factory = { activityContext ->

                mapService.initialize(activityContext, BuildConfig.AMAP_API_KEY)

                val mapView = mapService.getMapView()

                (mapView.parent as? ViewGroup)?.removeView(mapView)

                mapView

            },

            update = { /* MapView 单例复用，无需重复初始化 */ },

            onRelease = {

                mapService.stopMyLocation()

                if (isMapVisible) {

                    mapService.onPause()

                }

            },

            modifier = Modifier.fillMaxSize()

        )



        Row(

            modifier = Modifier

                .fillMaxWidth()

                .padding(12.dp),

            horizontalArrangement = Arrangement.End

        ) {

            MapTypeChip(

                label = "标准",

                selected = mapType == MapType.STANDARD,

                onClick = {

                    mapType = MapType.STANDARD

                    mapService.switchMapType(MapType.STANDARD)

                }

            )

            MapTypeChip(

                label = "卫星",

                selected = mapType == MapType.SATELLITE,

                onClick = {

                    mapType = MapType.SATELLITE

                    mapService.switchMapType(MapType.SATELLITE)

                },

                modifier = Modifier.padding(start = 8.dp)

            )

        }

        if (showNearbyPanel) {
            NearbyPoiPanel(
                poiResults = poiResults,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 124.dp)
            )
        }

    }

}



@Composable
private fun NearbyPoiPanel(
    poiResults: List<MapPoiResult>,
    modifier: Modifier = Modifier
) {
    val isNearbySearch = poiResults.any { it.distanceMeters > 0 }
    val panelTitle = if (isNearbySearch) "附近推荐" else "已找到地点"

    Column(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 16.dp)
            .background(
                color = Color(0xFF1565C0).copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = panelTitle,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.heightIn(max = 180.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(poiResults, key = { "${it.name}:${it.latitude}:${it.longitude}" }) { poi ->
                Text(
                    text = if (poi.distanceMeters > 0) {
                        "${poi.name} · ${formatPoiDistanceLabel(poi.distanceMeters)}"
                    } else {
                        poi.name
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 28.sp
                )
            }
        }
    }
}

private fun formatPoiDistanceLabel(distanceM: Int): String = when {
    distanceM < 1000 -> "${distanceM}米"
    else -> String.format("%.1f公里", distanceM / 1000f)
}



private fun isLocationGranted(context: android.content.Context): Boolean {

    val fine = ContextCompat.checkSelfPermission(

        context,

        Manifest.permission.ACCESS_FINE_LOCATION

    ) == PackageManager.PERMISSION_GRANTED

    val coarse = ContextCompat.checkSelfPermission(

        context,

        Manifest.permission.ACCESS_COARSE_LOCATION

    ) == PackageManager.PERMISSION_GRANTED

    return fine || coarse

}



@Composable

private fun MapTypeChip(

    label: String,

    selected: Boolean,

    onClick: () -> Unit,

    modifier: Modifier = Modifier

) {

    TextButton(

        onClick = onClick,

        modifier = modifier

            .background(

                color = if (selected) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.7f),

                shape = RoundedCornerShape(16.dp)

            )

    ) {

        Text(

            text = label,

            fontSize = 14.sp,

            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,

            color = Color(0xFF212121)

        )

    }

}


