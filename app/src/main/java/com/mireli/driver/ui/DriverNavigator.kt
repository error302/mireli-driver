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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mireli.driver.domain.DriverNavigation
import com.mireli.driver.domain.DriverRoute
import com.mireli.driver.domain.GeoPoint
import com.mireli.driver.domain.NavigationStop
import com.mireli.driver.domain.SimulatedDrive
import com.mireli.driver.domain.distanceMeters
import com.mireli.driver.domain.nearestRoutePointIndex
import com.mireli.driver.domain.offRouteMetres
import com.mireli.driver.domain.remainingDistanceMeters
import com.mireli.driver.domain.estimatedRemainingSeconds
import kotlinx.coroutines.delay
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
private const val LANE_ACCURACY_METRES = 20f

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
    nextTrain: String? = null,
    trainStation: String? = null,
    simulated: SimulatedDrive? = null
) {
    val context = LocalContext.current
    val livePosition = rememberDriverPosition()
    val position = if (simulated != null) rememberSimulatedPosition(simulated) else livePosition
    var routeState by remember { mutableStateOf<DriverRoute?>(null) }
    var routeError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var lastRequest by remember { mutableLongStateOf(0L) }
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    var manualFallback by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var offRouteSamples by remember { mutableIntStateOf(0) }
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
    val lateralError = route?.let { r -> current?.let { offRouteMetres(r, it) } }
    // Count off-route *fixes*, not distinct error values. Keying this on the error value meant a
    // steady lateral error (the same off-route position repeated) counted once and never reached
    // the two-sample threshold, so the banner never appeared.
    LaunchedEffect(route, current?.latitude, current?.longitude) {
        val error = route?.let { r -> current?.let { offRouteMetres(r, it) } }
        offRouteSamples = if (route != null && (error ?: 0.0) > OFF_ROUTE_METRES) offRouteSamples + 1 else 0
    }

    LaunchedEffect(tripId, stop?.id, current?.latitude, current?.longitude, simulated) {
        val request = onRequestRoute
        val point = current
        val destination = stop
        if (simulated != null || request == null || point == null || destination == null) return@LaunchedEffect
        val now = SystemClock.elapsedRealtime()
        if (routeState != null && now - lastRequest < 25_000L) return@LaunchedEffect
        val needsRoute = routeState == null || (lateralError ?: 0.0) > OFF_ROUTE_METRES
        if (!needsRoute) return@LaunchedEffect
        loading = true; routeError = null; lastRequest = now
        request(tripId.orEmpty(), destination.id, point) { result ->
            result.onSuccess { json ->
                runCatching { DriverRoute.fromJson(json) }
                    .onSuccess { routeState = it; routeError = null; offRouteSamples = 0 }
                    .onFailure { routeError = "The route service returned unusable directions." }
            }.onFailure { routeError = it.message?.take(220) ?: "Directions are unavailable right now." }
            loading = false
        }
    }

    val nearest = route?.let { r -> current?.let { nearestRoutePointIndex(r, it) } }
    val maneuver = nearest?.let { index -> route?.maneuvers?.firstOrNull { it.endShapeIndex > index } ?: route?.maneuvers?.lastOrNull() }
    val maneuverDistance = route?.let { r -> current?.let { p ->
        maneuver?.let { step -> r.geometry.getOrNull(step.endShapeIndex)?.let { distanceMeters(p, it).toInt() } }
    } }
    val remainingTime = route?.let { r -> nearest?.let { estimatedRemainingSeconds(r, it) } }
    val remainingDistance = route?.let { r -> nearest?.let { remainingDistanceMeters(r, it) } }
    val atStop = stop != null && current != null && position.accuracy != null &&
        distanceMeters(current, GeoPoint(stop.latitude, stop.longitude)) + position.accuracy!! < 35.0
    val offRoute = offRouteSamples >= 2
    val accuracy = position.accuracy
    val laneHonest = accuracy != null && accuracy <= LANE_ACCURACY_METRES
    // One state machine decides every banner and status string on this screen.
    val phase = com.mireli.driver.domain.navigationPhase(
        atStop = atStop, hasFix = current != null, offRoute = offRoute,
        loading = loading, routeError = routeError, hasRoute = route != null
    )

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavigationMap(
            target = stop, location = current, fix = position.fix, route = route,
            modifier = Modifier.fillMaxSize(), interactive = true, follow = true
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
                        stop?.name ?: "Live map",
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
            if (stop != null && !atStop) {
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
                                when (phase) {
                                    com.mireli.driver.domain.NavPhase.OFF_ROUTE -> "Off route · finding a way back"
                                    com.mireli.driver.domain.NavPhase.ROUTING -> "Working out your route…"
                                    com.mireli.driver.domain.NavPhase.NO_FIX -> "Waiting for a GPS fix…"
                                    com.mireli.driver.domain.NavPhase.FAILED -> routeError ?: "Directions are unavailable right now."
                                    com.mireli.driver.domain.NavPhase.ARRIVED -> "Arrived at ${stop.name}"
                                    com.mireli.driver.domain.NavPhase.GUIDING ->
                                        maneuver?.let { "${metres(maneuverDistance ?: it.distanceMeters)} · ${it.instruction}" }
                                            ?: "Head to ${stop.name}"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = if (offRoute) MaterialTheme.colorScheme.onErrorContainer else Color.White,
                                maxLines = 2
                            )
                            Text(
                                routeError ?: when {
                                    !position.live -> "Waiting for a GPS fix · guidance pauses, the map does not"
                                    loading -> "Contacting the routing service"
                                    laneHonest -> "Follows the mapped road network"
                                    else -> "GPS is ±${accuracy?.toInt()} m, so your lane cannot be pinpointed"
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
                    Text("You have arrived at ${stop?.name}", Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        NavigationSheet(
            modifier = Modifier.align(Alignment.BottomCenter),
            stop = stop,
            distance = remainingDistance,
            seconds = remainingTime,
            accuracy = accuracy,
            speedKmh = position.speedKmh,
            ageSeconds = position.ageSeconds,
            live = position.live,
            loading = loading,
            nextTrain = nextTrain,
            trainStation = trainStation,
            expanded = expanded,
            onToggle = { expanded = !expanded },
            onEnd = onClose,
            canOpenMaps = stop != null,
            onOpenMaps = {
                manualFallback = !DriverNavigation.directions(stop!!).let { url -> url != null && openMaps(context, url) }
            },
            manualFallback = manualFallback
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
    nextTrain: String?,
    trainStation: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEnd: () -> Unit,
    canOpenMaps: Boolean,
    onOpenMaps: () -> Unit,
    manualFallback: Boolean
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
                if (nextTrain != null) {
                    Surface(shape = RoundedCornerShape(MireliDesign.Radius.chip), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(MireliIcons.Train, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(
                                (trainStation?.let { "$it train " } ?: "Next train ") + nextTrain,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        speedKmh?.let { LiveChip("%.0f km/h".format(it), live) }
                        ageSeconds?.let { LiveChip("updated ${it.coerceAtLeast(0)}s ago", live) }
                        if (accuracy != null && accuracy > LANE_ACCURACY_METRES) {
                            Text(
                                "Accuracy is ±${accuracy.toInt()} m: the car is shown where the fix is, not on a guessed lane.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onEnd, modifier = Modifier.weight(1f)) { Text("End navigation") }
                        if (canOpenMaps) {
                            Button(onClick = onOpenMaps, modifier = Modifier.weight(1f)) { Text("Open in Maps") }
                        }
                    }
                    if (manualFallback) {
                        Text("No maps app or browser could open.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
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
