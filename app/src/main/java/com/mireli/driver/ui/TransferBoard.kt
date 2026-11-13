package com.mireli.driver.ui

import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mireli.driver.domain.DriverNavigation
import com.mireli.driver.domain.NavigationStop
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val StationNavy = Color(0xFF101820)
internal val StationMint = Color(0xFF7FE3CE)
private val DepartureZone = ZoneId.of("Africa/Nairobi")
private val DepartureDay = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.forLanguageTag("en-KE")).withZone(DepartureZone)
private val DepartureTime = DateTimeFormatter.ofPattern("HH:mm").withZone(DepartureZone)

internal fun JSONObject.navigationStops(): List<NavigationStop> {
    val stages = optJSONArray("stages") ?: return emptyList()
    return (0 until stages.length()).mapNotNull { index ->
        val stage = stages.optJSONObject(index) ?: return@mapNotNull null
        NavigationStop(stage.optString("id", index.toString()), stage.optString("name"),
            stage.optDouble("latitude", Double.NaN), stage.optDouble("longitude", Double.NaN))
    }
}

private fun JSONObject.passengerManifest(): List<JSONObject> {
    val passengers = optJSONArray("passengers") ?: return emptyList()
    return (0 until passengers.length()).mapNotNull(passengers::optJSONObject)
}

internal fun JSONObject.bookedStageIds(): Set<String> =
    passengerManifest().mapNotNull { it.opt("stageId") as? String }.toSet()

internal fun JSONObject.hasDoorAddress(): Boolean = passengerManifest().any { it.optBoolean("homePickup") }

internal fun JSONObject.primaryNavigationStop(stops: List<NavigationStop> = navigationStops()): NavigationStop? =
    DriverNavigation.primaryStop(stops, optString("direction"), optString("phase"), bookedStageIds(), hasDoorAddress())

internal fun JSONObject.departureInstant(): Instant? = runCatching { Instant.parse(optString("departureAt")) }.getOrNull()

internal fun JSONObject.isActiveTransfer(): Boolean = optString("phase") in listOf("assigned", "accepted", "at_pickup", "in_progress")

internal fun transferOrder(): Comparator<JSONObject> = compareBy<JSONObject> {
    when (it.optString("phase")) {
        "in_progress" -> 0
        "at_pickup" -> 1
        "accepted" -> 2
        "assigned" -> 3
        else -> 4
    }
}.thenBy { it.departureInstant() ?: Instant.MAX }

@Composable
internal fun StationLines(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val near = Offset(size.width * .24f, size.height * .69f)
        val bend = Offset(size.width * .53f, near.y)
        val far = Offset(size.width * .85f, size.height * .27f)
        val width = 2.dp.toPx()
        drawLine(StationMint.copy(alpha = .45f), near, bend, width, StrokeCap.Round)
        drawLine(StationMint.copy(alpha = .45f), bend, far, width, StrokeCap.Round)
        drawCircle(StationNavy, 5.dp.toPx(), near)
        drawCircle(StationMint, 5.dp.toPx(), near, style = Stroke(width))
        drawCircle(StationNavy, 7.dp.toPx(), far)
        drawCircle(StationMint, 7.dp.toPx(), far, style = Stroke(width))
        drawCircle(StationMint, 2.5.dp.toPx(), far)
    }
}

@Composable
internal fun DriverNotice(icon: ImageVector, title: String, detail: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.titleMedium)
                Text(detail, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun TransferStatus(phase: String) {
    val (label, icon) = when (phase) {
        "assigned" -> "New assignment" to MireliIcons.Calendar
        "accepted" -> "Pickup pending" to MireliIcons.LocationOn
        "at_pickup" -> "Boarding" to MireliIcons.Groups
        "in_progress" -> "On the road" to MireliIcons.DirectionsCar
        "completed" -> "Trip completed" to MireliIcons.CheckCircleOutline
        "cancelled" -> "Cancelled" to MireliIcons.Cancel
        "declined" -> "Declined" to MireliIcons.Cancel
        else -> "Status unavailable" to MireliIcons.Info
    }
    val inactive = phase in listOf("cancelled", "declined")
    val colors = MaterialTheme.colorScheme
    val textColor = if (inactive) colors.onSurfaceVariant else colors.onSecondaryContainer
    Row(Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (inactive) textColor else colors.primary, modifier = Modifier.size(18.dp))
        Text(label, color = colors.onSurface, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun TransferSchedule(trip: JSONObject, useDepartureColor: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val departure = trip.departureInstant()
    val service = when (trip.optString("serviceType").lowercase(Locale.ROOT)) {
        "shared" -> "Shared transfer"
        "charter", "private" -> "Private charter"
        else -> "SGR transfer"
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(if (departure == null) "Departure time pending" else "${DepartureTime.format(departure)} EAT",
            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = MireliDesign.NumberFeatures),
            color = if (useDepartureColor) colors.secondary else colors.onSurface)
        Text(if (departure == null) service else "${DepartureDay.format(departure)} • $service",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable
internal fun TransferEndpoints(
    trip: JSONObject, allowIntermediateStops: Boolean = false, showLabels: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val stages = trip.navigationStops()
    val direction = trip.optString("direction")
    val booked = trip.bookedStageIds()
    val doorAddress = trip.hasDoorAddress()
    val pickup = DriverNavigation.primaryStop(stages, direction, "accepted", booked, doorAddress)
    val dropoff = DriverNavigation.primaryStop(stages, direction, "in_progress", booked, doorAddress)
    val otherStops = DriverNavigation.orderedStops(stages, direction)
        .filter { it.id != pickup?.id && it.id != dropoff?.id && it.name.isNotBlank() }
    var expanded by rememberSaveable(trip.optString("id")) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(22.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(MireliIcons.TripOrigin, null,
                    tint = if (showLabels) colors.primary else colors.secondary, modifier = Modifier.size(20.dp))
                VerticalDivider(Modifier.weight(1f), thickness = 2.dp, color = colors.outlineVariant)
            }
            Column(Modifier.weight(1f).padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val label = if (booked.size > 1 && direction == "TO_TERMINUS") "First pickup" else "Pickup"
                EndpointLabel(label, pickup?.name ?: if (doorAddress) "See manifest for the address" else "Confirm pickup with dispatch", showLabels)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (showLabels) MireliIcons.LocationOn else MireliIcons.TripOrigin, null,
                tint = if (showLabels) colors.secondary else colors.primary, modifier = Modifier.size(22.dp))
            val label = if (booked.size > 1 && direction == "FROM_TERMINUS") "First drop-off" else "Drop-off"
            EndpointLabel(label, dropoff?.name ?: if (doorAddress) "See manifest for the address" else "Confirm drop-off with dispatch", showLabels)
        }
        if (allowIntermediateStops && otherStops.isNotEmpty()) {
            TextButton(onClick = { expanded = !expanded }) {
                Icon(MireliIcons.Route, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (expanded) "Hide route stops" else "Show route stops")
            }
            AnimatedVisibility(expanded,
                enter = expandVertically(tween(MireliDesign.DetailDurationMs, easing = FastOutSlowInEasing)) + fadeIn(tween(MireliDesign.DetailDurationMs)),
                exit = shrinkVertically(tween(144)) + fadeOut(tween(144))) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    otherStops.forEach { stop -> Text(stop.name, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun EndpointLabel(label: String, name: String, showLabel: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (showLabel) Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
