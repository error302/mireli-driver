package com.mireli.driver.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mireli.driver.BuildConfig
import com.mireli.driver.R
import com.mireli.driver.domain.DriverRoute
import com.mireli.driver.domain.GeoPoint
import com.mireli.driver.domain.NavigationStop
import com.mireli.driver.domain.bearingDelta
import com.mireli.driver.domain.distanceMeters
import com.mireli.driver.domain.nearestRoutePointIndex
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos

private const val TAG = "MireliMap"
private val MombasaMapCentre = LatLng(-4.0435, 39.6682)

private const val CAR_ICON_ID = "mireli-car-icon"
private const val CAR_SHADOW_ICON_ID = "mireli-car-shadow"
private const val CAR_SOURCE_ID = "mireli-car-source"
private const val CAR_LAYER_ID = "mireli-car-layer"
private const val CAR_SHADOW_LAYER_ID = "mireli-car-shadow-layer"
private const val CAR_HALO_ICON_ID = "mireli-car-halo"
private const val CAR_HALO_LAYER_ID = "mireli-car-halo-layer"
private const val ACCURACY_SOURCE_ID = "mireli-accuracy-source"
private const val ACCURACY_FILL_ID = "mireli-accuracy-fill"
private const val ACCURACY_EDGE_ID = "mireli-accuracy-edge"
private const val ROUTE_SOURCE_ID = "mireli-route-source"
private const val ROUTE_CASING_ID = "mireli-route-casing"
private const val ROUTE_MAIN_ID = "mireli-route-main"
private const val ROUTED_SOURCE_ID = "mireli-route-done-source"
private const val ROUTED_LAYER_ID = "mireli-route-done"

private const val AccuracyBlue = "#3B6F9B"

/** Frame time for the marker interpolation loop. */
private const val TICK_MS = 55L

/**
 * Basemap choices. Auto follows the app theme; the rest are fixed OpenFreeMap styles so the
 * driver can pick the rendering that reads best in their light conditions.
 */
internal enum class MapLayer(val label: String, val styleName: String?) {
    Auto("Auto", null),
    Streets("Streets", "liberty"),
    Daylight("Daylight", "bright"),
    Minimal("Minimal", "positron"),
    Night("Night", "dark"),
    Satellite("Satellite", null);

    companion object {
        private const val PREF = "map_layer"
        fun read(prefs: android.content.SharedPreferences): MapLayer =
            entries.firstOrNull { it.name == prefs.getString(PREF, null) } ?: Auto
        fun write(prefs: android.content.SharedPreferences, layer: MapLayer) {
            prefs.edit().putString(PREF, layer.name).apply()
        }
    }
}

private object DriverMapHttp {
    private val origin = BuildConfig.DRIVER_SERVICE_URL.toHttpUrl()
    @Volatile var satelliteToken: String? = null
    val client = OkHttpClient.Builder().addNetworkInterceptor { chain ->
        val request = chain.request()
        val url = request.url
        val token = satelliteToken
        if (token != null && url.scheme == origin.scheme && url.host == origin.host && url.port == origin.port &&
            url.encodedPath.startsWith("/api/v1/driver/navigation/satellite/")) {
            chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
        } else chain.proceed(request)
    }.build()
}

private fun satelliteStyle(): String = JSONObject("""{
  "version":8,"sources":{"imagery":{"type":"raster","tileSize":256,"maxzoom":19,
  "tiles":["${BuildConfig.DRIVER_SERVICE_URL}/api/v1/driver/navigation/satellite/{z}/{x}/{y}"],
  "attribution":"© <a href='https://docs.aws.amazon.com/location/latest/developerguide/data-attribution.html'>AWS</a>, <a href='https://legal.here.com/en-gb/terms/general-content-supplier-terms-and-notices'>HERE</a>"}},
  "layers":[{"id":"imagery","type":"raster","source":"imagery"}]}
""").toString()

/** Resolves the style URL for a layer choice; Auto keeps the theme-following behaviour. */
internal fun mapLayerUrl(layer: MapLayer, dark: Boolean): String {
    val name = layer.styleName
    if (name == null) return mapStyleUrl(dark)
    val base = BuildConfig.MAP_STYLE_URL
    val cut = base.lastIndexOf('/')
    return if (cut <= 0) base else base.substring(0, cut + 1) + name
}

/**
 * Navigation basemap. The vehicle is a style symbol layer rather than a marker annotation so it can
 * carry a rotating, perspective-correct icon; annotations in this SDK cannot be rotated at all.
 *
 * Everything positional is drawn straight from GPS: the car's icon anchor is its centre, the
 * accuracy polygon is metres on the ground, and the route line is only ever the routing service's
 * geometry. There is no straight "as the crow flies" line anywhere, because that would read as a
 * driving route when it is not one.
 */
@Composable
internal fun NavigationMap(
    target: NavigationStop?,
    location: GeoPoint?,
    fix: Location?,
    route: DriverRoute?,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    follow: Boolean = false,
    controls: Boolean = true,
    homePreview: Boolean = false,
    satelliteToken: String? = null,
    onLongPress: ((GeoPoint) -> Unit)? = null,
    controlsBottomInset: Dp = MireliDesign.Space.xs,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latestLongPress by rememberUpdatedState(onLongPress)
    val dark = MaterialTheme.colorScheme.background.luminanceIsDark()
    val prefs = remember { context.getSharedPreferences("map", android.content.Context.MODE_PRIVATE) }
    var layerChoice by remember { mutableStateOf(MapLayer.read(prefs)) }
    val activeLayer = if (homePreview || (layerChoice == MapLayer.Satellite && satelliteToken == null)) MapLayer.Auto else layerChoice
    val styleUrl = mapLayerUrl(activeLayer, dark)
    var layersOpen by remember { mutableStateOf(false) }
    var retryStyle by remember { mutableIntStateOf(0) }
    DisposableEffect(satelliteToken) {
        if (satelliteToken != null) DriverMapHttp.satelliteToken = satelliteToken
        onDispose { if (satelliteToken != null && DriverMapHttp.satelliteToken == satelliteToken) DriverMapHttp.satelliteToken = null }
    }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var styleVersion by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var following by remember(follow, target?.id) { mutableStateOf(follow) }
    val stopFollowing by rememberUpdatedState({ following = false })
    var lastHeading by remember { mutableStateOf(0.0) }

    // The interpolation loop reads these every tick, so they must survive recomposition.
    val latestPoint = remember { mutableStateOf(location) }
    val latestAccuracy = remember { mutableStateOf<Float?>(null) }
    val latestHeading = remember { mutableStateOf(0.0) }
    val latestRoute = remember { mutableStateOf(route) }
    val followWanted = remember { mutableStateOf(follow) }
    var targetMarker by remember { mutableStateOf<Marker?>(null) }

    val carIcon = remember(context) { rasterise(context, R.drawable.ic_vehicle_top, 256) }
    val shadowIcon = remember(context) { carShadowBitmap() }
    val haloIcon = remember { carHaloBitmap() }

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        HttpRequestUtil.setOkHttpClient(DriverMapHttp.client)
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
        if (!interactive) {
            // TextureView follows the rounded clip and scrolls with the Compose screen.
            MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
        } else {
            MapView(context)
        }
    }

    DisposableEffect(mapView, owner) {
        var started = false
        var resumed = false
        fun start() { if (!started) { mapView.onStart(); started = true } }
        fun resume() { start(); if (!resumed) { mapView.onResume(); resumed = true } }
        fun pause() { if (resumed) { mapView.onPause(); resumed = false } }
        fun stop() { pause(); if (started) { mapView.onStop(); started = false } }
        mapView.onCreate(Bundle())
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()

        mapView.addOnDidFailLoadingMapListener { reason -> failed = true; Log.e(TAG, "map failed: $reason") }
        mapView.getMapAsync { ready ->
            map = ready
            ready.moveCamera(CameraUpdateFactory.newLatLngZoom(MombasaMapCentre, 12.0))
            // The SDK wordmark sits on top of the card artwork; attribution stays on instead.
            ready.uiSettings.isLogoEnabled = false
            ready.uiSettings.isAttributionEnabled = true
            if (controls) ready.uiSettings.setAttributionMargins(12, 0, 0, (150 * context.resources.displayMetrics.density).toInt())
            // The SDK compass floats over the status bar; we draw our own control instead.
            ready.uiSettings.isCompassEnabled = false
            ready.uiSettings.setAllGesturesEnabled(interactive)
            ready.addOnMapLongClickListener { coordinate ->
                val action = latestLongPress ?: return@addOnMapLongClickListener false
                action(GeoPoint(coordinate.latitude, coordinate.longitude))
                true
            }
            ready.addOnCameraMoveStartedListener { reason ->
                // Only a real finger gesture stops the chase; our own animations never do.
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    stopFollowing()
                    followWanted.value = false
                }
            }
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            stop()
            mapView.onDestroy()
            map = null
            style = null
        }
    }

    // Keep the latest inputs visible to the animation loop without restarting it on every fix.
    latestPoint.value = location
    latestRoute.value = route
    latestAccuracy.value = fix?.takeIf { it.hasAccuracy() }?.accuracy
    latestHeading.value = location?.let { point ->
        resolveHeading(fix?.takeIf { it.hasBearing() }?.bearing, fix?.speed, lastHeading, route, point)
    } ?: lastHeading
    followWanted.value = following

    // Style loads, initial and on layer switch. installLayers re-applies the vehicle, route and
    // accuracy layers on the fresh style, and styleVersion restarts the render loop and marker.
    LaunchedEffect(map, styleUrl, activeLayer, retryStyle) {
        val ready = map ?: return@LaunchedEffect
        // Drop the old style reference before the switch: MapLibre invalidates it, and the render
        // loop must not touch it while the new style loads.
        loaded = false
        failed = false
        style = null
        val builder = Style.Builder().apply { if (activeLayer == MapLayer.Satellite) fromJson(satelliteStyle()) else fromUri(styleUrl) }
        ready.setStyle(builder) { loadedStyle ->
            try {
                installLayers(loadedStyle, carIcon, shadowIcon, haloIcon)
                style = loadedStyle
                loaded = true
                styleVersion++
                failed = false
            } catch (error: Throwable) {
                // Never fail silently again: a swallowed exception here is how the car
                // disappeared from the map the first time.
                Log.e(TAG, "could not install vehicle layers", error)
                failed = true
            }
        }
        delay(12_000)
        if (!loaded) failed = true
    }

    LaunchedEffect(map, loaded, route) {
        val ready = map ?: return@LaunchedEffect
        if (loaded && route != null) {
            drawRoute(style, route, location)
            if (!following) frameRouteOrPoint(ready, route, location ?: route.geometry.first())
        }
    }

    LaunchedEffect(map, loaded, styleVersion, target) {
        val ready = map?.takeIf { loaded } ?: return@LaunchedEffect
        targetMarker?.let { runCatching { ready.removeAnnotation(it) } }
        targetMarker = null
        target?.let { stop ->
            targetMarker = runCatching {
                ready.addMarker(MarkerOptions().position(LatLng(stop.latitude, stop.longitude)).title(stop.name))
            }.getOrNull()
            if (location == null) ready.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(stop.latitude, stop.longitude), 15.0))
        }
    }

    // The dashboard only needs to redraw when a new fix or style arrives.
    LaunchedEffect(map, style, location, fix) {
        if (!homePreview) return@LaunchedEffect
        val ready = map ?: return@LaunchedEffect
        val currentStyle = style ?: return@LaunchedEffect
        if (location == null) {
            val empty = FeatureCollection.fromFeatures(emptyList<Feature>())
            currentStyle.getSourceAs<GeoJsonSource>(ACCURACY_SOURCE_ID)?.setGeoJson(empty)
            ready.moveCamera(CameraUpdateFactory.newLatLngZoom(MombasaMapCentre, 12.0))
        } else {
            val accuracy = fix?.takeIf { it.hasAccuracy() }?.accuracy
            drawAccuracy(currentStyle, location, accuracy)
            val targetZoom = when {
                accuracy == null || accuracy <= 150f -> 16.4
                accuracy <= 400f -> 15.0
                else -> 13.0
            }
            ready.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), targetZoom))
        }
    }

    // One loop for vehicle motion, rotation, accuracy and the follow camera.
    LaunchedEffect(map, loaded, styleVersion) {
        if (homePreview || !loaded) return@LaunchedEffect
        val ready = map ?: return@LaunchedEffect
        var shown: GeoPoint? = null
        var shownBearing = 0.0
        var lastCamera = 0L
        var lastSplit = Int.MIN_VALUE
        var lastRouteRef: DriverRoute? = null
        while (true) {
            val goal = latestPoint.value
            // A style switch invalidates the previous Style object before the new one finishes
            // loading, and getLayerAs throws on a stale style. Treat that as "no layer yet"
            // rather than letting it kill the app mid-drive.
            val layer = runCatching { style?.getLayerAs<SymbolLayer>(CAR_LAYER_ID) }.getOrNull()
            if (goal == null) {
                layer?.setProperties(PropertyFactory.iconOpacity(0.4f))
                delay(200)
                continue
            }
            layer?.setProperties(PropertyFactory.iconOpacity(1f))
            if (shown == null) {
                shown = goal
                shownBearing = latestHeading.value
                lastHeading = shownBearing
                drawCar(style, goal, shownBearing)
                drawAccuracy(style, goal, latestAccuracy.value)
                if (!cameraReady) {
                    frameRouteOrPoint(ready, latestRoute.value, goal)
                    cameraReady = true
                }
            } else {
                // Ease toward the new fix and turn along the shortest arc. At 18 Hz this is smooth
                // even when the provider only delivers a fix every few seconds.
                shown = GeoPoint(
                    shown.latitude + (goal.latitude - shown.latitude) * 0.22,
                    shown.longitude + (goal.longitude - shown.longitude) * 0.22
                )
                val wanted = latestHeading.value
                shownBearing = (shownBearing + bearingDelta(shownBearing, wanted) * 0.2) % 360.0
                lastHeading = shownBearing
                drawCar(style, shown, shownBearing)
                drawAccuracy(style, shown, latestAccuracy.value)
            }
            // Rebuilding a 600-point geometry 18 times a second is wasted work; the split only
            // moves when the car passes a vertex.
            val activeRoute = latestRoute.value
            val splitNow = if (activeRoute != null && goal != null) nearestRoutePointIndex(activeRoute, goal) else -1
            if (splitNow != lastSplit || activeRoute !== lastRouteRef) {
                drawRoute(style, activeRoute, goal)
                lastSplit = splitNow
                lastRouteRef = activeRoute
            }
            if (followWanted.value) {
                val now = System.currentTimeMillis()
                val moved = distanceMeters(shown, goal)
                if (now - lastCamera > 650L || moved > 25.0) {
                    ready.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(LatLng(shown.latitude, shown.longitude))
                                .zoom(17.4).tilt(45.0).bearing(shownBearing).build()
                        ), 620
                    )
                    lastCamera = now
                }
            }
            if (!cameraReady) {
                frameRouteOrPoint(ready, latestRoute.value, goal)
                cameraReady = true
            }
            delay(TICK_MS)
        }
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (homePreview && loaded && !failed && location != null) {
            // The preview camera stays centered on the fix. Draw its vehicle above the texture
            // so the north-up marker has a consistent size at every accuracy-dependent zoom.
            Image(painterResource(R.drawable.ic_vehicle_top), contentDescription = "Current position",
                modifier = Modifier.align(Alignment.Center).size(32.dp))
        }
        if (!loaded && !failed) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (failed) {
            Surface(
                Modifier.align(Alignment.Center).padding(20.dp),
                color = MaterialTheme.colorScheme.surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("${activeLayer.label} map could not load.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { retryStyle++ }) { Text("Retry map") }
                    if (activeLayer != MapLayer.Auto) TextButton(onClick = { layerChoice = MapLayer.Auto; MapLayer.write(prefs, MapLayer.Auto) }) { Text("Use automatic map") }
                }
            }
        }
        if (controls) {
            Column(
                // Sit above the driving sheet, not behind it.
                Modifier.align(Alignment.BottomEnd).padding(end = MireliDesign.Space.xs, bottom = controlsBottomInset),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (interactive && location != null) {
                    FilledTonalIconButton(onClick = {
                        following = true
                        followWanted.value = true
                        map?.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(LatLng(location.latitude, location.longitude))
                                    .zoom(17.4).tilt(45.0).bearing(lastHeading).build()
                            ), 500
                        )
                    }) { Icon(MireliIcons.Compass, "Recentre and align to my heading") }
                }
                if (interactive) {
                    Box {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        onClick = { layersOpen = true }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(MireliIcons.Map, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                            Text("Layers · ${activeLayer.label}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    DropdownMenu(expanded = layersOpen, onDismissRequest = { layersOpen = false }) {
                        MapLayer.entries.forEach { layer ->
                            val enabled = layer != MapLayer.Satellite || satelliteToken != null
                            DropdownMenuItem(text = { Text(if (enabled) layer.label else "Satellite unavailable") },
                                enabled = enabled, leadingIcon = if (layer == activeLayer) {
                                    { Icon(MireliIcons.CheckCircleOutline, "Selected") }
                                } else null, onClick = {
                                    layerChoice = layer
                                    MapLayer.write(prefs, layer)
                                    layersOpen = false
                                })
                        }
                    }
                    }
                    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                        Column {
                            MapZoomButton("+") { map?.animateCamera(CameraUpdateFactory.zoomIn()) }
                            MapZoomButton("−") { map?.animateCamera(CameraUpdateFactory.zoomOut()) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapZoomButton(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.size(MireliDesign.TouchTarget)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

/** Layers are installed once per style load; ids are stable so later updates can find them. */
private fun installLayers(style: Style, car: Bitmap, shadow: Bitmap, halo: Bitmap) {
    style.addImage(CAR_ICON_ID, car)
    style.addImage(CAR_SHADOW_ICON_ID, shadow)
    style.addImage(CAR_HALO_ICON_ID, halo)

    style.addSource(GeoJsonSource(ACCURACY_SOURCE_ID))
    style.addLayer(
        FillLayer(ACCURACY_FILL_ID, ACCURACY_SOURCE_ID).withProperties(
            PropertyFactory.fillColor(AccuracyBlue), PropertyFactory.fillOpacity(0.12f)
        )
    )
    style.addLayer(
        LineLayer(ACCURACY_EDGE_ID, ACCURACY_SOURCE_ID).withProperties(
            PropertyFactory.lineColor(AccuracyBlue), PropertyFactory.lineOpacity(0.45f),
            PropertyFactory.lineWidth(1.5f)
        )
    )

    style.addSource(GeoJsonSource(ROUTED_SOURCE_ID))
    style.addSource(GeoJsonSource(ROUTE_SOURCE_ID))
    style.addLayer(
        LineLayer(ROUTED_LAYER_ID, ROUTED_SOURCE_ID).withProperties(
            PropertyFactory.lineColor("#93A8A3"),
            PropertyFactory.lineWidth(zoomWidth(4f, 11f)),
            PropertyFactory.lineOpacity(0.75f),
            PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")
        )
    )
    style.addLayer(
        LineLayer(ROUTE_CASING_ID, ROUTE_SOURCE_ID).withProperties(
            PropertyFactory.lineColor("#FFFFFF"),
            PropertyFactory.lineWidth(zoomWidth(8f, 20f)),
            PropertyFactory.lineOpacity(0.95f),
            PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")
        )
    )
    style.addLayer(
        LineLayer(ROUTE_MAIN_ID, ROUTE_SOURCE_ID).withProperties(
            PropertyFactory.lineColor(RouteBlue),
            PropertyFactory.lineWidth(zoomWidth(5f, 14f)),
            PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")
        )
    )

    style.addSource(GeoJsonSource(CAR_SOURCE_ID))
    // A white puck under the car: the single biggest legibility win on a dark basemap.
    style.addLayer(
        SymbolLayer(CAR_HALO_LAYER_ID, CAR_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(CAR_HALO_ICON_ID),
            PropertyFactory.iconSize(zoomHaloSize()),
            PropertyFactory.iconRotate(0f),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconPitchAlignment("map")
        )
    )
    style.addLayer(
        SymbolLayer(CAR_SHADOW_LAYER_ID, CAR_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(CAR_SHADOW_ICON_ID),
            PropertyFactory.iconSize(zoomCarSize()),
            PropertyFactory.iconRotate(0f),
            PropertyFactory.iconTranslate(arrayOf(0f, 10f)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconPitchAlignment("map"),
            PropertyFactory.iconRotationAlignment("map")
        )
    )
    style.addLayer(
        SymbolLayer(CAR_LAYER_ID, CAR_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(CAR_ICON_ID),
            PropertyFactory.iconSize(zoomCarSize()),
            PropertyFactory.iconRotate(0f),
            // Centre anchoring is what keeps the car exactly on the GPS fix rather than offset
            // north by half an icon like a bottom-anchored marker.
            PropertyFactory.iconAnchor("center"),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconPitchAlignment("map"),
            PropertyFactory.iconRotationAlignment("map")
        )
    )
}

private fun zoomWidth(near: Float, far: Float) = Expression.interpolate(
    Expression.exponential(1.6f), Expression.zoom(),
    Expression.stop(12, near), Expression.stop(18, far)
)

/** The car grows with zoom so it stays the same size on the road, not on the screen. */
private fun zoomCarSize() = Expression.interpolate(
    Expression.exponential(1.5f), Expression.zoom(),
    Expression.stop(11, 0.26f), Expression.stop(15, 0.40f), Expression.stop(19, 0.68f)
)

/** The puck is wider than the car so a ring of white always shows around it. */
private fun zoomHaloSize() = Expression.interpolate(
    Expression.exponential(1.5f), Expression.zoom(),
    Expression.stop(11, 0.30f), Expression.stop(15, 0.46f), Expression.stop(19, 0.76f)
)

private fun drawCar(style: Style?, point: GeoPoint, bearing: Double) {
    val ready = style ?: return
    runCatching {
        ready.getSourceAs<GeoJsonSource>(CAR_SOURCE_ID)
            ?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)))
    }.onFailure { Log.w(TAG, "car position", it) }
    runCatching {
        ready.getLayerAs<SymbolLayer>(CAR_LAYER_ID)?.setProperties(PropertyFactory.iconRotate(bearing.toFloat()))
        ready.getLayerAs<SymbolLayer>(CAR_SHADOW_LAYER_ID)?.setProperties(PropertyFactory.iconRotate(bearing.toFloat()))
    }.onFailure { Log.w(TAG, "car rotation", it) }
}

/** Accuracy is drawn as a real ground polygon; a pixel circle would lie about scale when tilted. */
private fun drawAccuracy(style: Style?, point: GeoPoint, accuracy: Float?) {
    val ready = style ?: return
    val radius = accuracy?.takeIf { it.isFinite() && it > 0.5f }?.toDouble()
    val source = runCatching { ready.getSourceAs<GeoJsonSource>(ACCURACY_SOURCE_ID) }.getOrNull() ?: return
    if (radius == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        return
    }
    val dLat = radius / 111_320.0
    val dLon = radius / (111_320.0 * cos(Math.toRadians(point.latitude)).coerceAtLeast(0.05))
    val ring = (0 until 48).map { step ->
        val angle = Math.toRadians(step * 360.0 / 48)
        Point.fromLngLat(point.longitude + dLon * Math.cos(angle), point.latitude + dLat * Math.sin(angle))
    }.toMutableList()
    ring.add(ring.first())
    runCatching { source.setGeoJson(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))) }
        .onFailure { Log.w(TAG, "accuracy", it) }
}

private fun drawRoute(style: Style?, route: DriverRoute?, point: GeoPoint?) {
    val ready = style ?: return
    val remaining = runCatching { ready.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID) }.getOrNull()
    val done = runCatching { ready.getSourceAs<GeoJsonSource>(ROUTED_SOURCE_ID) }.getOrNull()
    if (remaining == null || done == null) return
    if (route == null || route.geometry.size < 2) {
        remaining.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        done.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        return
    }
    val split = point?.let { nearestRoutePointIndex(route, it) } ?: 0
    val ahead = route.geometry.drop(split.coerceAtMost(route.geometry.lastIndex - 1))
    val behind = route.geometry.take((split + 1).coerceAtLeast(2))
    fun line(points: List<GeoPoint>): Feature {
        val coordinates = ArrayList<Point>(points.size)
        for (entry in points) coordinates.add(Point.fromLngLat(entry.longitude, entry.latitude))
        return Feature.fromGeometry(LineString.fromLngLats(coordinates))
    }
    runCatching {
        remaining.setGeoJson(line(ahead))
        if (behind.size >= 2 && split > 0) done.setGeoJson(line(behind))
        else done.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
    }.onFailure { Log.w(TAG, "route", it) }
}

private fun frameRouteOrPoint(map: MapLibreMap, route: DriverRoute?, point: GeoPoint) {
    runCatching {
        if (route != null && route.geometry.size >= 2) {
            val builder = LatLngBounds.Builder()
            route.geometry.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
            builder.include(LatLng(point.latitude, point.longitude))
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 90))
        } else {
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder().target(LatLng(point.latitude, point.longitude)).zoom(16.4).build()
                )
            )
        }
    }
}

/** Light style by day, the published dark variant at night, so the map matches the app. */
internal fun mapStyleUrl(dark: Boolean): String {
    val base = BuildConfig.MAP_STYLE_URL
    if (!dark) return base
    val cut = base.lastIndexOf('/')
    if (cut <= 0) return base
    val name = base.substring(cut + 1)
    if (name.isBlank()) return base
    return base.substring(0, cut + 1) + "dark"
}

/** The theme's background colour decides the basemap, not the system flag alone. */
private fun androidx.compose.ui.graphics.Color.luminanceIsDark(): Boolean =
    (0.299 * red + 0.587 * green + 0.114 * blue) < 0.4

internal fun rasterise(context: Context, resId: Int, targetWidth: Int): Bitmap {
    val drawable = ContextCompat.getDrawable(context, resId)
    val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
        drawable.bitmap
    } else {
        val width = drawable?.intrinsicWidth?.takeIf { it > 0 } ?: targetWidth
        val height = drawable?.intrinsicHeight?.takeIf { it > 0 } ?: targetWidth
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { target ->
            val canvas = Canvas(target)
            drawable?.setBounds(0, 0, width, height)
            drawable?.draw(canvas)
        }
    }
    val height = (targetWidth * bitmap.height.toFloat() / bitmap.width.toFloat()).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, height, true)
}

/** White puck with a very soft edge, drawn behind the car. */
internal fun carHaloBitmap(): Bitmap {
    val size = 128
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.shader = RadialGradient(
        size / 2f, size / 2f, size / 2f,
        intArrayOf(AndroidColor.WHITE, AndroidColor.WHITE, AndroidColor.argb(220, 255, 255, 255), AndroidColor.TRANSPARENT),
        floatArrayOf(0f, 0.82f, 0.94f, 1f), Shader.TileMode.CLAMP
    )
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    return bitmap
}

/** A soft drop shadow under the car: cheap depth without a real 3D model. */
private fun carShadowBitmap(): Bitmap {
    val width = 220
    val height = 130
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.shader = RadialGradient(
        width / 2f, height / 2f, width / 2.1f,
        intArrayOf(AndroidColor.argb(90, 6, 30, 26), AndroidColor.argb(30, 6, 30, 26), AndroidColor.TRANSPARENT),
        floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
    )
    canvas.drawOval(6f, 8f, width - 6f, height - 8f, paint)
    return bitmap
}
