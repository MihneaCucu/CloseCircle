package com.closecircle.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.closecircle.app.data.Member
import com.closecircle.app.domain.Freshness
import com.closecircle.app.domain.GeoPoint
import com.closecircle.app.domain.MapFrame
import com.closecircle.app.domain.freshnessOf
import com.closecircle.app.domain.frameOf
import com.closecircle.app.ui.theme.Cc
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

// shortcut: OpenFreeMap's hosted "positron" style (light, few labels, OpenStreetMap data, no key) is a development
// choice; self-host or pick a paid provider before real traffic. The visible credit is drawn by MapScreen.
private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/positron"

// Romania, shown until someone has a position.
private val DEFAULT_CAMERA = CameraPosition.Builder().target(LatLng(45.9, 25.0)).zoom(5.0).build()

private const val PAD_SIDE_DP = 60
private const val PAD_TOP_DP = 215 // under the group pill and the sharing banner
private const val PAD_BOTTOM_DP = 130 // above the members sheet

/** A real map (MapLibre) with a member avatar at each member's last position. */
@Composable
fun LiveMap(members: List<Member>, key: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val mapView = remember { MapView(context) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var screen by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    val placed = members.filter { it.latitude != null && it.longitude != null }
    val latest by rememberUpdatedState(placed)

    fun project() {
        val m = map ?: return
        screen = latest.associate { it.id to m.projection.toScreenLocation(LatLng(it.latitude!!, it.longitude!!)).let { p -> Offset(p.x, p.y) } }
    }

    DisposableEffect(lifecycle, mapView) {
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> if (!destroyed) { destroyed = true; mapView.onDestroy() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (!destroyed) {
                destroyed = true
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    // Fit the camera when the set of members with a position changes (first load, another group), not on every refresh,
    // so a map the user has panned is not pulled back by each poll.
    val frameKey = key + placed.map { it.id }.sorted().joinToString(",")
    LaunchedEffect(map, frameKey) {
        val m = map ?: return@LaunchedEffect
        val padSide = with(density) { PAD_SIDE_DP.dp.roundToPx() }
        val padTop = with(density) { PAD_TOP_DP.dp.roundToPx() }
        val padBottom = with(density) { PAD_BOTTOM_DP.dp.roundToPx() }
        when (val f = frameOf(latest.map { GeoPoint(it.latitude!!, it.longitude!!) })) {
            null -> m.moveCamera(CameraUpdateFactory.newCameraPosition(DEFAULT_CAMERA))
            is MapFrame.Center -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(f.point.lat, f.point.lon), f.zoom))
            is MapFrame.Bounds -> m.moveCamera(
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.Builder().include(LatLng(f.south, f.west)).include(LatLng(f.north, f.east)).build(),
                    padSide, padTop, padSide, padBottom,
                ),
            )
        }
        project()
    }
    LaunchedEffect(map, placed) { project() }

    Box(modifier) {
        AndroidView(
            factory = {
                mapView.also { view ->
                    view.getMapAsync { m ->
                        m.uiSettings.isAttributionEnabled = false
                        m.uiSettings.isLogoEnabled = false
                        m.uiSettings.isCompassEnabled = false
                        m.cameraPosition = DEFAULT_CAMERA
                        m.addOnCameraMoveListener { project() }
                        m.addOnCameraIdleListener { project() }
                        m.setStyle(Style.Builder().fromUri(MAP_STYLE)) { map = m }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        placed.forEach { m ->
            screen[m.id]?.let { o ->
                Pin(m, with(density) { o.x.toDp() } - 22.dp, with(density) { o.y.toDp() } - 22.dp)
            }
        }
    }
}

@Composable
private fun Pin(m: Member, x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp) {
    val c = Cc.colors
    Column(Modifier.offset(x, y), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, shadowElevation = 4.dp, color = c.surface) { MemberAvatar(m, 44.dp) }
        if (freshnessOf(m.minutesAgo) == Freshness.LAST_KNOWN) {
            Text(
                "acum ${m.minutesAgo} min",
                Modifier.padding(top = 4.dp).clip(CircleShape).background(c.surface).padding(horizontal = 8.dp, vertical = 2.dp),
                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.text,
            )
        }
    }
}
