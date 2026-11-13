package com.mireli.driver.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mireli.driver.domain.DriverNavigation
import com.mireli.driver.domain.DriverRoute
import com.mireli.driver.domain.GeoPoint
import com.mireli.driver.domain.NavPhase
import com.mireli.driver.domain.NavigationStop
import com.mireli.driver.domain.DriverDestination
import com.mireli.driver.domain.SimulatedDrive
import com.mireli.driver.domain.distanceMeters
import com.mireli.driver.domain.nearestRoutePointIndex
import com.mireli.driver.domain.offRouteMetres
import com.mireli.driver.domain.remainingDistanceMeters
import com.mireli.driver.domain.estimatedRemainingSeconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A driver position fix plus why there is none, when there is none. */
internal class DriverPosition(val fix: Location?, val error: String?) {
    val point: GeoPoint? get() = fix?.let { GeoPoint(it.latitude, it.longitude) }
    val accuracy: Float? get() = fix?.takeIf { it.hasAccuracy() }?.accuracy
    val speedKmh: Float? get() = fix?.takeIf { it.hasSpeed() }?.speed?.times(3.6f)
    val ageSeconds: Long?
        get() = fix?.let { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000_000L }
    /** A fix older than this is shown, but labelled as stale rather than passed off as live. */
    val live: Boolean get() = fix != null && (ageSeconds ?: Long.MAX_VALUE) < 15
}

internal const val LOCATION_STALE_MS = 45_000L

/** Off-route is declared beyond this lateral error, and normally needs a few samples. */
private const val OFF_ROUTE_METRES = 60.0

/** Beyond this the car cannot honestly be drawn on a particular lane. */
internal const val GUIDANCE_ACCURACY_METRES = 35f
internal const val ROUTE_ORIGIN_ACCURACY_METRES = 100f

internal fun locationPermissionGranted(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Subscribes to device location while [enabled] and the screen is started. Updates stop when the
 * app leaves the foreground, so the driver is never tracked in the background.
 */
@SuppressLint("MissingPermission")
@Composable
internal fun rememberDriverPosition(enabled: Boolean = true): DriverPosition {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var fix by remember { mutableStateOf<Location?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val granted = locationPermissionGranted(context)

    DisposableEffect(enabled, granted, owner) {
        if (!enabled || !granted) {
            if (!granted) error = "Location is off. Turn on device location to show your position."
            onDispose { }
        } else {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var listening = false
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
                    if (ageMs !in 0 until LOCATION_STALE_MS) return
                    if (fix == null || location.elapsedRealtimeNanos > fix!!.elapsedRealtimeNanos) {
                        fix = location
                        error = null
                    }
                }

                @Deprecated("Deprecated by Android") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) { error = null }
                override fun onProviderDisabled(provider: String) {
                    if (fix == null) error = "Turn on device location to see your position."
                }
            }
            fun start() {
                if (listening) return
                listening = true
                try {
                    val providers = if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    else listOf(LocationManager.NETWORK_PROVIDER)
                    providers.filter { it in manager.allProviders }.forEach { provider ->
                        manager.getLastKnownLocation(provider)?.let { listener.onLocationChanged(it) }
                        manager.requestLocationUpdates(provider, 3_000L, 0f, listener, Looper.getMainLooper())
                    }
                    if (fix == null) error = "Waiting for a location fix…"
                } catch (_: SecurityException) {
                    error = "Location permission is needed to show your position."
                }
            }
            fun stop() {
                if (listening) { runCatching { manager.removeUpdates(listener) }; listening = false }
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> start()
                    Lifecycle.Event.ON_STOP -> stop()
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
            onDispose { owner.lifecycle.removeObserver(observer); stop() }
        }
    }
    return DriverPosition(fix, error)
}

/** Plays a recorded drive on a timer; used by the debug harness, never by the shipping flow. */
@Composable
internal fun rememberSimulatedPosition(drive: SimulatedDrive): DriverPosition {
    var elapsed by remember(drive) { mutableLongStateOf(0L) }
    LaunchedEffect(drive) {
        while (true) {
            delay(500)
            elapsed += 500
        }
    }
    return remember(elapsed, drive) { DriverPosition(drive.fixAt(elapsed), null) }
}

private fun metres(meters: Int): String =
    if (meters >= 1000) "%.1f km".format(meters / 1000.0) else "$meters m"

private fun clock(secondsFromNow: Int): String =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Africa/Nairobi"))
        .format(Instant.now().plusSeconds(secondsFromNow.toLong()))

private fun minutesLabel(seconds: Int): String {
    val minutes = ((seconds + 30) / 60).coerceAtLeast(1)
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

/**
 * Full-screen in-app map. With a [stop] it guides the driver there with the routing service;
 * without one it is simply the live driver map.
 */
@Composable
internal fun DriverNavigationScreen(
    tripId: String?,
    stop: NavigationStop?,
    onRequestRoute: RouteRequest?,
    onClose: () -> Unit,
    simulated: SimulatedDrive? = null,
    onSearch: (suspend (String, GeoPoint?) -> List<DriverDestination>)? = null,
    onDestinationRoute: (suspend (GeoPoint, GeoPoint) -> DriverRoute)? = null,
    satelliteToken: String? = null,
    initialQuery: String? = null,
) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    val livePosition = rememberDriverPosition()
    val position = if (simulated != null) rememberSimulatedPosition(simulated) else livePosition
    var routeState by remember { mutableStateOf<DriverRoute?>(null) }
    var routeError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var lastRequest by remember { mutableLongStateOf(0L) }
    var routeAttempt by remember { mutableIntStateOf(0) }
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    var manualFallback by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var offRouteSamples by remember { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current
    var searchQuery by rememberSaveable(initialQuery) { mutableStateOf(initialQuery.orEmpty()) }
    var searchResults by remember { mutableStateOf<List<DriverDestination>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var previewStop by remember { mutableStateOf<NavigationStop?>(null) }
    val destination = stop ?: previewStop
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val granted = locationPermissionGranted(context)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] != true &&
            result[Manifest.permission.ACCESS_COARSE_LOCATION] != true
        ) permissionAsked = true
    }
    LaunchedEffect(granted, permissionAsked, simulated) {
        if (simulated == null && !granted && !permissionAsked) launcher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    // A recorded drive carries its own route; nothing is requested from the network.
    val route = if (simulated != null) simulated.route else routeState
    LaunchedEffect(simulated) { if (simulated == null) routeState = null }

    val current = position.point
    val accuracy = position.accuracy
    val usableGuidanceFix = position.live && accuracy != null && accuracy <= GUIDANCE_ACCURACY_METRES
    val usableRouteOrigin = position.live && accuracy != null && accuracy <= ROUTE_ORIGIN_ACCURACY_METRES
    val lateralError = route?.let { active -> current?.let { offRouteMetres(active, it) } }
    val confidentlyOffRoute = usableGuidanceFix && (lateralError ?: 0.0) > OFF_ROUTE_METRES + (accuracy ?: 0f)
    LaunchedEffect(route, position.fix, confidentlyOffRoute) {
        offRouteSamples = if (route != null && confidentlyOffRoute) offRouteSamples + 1 else 0
    }
    val offRoute = confidentlyOffRoute && offRouteSamples >= 2

    LaunchedEffect(tripId, destination, current, usableRouteOrigin, offRoute, routeAttempt, simulated) {
        val point = current ?: return@LaunchedEffect
        val target = destination ?: return@LaunchedEffect
        if (simulated != null || loading || !usableRouteOrigin) return@LaunchedEffect
        val now = SystemClock.elapsedRealtime()
        if (lastRequest > 0 && now - lastRequest < 25_000L) return@LaunchedEffect
        if (routeState != null && !offRoute && lastRequest != 0L) return@LaunchedEffect
        val request = onRequestRoute
        if (stop != null && request != null) {
            loading = true
            routeError = null
            lastRequest = now
            request(tripId.orEmpty(), target.id, point) { result ->
                result.mapCatching(DriverRoute::fromJson)
                    .onSuccess { routeState = it; offRouteSamples = 0 }
                    .onFailure { routeError = it.message?.take(160) ?: "Directions are unavailable." }
                loading = false
            }
        } else if (stop == null && onDestinationRoute != null) {
            loading = true
            routeError = null
            lastRequest = now
            scope.launch {
                try {
                    val result = onDestinationRoute(point, GeoPoint(target.latitude, target.longitude))
                    if (previewStop === target) { routeState = result; offRouteSamples = 0 }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (previewStop === target) routeError = error.message?.take(160) ?: "Directions are unavailable."
                } finally {
                    if (previewStop === target) loading = false
                }
            }
        }
    }

    val nearest = route?.let { r -> current?.takeIf { usableGuidanceFix }?.let { nearestRoutePointIndex(r, it) } }
    val maneuver = nearest?.let { index -> route?.maneuvers?.firstOrNull { it.endShapeIndex > index } ?: route?.maneuvers?.lastOrNull() }
    val maneuverDistance = route?.let { r -> current?.let { p ->
        maneuver?.let { step -> r.geometry.getOrNull(step.endShapeIndex)?.let { distanceMeters(p, it).toInt() } }
    } }
    val remainingTime = route?.let { r -> nearest?.let { estimatedRemainingSeconds(r, it) } }
    val remainingDistance = route?.let { r -> nearest?.let { remainingDistanceMeters(r, it) } }
    val mapTarget = stop ?: previewStop

    fun searchDestination(query: String) {
        focus.clearFocus()
        val search = onSearch
        if (search == null) {
            if (DriverNavigation.addressSearch(query)?.let { openMaps(context, it) } != true) {
                searchError = "Google Maps could not open."
            }
            return
        }
        searchJob?.cancel()
        searchJob = scope.launch {
            searching = true
            searchError = null
            searchResults = null
            try {
                val results = search(query, current?.takeIf { position.live })
                searchResults = results.takeIf { it.isNotEmpty() }
                if (results.isEmpty()) searchError = "No match in Mireli. Try a nearby landmark or search in Google Maps."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                searchError = "Mireli search is unavailable. Try again or search in Google Maps."
            } finally {
                searching = false
            }
        }
    }
    fun clearPreview() {
        searchJob?.cancel()
        previewStop = null
        routeState = null
        routeError = null
        searchResults = null
        searchError = null
        loading = false
        lastRequest = 0
        manualFallback = false
    }
    fun previewTo(place: DriverDestination) {
        clearPreview()
        previewStop = NavigationStop("destination-${place.id}", place.name, place.point.latitude, place.point.longitude)
        searchQuery = place.name
    }
    val atStop = destination != null && current != null && position.accuracy != null && position.live &&
        distanceMeters(current, GeoPoint(destination.latitude, destination.longitude)) + position.accuracy!! < 35.0
    val guidanceError = routeError ?: when {
        simulated != null || destination == null -> null
        stop != null && onRequestRoute == null -> "Open Google Maps for directions to this stop."
        stop == null && onDestinationRoute == null -> "Open Google Maps for directions to this pin."
        else -> null
    }
    // One state machine decides every banner and status string on this screen.
    val phase = com.mireli.driver.domain.navigationPhase(
        atStop = atStop, hasFix = usableRouteOrigin || route != null, offRoute = offRoute,
        loading = loading, routeError = guidanceError, hasRoute = route != null
    )
    val weakGuidance = !usableGuidanceFix && route != null

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavigationMap(
            target = mapTarget, location = current, fix = position.fix, route = route,
            modifier = Modifier.fillMaxSize(), interactive = true, follow = stop != null || simulated != null,
            satelliteToken = satelliteToken,
            onLongPress = if (stop == null && simulated == null) { point ->
                focus.clearFocus()
                clearPreview()
                previewStop = NavigationStop("dropped-pin", "Dropped pin", point.latitude, point.longitude)
                searchQuery = "Dropped pin"
            } else null
        )

        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(100), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                    IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) {
                        Icon(MireliIcons.Cancel, "End navigation", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Surface(shape = RoundedCornerShape(MireliDesign.Radius.chip), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                    Text(
                        stop?.name ?: previewStop?.name ?: "Live map",
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge, maxLines = 1
                    )
                }
                if (simulated != null) {
                    Surface(shape = RoundedCornerShape(100), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text("SIMULATED", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            if (stop == null && simulated == null) {
                Surface(shape = RoundedCornerShape(MireliDesign.Radius.card), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(MireliIcons.LocationOn, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            androidx.compose.material3.TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it.take(120); searchJob?.cancel(); searchResults = null; searchError = null },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(if (onSearch == null) "Search in Google Maps" else "Where are you going?", style = MaterialTheme.typography.bodyMedium) },
                                singleLine = true,
                                colors = androidx.compose.material3.TextFieldDefaults.colors(
                                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                                ),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onSearch = { if (searchQuery.trim().length >= 3) searchDestination(searchQuery) }
                                )
                            )
                            if (searchQuery.trim().length >= 3) {
                                IconButton(onClick = { searchDestination(searchQuery) }, enabled = !searching) {
                                    Icon(MireliIcons.Search, "Search destination")
                                }
                            }
                            if (previewStop != null || searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""; clearPreview()
                                }, modifier = Modifier.size(36.dp)) {
                                    Icon(MireliIcons.Cancel, "Clear destination", Modifier.size(16.dp))
                                }
                            }
                        }
                        if (previewStop == null && searchQuery.isBlank()) {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Likoni Ferry", "Moi Airport Mombasa", "Fort Jesus").forEach { hint ->
                                    androidx.compose.material3.AssistChip(
                                        onClick = { searchQuery = hint; searchDestination(hint) },
                                        label = { Text(hint, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                                    )
                                }
                            }
                        }
                    }
                }
                if (previewStop == null && !searching && (searchQuery.isBlank() || searchError != null)) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface) {
                        Text("Touch and hold the map to drop a pin", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                searchError?.let { error ->
                    Surface(shape = RoundedCornerShape(MireliDesign.Radius.card), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            DriverNavigation.addressSearch(searchQuery)?.let { url ->
                                TextButton(onClick = { manualFallback = !openMaps(context, url) }) { Text("Search in Google Maps") }
                            }
                        }
                    }
                }
                searchResults?.let { results ->
                    Surface(shape = RoundedCornerShape(MireliDesign.Radius.card), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                        Column {
                            results.forEachIndexed { index, place ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    Modifier.fillMaxWidth().clickable { previewTo(place) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(MireliIcons.LocationOn, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f)) {
                                        Text(place.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                        if (place.detail.isNotBlank()) Text(place.detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                if (onSearch != null && (searchResults != null || previewStop != null)) {
                    TextButton(onClick = { runCatching { uriHandler.openUri("https://docs.aws.amazon.com/location/latest/developerguide/data-attribution.html") } }) {
                        Text("Map data © AWS, HERE", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (destination != null && !atStop) {
                Surface(
                    shape = RoundedCornerShape(MireliDesign.Radius.card),
                    color = if (offRoute) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (offRoute) MireliIcons.Refresh else MireliIcons.TurnArrow, null,
                            tint = if (offRoute) MaterialTheme.colorScheme.onErrorContainer else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    weakGuidance -> "GPS signal is weak"
                                    phase == NavPhase.OFF_ROUTE -> "Off route · finding a way back"
                                    phase == NavPhase.ROUTING -> "Working out your route…"
                                    phase == NavPhase.NO_FIX -> "Waiting for a GPS fix…"
                                    phase == NavPhase.FAILED -> "Directions unavailable"
                                    else -> maneuver?.let { "${metres(maneuverDistance ?: it.distanceMeters)} · ${it.instruction}" }
                                        ?: "Head to ${destination.name}"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = if (offRoute) MaterialTheme.colorScheme.onErrorContainer else Color.White,
                                maxLines = 2
                            )
                            Text(
                                when {
                                    weakGuidance -> "Follow the highlighted route and road signs."
                                    guidanceError != null -> guidanceError
                                    !usableRouteOrigin -> "The destination is pinned. Open Google Maps for directions."
                                    loading -> "Contacting the routing service"
                                    else -> destination.name
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = (if (offRoute) MaterialTheme.colorScheme.onErrorContainer else Color.White).copy(alpha = .85f),
                                maxLines = 2
                            )
                        }
                    }
                }
            }
            if (atStop) {
                Surface(shape = RoundedCornerShape(MireliDesign.Radius.card), color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 4.dp) {
                    Text("You have arrived at ${destination?.name}", Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        NavigationSheet(
            modifier = Modifier.align(Alignment.BottomCenter),
            stop = stop ?: previewStop,
            distance = remainingDistance?.takeUnless { offRoute },
            seconds = remainingTime?.takeUnless { offRoute },
            accuracy = accuracy,
            speedKmh = position.speedKmh,
            ageSeconds = position.ageSeconds,
            live = position.live,
            loading = loading,
            expanded = expanded,
            onToggle = { expanded = !expanded },
            onEnd = onClose,
            canOpenMaps = mapTarget != null,
            onOpenMaps = {
                val target = mapTarget!!
                manualFallback = !DriverNavigation.directions(target).let { url -> url != null && openMaps(context, url) }
            },
            manualFallback = manualFallback,
            onRetry = if (routeError != null && usableRouteOrigin && !loading) {
                { lastRequest = 0; routeAttempt++ }
            } else null
        )

        if (!granted && permissionAsked && simulated == null) {
            Surface(
                Modifier.align(Alignment.Center).padding(28.dp),
                shape = RoundedCornerShape(MireliDesign.Radius.card),
                color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Location is off", style = MaterialTheme.typography.titleMedium)
                    Text("Mireli needs your device location to draw your car on the map and guide you to the stop. It is used only while this screen is open.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { permissionAsked = false }) { Text("Allow location") }
                }
            }
        }
    }
}

/**
 * The driving sheet: four numbers when collapsed, everything else on demand. The old panel was
 * taller than the map it sat on.
 */
@Composable
private fun NavigationSheet(
    modifier: Modifier,
    stop: NavigationStop?,
    distance: Int?,
    seconds: Int?,
    accuracy: Float?,
    speedKmh: Float?,
    ageSeconds: Long?,
    live: Boolean,
    loading: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEnd: () -> Unit,
    canOpenMaps: Boolean,
    onOpenMaps: () -> Unit,
    manualFallback: Boolean,
    onRetry: (() -> Unit)?,
) {
    Surface(
        modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(topStart = MireliDesign.Radius.hero, topEnd = MireliDesign.Radius.hero),
        color = MaterialTheme.colorScheme.surface, shadowElevation = 14.dp
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stop?.name ?: "Your location",
                        style = MaterialTheme.typography.titleMedium, maxLines = 1
                    )
                    Text(
                        when {
                            distance != null && seconds != null -> "${metres(distance)} · ${minutesLabel(seconds)} · arrive ${clock(seconds)} EAT"
                            loading -> "Working out the route…"
                            stop == null -> "Live position, updated every few seconds"
                            !live || accuracy == null || accuracy > GUIDANCE_ACCURACY_METRES -> "Waiting for a more accurate location"
                            else -> "Distance and time appear once the route loads"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                SheetChevron(expanded, onToggle)
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiveChip(
                    when {
                        !live -> "GPS lost"
                        accuracy != null -> "±${accuracy.toInt()} m"
                        else -> "No fix"
                    },
                    live && distance != null
                )
            }

            if (canOpenMaps) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry route") }
                    TextButton(onClick = onOpenMaps) { Text("Open in Google Maps") }
                }
            }
            if (manualFallback) Text("Google Maps could not open.", color = MaterialTheme.colorScheme.error)
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        speedKmh?.let { LiveChip("%.0f km/h".format(it), live) }
                        ageSeconds?.let { LiveChip("updated ${it.coerceAtLeast(0)}s ago", live) }
                    }
                    TextButton(onClick = onEnd) { Text("End navigation") }
                }
            }
        }
    }
}

@Composable
private fun SheetChevron(expanded: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
        Icon(
            if (expanded) MireliIcons.ExpandLess else MireliIcons.ExpandMore,
            if (expanded) "Show less" else "Show more",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LiveChip(text: String, live: Boolean) {
    Surface(
        shape = RoundedCornerShape(MireliDesign.Radius.chip),
        color = if (live) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
            color = if (live) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
