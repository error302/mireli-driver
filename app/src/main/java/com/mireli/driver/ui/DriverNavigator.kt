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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mireli.driver.data.DriverServiceException
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
                    if (!LocationManagerCompat.isLocationEnabled(manager)) {
                        fix = null
                        error = "Turn on device location to see your position."
                    }
                }
            }
            fun start() {
                if (listening) return
                if (!LocationManagerCompat.isLocationEnabled(manager)) fix = null
                listening = true
                try {
                    val providers = if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    else listOf(LocationManager.NETWORK_PROVIDER)
                    providers.filter { it in manager.allProviders }.forEach { provider ->
                        manager.getLastKnownLocation(provider)?.let { listener.onLocationChanged(it) }
                        manager.requestLocationUpdates(provider, 3_000L, 0f, listener, Looper.getMainLooper())
                    }
                    if (fix == null) {
                        error = if (LocationManagerCompat.isLocationEnabled(manager)) "Waiting for a location fix…"
                        else "Turn on device location to see your position."
                    }
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
    onDestinationRoute: (suspend (GeoPoint, GeoPoint, Boolean) -> DriverRoute)? = null,
    satelliteToken: String? = null,
    initialQuery: String? = null,
) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    val livePosition = rememberDriverPosition()
    val position = if (simulated != null) rememberSimulatedPosition(simulated) else livePosition
    var routeState by remember { mutableStateOf<DriverRoute?>(null) }
    var routeError by remember { mutableStateOf<String?>(null) }
    var ferryFreeRouteUnavailable by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var lastRequest by remember { mutableLongStateOf(0L) }
    var routeAttempt by remember { mutableIntStateOf(0) }
    var avoidFerries by rememberSaveable { mutableStateOf(true) }
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
    var guidanceStarted by rememberSaveable(previewStop?.id) { mutableStateOf(false) }
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
        if (simulated != null || loading || !usableRouteOrigin || ferryFreeRouteUnavailable) return@LaunchedEffect
        val now = SystemClock.elapsedRealtime()
        if (lastRequest > 0 && now - lastRequest < 25_000L) return@LaunchedEffect
        if (routeState != null && !offRoute && lastRequest != 0L) return@LaunchedEffect
        val request = onRequestRoute
        val attempt = routeAttempt
        if (stop != null && request != null) {
            loading = true
            routeError = null
            lastRequest = now
            request(tripId.orEmpty(), target.id, point, avoidFerries) { result ->
                if (routeAttempt != attempt) return@request
                result.mapCatching(DriverRoute::fromJson)
                    .onSuccess { routeState = it; offRouteSamples = 0 }
                    .onFailure { error ->
                        ferryFreeRouteUnavailable = (error as? DriverServiceException)?.code == "NO_FERRY_FREE_ROUTE"
                        routeError = error.message?.take(160) ?: "Directions are unavailable."
                    }
                loading = false
            }
        } else if (stop == null && onDestinationRoute != null) {
            loading = true
            routeError = null
            lastRequest = now
            scope.launch {
                try {
                    val result = onDestinationRoute(point, GeoPoint(target.latitude, target.longitude), avoidFerries)
                    if (previewStop === target && routeAttempt == attempt) { routeState = result; offRouteSamples = 0 }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (previewStop === target && routeAttempt == attempt) {
                        ferryFreeRouteUnavailable = (error as? DriverServiceException)?.code == "NO_FERRY_FREE_ROUTE"
                        routeError = error.message?.take(160) ?: "Directions are unavailable."
                    }
                } finally {
                    if (previewStop === target && routeAttempt == attempt) loading = false
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
        routeAttempt++
        routeState = null
        routeError = null
        ferryFreeRouteUnavailable = false
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

    val colors = MaterialTheme.colorScheme
    var sheetHeight by remember { mutableIntStateOf(0) }
    val controlsInset = with(LocalDensity.current) { sheetHeight.toDp() } + MireliDesign.Space.xs

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavigationMap(
            target = mapTarget, location = current, fix = position.fix, route = route,
            modifier = Modifier.fillMaxSize(), interactive = true,
            follow = stop != null || simulated != null || guidanceStarted,
            satelliteToken = satelliteToken,
            controlsBottomInset = controlsInset,
            onLongPress = if (stop == null && simulated == null) { point ->
                focus.clearFocus()
                clearPreview()
                previewStop = NavigationStop("dropped-pin", "Dropped pin", point.latitude, point.longitude)
                searchQuery = "Dropped pin"
            } else null
        )

        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(MireliDesign.Space.xs),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainer) {
                    IconButton(onClick = onClose, modifier = Modifier.size(MireliDesign.TouchTarget)) {
                        Icon(MireliIcons.Cancel, "End navigation", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Surface(shape = RoundedCornerShape(MireliDesign.Radius.chip), color = colors.surfaceContainer) {
                    Text(
                        stop?.name ?: previewStop?.name ?: "Live map",
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge, maxLines = 1
                    )
                }
                if (simulated != null) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text("SIMULATED", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            if (stop == null && simulated == null) {
                Surface(shape = RoundedCornerShape(MireliDesign.Radius.card), color = colors.surfaceContainer) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(MireliIcons.LocationOn, null, Modifier.size(18.dp), tint = colors.secondary)
                            androidx.compose.material3.TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it.take(120); searchJob?.cancel(); searchResults = null; searchError = null },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(if (onSearch == null) "Search in Google Maps" else "Search destination", style = MaterialTheme.typography.bodyMedium) },
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
                                }, modifier = Modifier.size(MireliDesign.TouchTarget)) {
                                    Icon(MireliIcons.Cancel, "Clear destination", Modifier.size(16.dp))
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
                    Surface(shape = RoundedCornerShape(MireliDesign.Radius.card), color = colors.surfaceContainer) {
                        Column {
                            results.forEachIndexed { index, place ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = MireliDesign.TouchTarget).clickable { previewTo(place) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(MireliIcons.LocationOn, null, Modifier.size(16.dp), tint = colors.secondary)
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
                    color = if (offRoute) colors.errorContainer else colors.surfaceContainer,
                    contentColor = if (offRoute) colors.onErrorContainer else colors.onSurface
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (offRoute) MireliIcons.Refresh else MireliIcons.TurnArrow, null,
                            tint = if (offRoute) colors.onErrorContainer else colors.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    weakGuidance -> "GPS signal is weak"
                                    phase == NavPhase.OFF_ROUTE -> "Rerouting…"
                                    phase == NavPhase.ROUTING -> "Calculating route…"
                                    phase == NavPhase.NO_FIX -> "Waiting for GPS"
                                    phase == NavPhase.FAILED -> "Directions unavailable"
                                    else -> maneuver?.let { "${metres(maneuverDistance ?: it.distanceMeters)} · ${it.instruction}" }
                                        ?: "Head to ${destination.name}"
                                },
                                style = MaterialTheme.typography.drivingMetric,
                                maxLines = 2
                            )
                            val detail = when {
                                weakGuidance -> "Follow the highlighted route."
                                guidanceError != null -> guidanceError
                                !usableRouteOrigin -> "Destination pinned"
                                else -> null
                            }
                            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall,
                                color = if (offRoute) colors.onErrorContainer else colors.onSurfaceVariant)
                        }
                    }
                }
            }
            if (atStop) {
                Surface(shape = RoundedCornerShape(MireliDesign.Radius.card), color = colors.surfaceContainer) {
                    Text("You have arrived at ${destination?.name}", Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                }
            }
        }

        NavigationSheet(
            modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { sheetHeight = it.height },
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
            canOpenMaps = mapTarget != null,
            onStartGuidance = if (stop == null && simulated == null && previewStop != null &&
                route != null && !guidanceStarted) {
                { guidanceStarted = true }
            } else null,
            includesFerry = route?.includesFerry == true,
            avoidFerries = avoidFerries,
            onToggleFerryPreference = if (simulated == null && destination != null &&
                (onRequestRoute != null || onDestinationRoute != null)) {
                {
                    avoidFerries = !avoidFerries
                    routeState = null
                    routeError = null
                    ferryFreeRouteUnavailable = false
                    offRouteSamples = 0
                    lastRequest = 0
                    routeAttempt++
                }
            } else null,
            onOpenMaps = {
                val target = mapTarget!!
                manualFallback = !DriverNavigation.directions(target, avoidFerries).let { url -> url != null && openMaps(context, url) }
            },
            manualFallback = manualFallback,
            onRetry = if (routeError != null && usableRouteOrigin && !loading) {
                { ferryFreeRouteUnavailable = false; lastRequest = 0; routeAttempt++ }
            } else null
        )

        if (!granted && permissionAsked && simulated == null) {
            Surface(
                Modifier.align(Alignment.Center).padding(28.dp),
                shape = RoundedCornerShape(MireliDesign.Radius.card),
                color = colors.surfaceContainer
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Location is off", style = MaterialTheme.typography.titleMedium)
                    Text("Allow location to show your position and get directions.",
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
    canOpenMaps: Boolean,
    onStartGuidance: (() -> Unit)?,
    includesFerry: Boolean,
    avoidFerries: Boolean,
    onToggleFerryPreference: (() -> Unit)?,
    onOpenMaps: () -> Unit,
    manualFallback: Boolean,
    onRetry: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(topStart = MireliDesign.Radius.hero, topEnd = MireliDesign.Radius.hero),
        color = colors.surfaceContainer,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(MireliDesign.Space.md),
            verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stop?.name ?: "Your location", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, maxLines = 2)
                IconButton(onClick = onToggle, modifier = Modifier.size(MireliDesign.TouchTarget)) {
                    Icon(if (expanded) MireliIcons.ExpandLess else MireliIcons.ExpandMore,
                        if (expanded) "Hide GPS details" else "Show GPS details", tint = colors.secondary)
                }
            }
            if (distance != null && seconds != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.md)) {
                    Text(metres(distance), style = MaterialTheme.typography.drivingMetric)
                    Text(minutesLabel(seconds), style = MaterialTheme.typography.drivingMetric)
                    Text("ETA ${clock(seconds)}", style = MaterialTheme.typography.drivingMetric)
                }
            } else {
                val status = when {
                    loading -> "Calculating route…"
                    !live -> "Waiting for GPS"
                    stop == null -> "GPS active"
                    accuracy == null || accuracy > GUIDANCE_ACCURACY_METRES -> "Weak GPS"
                    else -> "Destination pinned"
                }
                DepartureNote(status)
            }
            if (includesFerry) DepartureNote("Includes a ferry crossing")
            onStartGuidance?.let { start ->
                DepartureButton(onClick = start, modifier = Modifier.fillMaxWidth()) { Text("Start navigation") }
            }
            if (canOpenMaps) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)) {
                    if (onToggleFerryPreference != null) {
                        FilterChip(selected = avoidFerries, onClick = onToggleFerryPreference,
                            enabled = !loading, modifier = Modifier.heightIn(min = MireliDesign.TouchTarget),
                            label = { Text("Avoid ferries") })
                    }
                    if (onRetry != null) TextButton(onClick = onRetry) {
                        Text("Retry route", color = colors.secondary)
                    }
                    TextButton(onClick = onOpenMaps) { Text("Open in Google Maps", color = colors.secondary) }
                }
            }
            if (manualFallback) Text("Google Maps could not open.", color = colors.error)
            AnimatedVisibility(expanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.md)) {
                    accuracy?.let { Text("GPS ±${it.toInt()} m", style = MaterialTheme.typography.numericDetail) }
                    speedKmh?.takeIf { live }?.let {
                        Text("%.0f km/h".format(it), style = MaterialTheme.typography.numericDetail)
                    }
                    ageSeconds?.let {
                        Text("${it.coerceAtLeast(0)}s since fix", style = MaterialTheme.typography.numericDetail)
                    }
                }
            }
        }
    }
}
