package com.mireli.driver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.mireli.driver.domain.DriverNavigation
import com.mireli.driver.domain.NavigationStop
import com.mireli.driver.domain.SgrTimetable
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NairobiZone = ZoneId.of("Africa/Nairobi")
private val ClockFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(NairobiZone)
private val DayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("en-KE")).withZone(NairobiZone)

/** A clock that ticks once a second while a countdown is on screen. */
@Composable
internal fun rememberNow(intervalMs: Long = 1_000L): Instant {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(intervalMs)
            value = Instant.now()
        }
    }
    return now
}

internal fun JSONObject.parties(): List<JSONObject> {
    val array = optJSONArray("passengers") ?: return emptyList()
    return (0 until array.length()).mapNotNull(array::optJSONObject)
}

internal fun JSONObject.departureClock(): String? = departureInstant()?.let(ClockFormat::format)

internal fun JSONObject.departureDay(): String? = departureInstant()?.let(DayFormat::format)

internal fun JSONObject.partySeats(): Int = parties().sumOf { it.optInt("seats", 1) }

internal fun JSONObject.seatsWaiting(): Int =
    parties().sumOf { it.optInt("remaining", it.optInt("unresolvedSeats", 0)) }

/** The SGR terminus this transfer belongs to, taken from the ordered stage list. */
internal fun JSONObject.terminusName(): String? {
    val stops = navigationStops().filter { it.name.isNotBlank() }
    if (stops.isEmpty()) return null
    return when (optString("direction")) {
        "TO_TERMINUS" -> stops.last().name
        else -> stops.first().name
    }
}

/** Countdown from now to a scheduled time, phrased for a countdown label. */
internal fun countdown(from: Instant, target: Instant): String {
    val seconds = Duration.between(from, target).seconds
    if (seconds <= -60) return "left ${(-seconds) / 60} min ago"
    if (seconds < 60) return "departing now"
    val minutes = seconds / 60
    if (minutes < 60) return "in $minutes min"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) "in $hours h" else "in $hours h $rest min"
}

private enum class Urgency { Now, Soon, Later }

private fun urgency(now: Instant, target: Instant?): Urgency {
    val minutes = target?.let { Duration.between(now, it).toMinutes() } ?: 999L
    return when {
        minutes <= 15 -> Urgency.Now
        minutes <= 60 -> Urgency.Soon
        else -> Urgency.Later
    }
}

internal enum class ChipTone { Positive, Neutral, Alert }

@Composable
internal fun MireliStatusChip(text: String, tone: ChipTone = ChipTone.Neutral) {
    val colors = MaterialTheme.colorScheme
    val background = when (tone) {
        ChipTone.Positive -> colors.primaryContainer
        ChipTone.Neutral -> colors.surfaceVariant
        ChipTone.Alert -> colors.tertiaryContainer
    }
    val foreground = when (tone) {
        ChipTone.Positive -> colors.onPrimaryContainer
        ChipTone.Neutral -> colors.onSurfaceVariant
        ChipTone.Alert -> colors.onTertiaryContainer
    }
    Row(
        Modifier.clip(RoundedCornerShape(MireliDesign.Radius.chip)).background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tone == ChipTone.Positive) Box(Modifier.size(6.dp).clip(CircleShape).background(foreground))
        Text(text, style = MaterialTheme.typography.labelMedium, color = foreground)
    }
}

/** White card with a hairline border — the base surface for every panel. */
@Composable
internal fun MireliPanel(
    modifier: Modifier = Modifier,
    padding: Dp = MireliDesign.PanelPadding,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(MireliDesign.Radius.card))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(MireliDesign.Radius.card))
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.sm),
        content = content
    )
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(action, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DriverAvatar(name: String?, size: Dp = 44.dp) {
    val initials = name?.trim()?.takeIf { it.isNotEmpty() }
        ?.split(' ')?.take(2)?.mapNotNull { it.firstOrNull()?.uppercaseChar() }?.joinToString("")
        ?: "MD"
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
internal fun DriverHomeTopBar(onSupport: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Home", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        ThemeIconButton(tint = MaterialTheme.colorScheme.secondary)
        IconButton(onClick = onSupport) {
            Icon(MireliIcons.HeadsetMic, "Help and support", tint = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun DriverIdentity(name: String?, verified: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DriverAvatar(name, size = 32.dp)
        Text(name ?: "Driver", modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.titleMedium)
        if (verified) VerifiedBadge(verified = true, compact = true)
    }
}

@Composable
private fun ApplicationPanel(status: String?, onOpenAccount: () -> Unit, onRefresh: () -> Unit) {
    val title = when (status) {
        "draft" -> "Complete your application"
        "submitted" -> "Application in review"
        "changes_requested" -> "Changes requested"
        "approved" -> "Check your driver documents"
        "suspended" -> "Driver account suspended"
        else -> "Check application status"
    }
    val detail = when (status) {
        "draft" -> "Save your driver and vehicle details, upload the required documents, then submit for review."
        "submitted" -> "Mireli is reviewing your documents. Check your application for updates."
        "changes_requested" -> "Read the reviewer’s feedback, update your documents and submit again."
        "approved" -> "Open your application to check which documents need attention before you can take transfers."
        "suspended" -> "Open your account for details or contact Mireli support."
        else -> "Refresh to load your application and see what to do next."
    }
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            DepartureButton(
                onClick = if (status == null) onRefresh else onOpenAccount,
                enabled = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(when (status) {
                    null -> "Refresh account"
                    "draft" -> "Continue onboarding"
                    else -> "View application"
                })
            }
        }
}

@Composable
private fun NextTransferCard(
    trip: JSONObject,
    now: Instant,
    stop: NavigationStop?,
    onStartNavigation: (String, NavigationStop) -> Unit,
    onOpenTrip: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tripId = trip.optString("id")
    val departure = trip.departureInstant()
    val phase = trip.optString("phase")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Next transfer", style = MaterialTheme.typography.titleLarge)
            if (departure != null) {
                val day = if (departure.atZone(NairobiZone).toLocalDate() == now.atZone(NairobiZone).toLocalDate())
                    "Today" else DayFormat.format(departure)
                Text(day, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (departure != null) {
                Text(ClockFormat.format(departure), style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 49.sp, lineHeight = 54.sp), color = colors.secondary)
                Text("EAT", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            } else {
                Text("Departure time pending", style = MaterialTheme.typography.titleMedium)
            }
            Text(when (phase) {
                "assigned" -> "Assigned"
                "accepted" -> "Accepted"
                "at_pickup" -> "At pickup"
                else -> "In progress"
            }, style = MaterialTheme.typography.labelLarge,
                color = if (phase == "assigned") colors.secondary else colors.primary)
        }
        TransferEndpoints(trip, showLabels = false)
        val seats = trip.partySeats()
        if (seats > 0) Text(if (seats == 1) "1 passenger" else "$seats passengers",
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        DepartureButton(onClick = { onOpenTrip(tripId) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (phase == "assigned") "Review & accept" else "Open transfer",
                style = MaterialTheme.typography.titleMedium)
        }
        if (stop != null && phase != "in_progress") {
            TextButton(onClick = { onStartNavigation(tripId, stop) }, modifier = Modifier.align(Alignment.End)) {
                Text("Navigate")
            }
        }
    }
}

/** Compact published-timetable awareness, shown when nothing is assigned. */
@Composable
private fun SgrTodayPanel(now: Instant) {
    val events = remember(now.toEpochMilli() / 60_000L) { SgrTimetable.upcoming(now) }
    var open by rememberSaveable { mutableStateOf(false) }
    MireliPanel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)
        ) {
            Icon(MireliIcons.TrainFront, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp))
            Text("SGR today · Mombasa Terminus", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f))
            Icon(if (open) MireliIcons.ExpandLess else MireliIcons.ExpandMore, "SGR timetable",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        val next = events.firstOrNull()
        if (next != null) {
            Text(
                "${next.kind} at ${next.clock} EAT · ${next.service.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (open) {
            events.forEach { event ->
                val minutes = Duration.between(now, event.at).toMinutes()
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.sm)
                ) {
                    Text(
                        event.clock,
                        style = MaterialTheme.typography.titleMedium
                            .copy(fontFeatureSettings = MireliDesign.NumberFeatures),
                        color = if (minutes <= 15) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                    )
                    MireliStatusChip(
                        event.kind,
                        if (minutes <= 15) ChipTone.Alert else ChipTone.Positive
                    )
                    Text(
                        event.service.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                "Published Madaraka Express timetable. Confirm with Kenya Railways; your dispatch assignment stays authoritative.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PickupQueue(
    trips: List<JSONObject>,
    emptyText: String,
    onOpenTrips: () -> Unit,
    onOpenTrip: (String) -> Unit
) {
    val rows = remember(trips) {
        trips.filter { it.optString("phase") in listOf("assigned", "accepted", "at_pickup") }
            .flatMap { trip -> trip.parties().map { trip to it } }
    }
    SectionHeader(
        if (rows.isEmpty()) "Pickup queue" else "Pickup queue (${rows.size})",
        if (rows.isNotEmpty()) "View all" else null,
        onOpenTrips
    )
    if (rows.isEmpty()) {
        MireliPanel(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(MireliIcons.Groups, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(emptyText, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    MireliPanel(Modifier.fillMaxWidth(), padding = 0.dp) {
        rows.take(4).forEachIndexed { index, (trip, party) ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PickupRow(trip, party, index, onOpenTrip)
        }
    }
}

@Composable
private fun PickupRow(trip: JSONObject, party: JSONObject, index: Int, onOpenTrip: (String) -> Unit) {
    val seats = party.optInt("seats", 1)
    val clock = trip.departureClock()
    val pending = trip.optString("phase") != "in_progress"
    Row(
        Modifier.fillMaxWidth().clickable { onOpenTrip(trip.optString("id")) }
            .padding(horizontal = MireliDesign.PanelPadding, vertical = MireliDesign.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.sm)
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(MireliIcons.PersonOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                party.optString("name").ifBlank { "SGR Passenger" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append("$seats pax · ")
                    append(
                        when {
                            clock == null -> party.optString("stageName").takeIf { it.isNotBlank() }
                                ?: "Departure time pending"
                            pending -> "$clock cab departure"
                            else -> "on board · $clock"
                        }
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (pending) {
            MireliStatusChip(
                if (index == 0) "Next" else if (index == 1) "After" else "Later",
                if (index == 0) ChipTone.Positive else ChipTone.Neutral
            )
        }
        Icon(MireliIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp))
    }
}

/**
 * The driver's own position, live. The tiny map is a preview: tapping it opens the full map with
 * speed, accuracy and guidance.
 */
@Composable
private fun LivePositionCard(onOpenMap: () -> Unit) {
    val position = rememberDriverPosition()
    val point = position.point
    MireliPanel(Modifier.fillMaxWidth(), padding = 0.dp) {
        Box(Modifier.fillMaxWidth().height(148.dp)) {
            if (point != null) {
                NavigationMap(
                    target = null, location = point, fix = position.fix, route = null,
                    modifier = Modifier.fillMaxSize(), interactive = false, controls = false,
                    rotateVehicle = false
                )
                Box(Modifier.matchParentSize().clickable(onClick = onOpenMap))
            } else {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onOpenMap).padding(MireliDesign.PanelPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(MireliIcons.LocationOn, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        position.error ?: "Waiting for a location fix…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        Column(Modifier.padding(MireliDesign.PanelPadding),
            verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(MireliIcons.CarFront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("Your location", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenMap, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Open map", style = MaterialTheme.typography.labelLarge)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MireliStatusChip(
                    position.accuracy?.let { "±${it.toInt()} m" } ?: "No fix yet",
                    if (position.accuracy != null) ChipTone.Positive else ChipTone.Neutral
                )
                position.speedKmh?.takeIf { it > 2f }?.let { MireliStatusChip("%.0f km/h".format(it)) }
                position.ageSeconds?.let {
                    MireliStatusChip("updated ${it.coerceAtLeast(0)}s ago", if (it < 15) ChipTone.Positive else ChipTone.Neutral)
                }
            }
            Text(
                "Location can be shared with Mireli during an assigned transfer.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun DriverHomeDashboard(
    name: String?,
    eligible: Boolean,
    verified: Boolean,
    applicationStatus: String?,
    tripsCurrent: Boolean,
    trips: List<JSONObject>?,
    activeNavigation: (@Composable () -> Unit)?,
    onRefresh: () -> Unit,
    onOpenTrips: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onStartNavigation: (String, NavigationStop) -> Unit,
    onOpenMap: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.md)) {
        DriverIdentity(name, verified)
        if (!eligible) {
            ApplicationPanel(applicationStatus, onOpenAccount, onRefresh)
            return@Column
        }

        val now = rememberNow()
        val currentTrips = trips.orEmpty().takeIf { tripsCurrent }.orEmpty()
        val nextTrip = remember(currentTrips) {
            currentTrips.filter { it.isActiveTransfer() }.minWithOrNull(transferOrder())
        }
        if (nextTrip != null) {
            val stops = nextTrip.navigationStops()
            val stationStop = stops.firstOrNull { it.valid && it.name == nextTrip.terminusName() }
                ?: stops.firstOrNull { it.valid }
            NextTransferCard(nextTrip, now, stationStop, onStartNavigation, onOpenTrip)
            val localDate = now.atZone(NairobiZone).toLocalDate()
            val later = currentTrips.filter { trip ->
                trip.isActiveTransfer() && trip.optString("id") != nextTrip.optString("id") &&
                    trip.departureInstant()?.let { it > now && it.atZone(NairobiZone).toLocalDate() == localDate } == true
            }.sortedBy { it.departureInstant() }
            if (later.isNotEmpty()) {
                Text("Later today", style = MaterialTheme.typography.titleMedium)
                later.take(3).forEachIndexed { index, trip ->
                    TextButton(onClick = { onOpenTrip(trip.optString("id")) },
                        modifier = Modifier.fillMaxWidth().staggerIn(index), contentPadding = PaddingValues(0.dp)) {
                        Text(trip.departureClock().orEmpty(), color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(12.dp))
                        Text(trip.optString("routeName").ifBlank { "SGR transfer" }, Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface)
                        Icon(MireliIcons.ChevronRight, null, Modifier.size(18.dp))
                    }
                }
            }
            activeNavigation?.invoke()
            if (currentTrips.any { it.isActiveTransfer() && it.parties().isNotEmpty() }) {
                PickupQueue(
                    trips = currentTrips,
                    emptyText = "Passenger groups will appear here when a pickup is due.",
                    onOpenTrips = onOpenTrips,
                    onOpenTrip = onOpenTrip
                )
            }
        } else if (tripsCurrent) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No transfers assigned", style = MaterialTheme.typography.headlineSmall)
                    Text("Your next scheduled transfer will appear here when Mireli assigns it.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onOpenTrips) { Text("View trips") }
                }
        }
        SgrTodayPanel(now)
        LivePositionCard(onOpenMap)
    }
}

// ---------------------------------------------------------------------------
// Station arrivals
// ---------------------------------------------------------------------------

private data class Departure(val time: Instant?, val trip: JSONObject)

/** Station board built only from scheduled times on the driver's own assignments. */
@Composable
internal fun StationArrivalsPanel(
    trips: List<JSONObject>?,
    eligible: Boolean,
    stationHint: String?,
    onOpenTrip: (String) -> Unit,
    onOpenTrips: () -> Unit
) {
    val now = rememberNow()
    val departures = remember(trips) {
        trips.orEmpty()
            .filter { it.isActiveTransfer() && it.departureInstant() != null }
            .map { Departure(it.departureInstant(), it) }
            .sortedBy { it.time ?: Instant.MAX }
    }
    val next = departures.firstOrNull()
    val station = next?.trip?.terminusName() ?: stationHint ?: "Mombasa Terminus"
    var info by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Station arrivals", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { info = true }) {
                Icon(MireliIcons.Info, "About these arrival times", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        StationHeadCard(station, departures.size)
        if (next == null) {
            MireliPanel(Modifier.fillMaxWidth()) {
                Text("No upcoming SGR departures", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Departures appear when dispatch assigns you a transfer with a scheduled train. The app has no separate public train feed yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onOpenTrips) { Text("See my trips") }
            }
        } else {
            NextTrainCard(next, now, onOpenTrip)
            SectionHeader("Upcoming departures", if (departures.size > 1) "All trips" else null, onOpenTrips)
            MireliPanel(Modifier.fillMaxWidth(), padding = 0.dp) {
                departures.take(5).forEachIndexed { index, departure ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DepartureRow(departure, now, index == 0, onOpenTrip)
                }
            }
            val withParties = departures.filter { it.trip.parties().isNotEmpty() }
            if (withParties.isNotEmpty()) {
                val count = withParties.sumOf { it.trip.parties().size }
                SectionHeader("Passenger requests for ${next.trip.departureClock() ?: "the next train"} ($count)")
                MireliPanel(Modifier.fillMaxWidth(), padding = 0.dp) {
                    withParties.flatMap { departure -> departure.trip.parties().map { departure.trip to it } }
                        .take(5)
                        .forEachIndexed { index, (trip, party) ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            RequestRow(trip, party, onOpenTrip)
                        }
                }
            }
            if (!eligible) {
                DriverNotice(MireliIcons.VerifiedUser, "Approval needed",
                    "You can read the board now; accepting and boarding unlock after compliance approval.")
            }
        }
        Text(
            "Source: Mireli dispatch assignments · scheduled times in EAT. Live Kenya Railways telemetry is not connected, so no arrival here is labelled real-time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            title = { Text("Where these times come from") },
            text = {
                Text(
                    "Every departure on this board is the scheduled train time attached to a transfer dispatch has assigned to you. " +
                        "Mireli does not receive a live Kenya Railways feed, so the app never claims a train is running on time or delayed. " +
                        "If operations reports a delay, treat the dispatch message as authoritative."
                )
            },
            confirmButton = { TextButton(onClick = { info = false }) { Text("Got it") } }
        )
    }
}

@Composable
private fun StationHeadCard(station: String, departures: Int) {
    val now = rememberNow(60_000L)
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(MireliDesign.Radius.card))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(MireliDesign.Radius.card))
            .padding(MireliDesign.Space.md),
        horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(MireliDesign.Radius.tile))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(MireliIcons.Train, null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(station, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(
                "$departures scheduled · ${ClockFormat.format(now)} EAT",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NextTrainCard(next: Departure, now: Instant, onOpenTrip: (String) -> Unit) {
    val departure = next.time ?: return
    val urgent = urgency(now, departure) == Urgency.Now
    MireliPanel(Modifier.fillMaxWidth()) {
        Text("Next train", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                ClockFormat.format(departure),
                style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = MireliDesign.NumberFeatures),
                color = if (urgent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
            )
            Text("EAT", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            MireliStatusChip(countdown(now, departure), if (urgent) ChipTone.Alert else ChipTone.Positive)
        }
        Text(
            "${next.trip.departureDay() ?: "Date pending"} · ${next.trip.optString("routeName").ifBlank { "SGR transfer" }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(MireliIcons.Seat, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text("${next.trip.partySeats()} passengers · ${next.trip.parties().size} requests",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { onOpenTrip(next.trip.optString("id")) }) { Text("Open trip") }
        }
    }
}

@Composable
private fun DepartureRow(departure: Departure, now: Instant, isNext: Boolean, onOpenTrip: (String) -> Unit) {
    val time = departure.time ?: return
    Row(
        Modifier.fillMaxWidth().clickable { onOpenTrip(departure.trip.optString("id")) }
            .padding(horizontal = MireliDesign.PanelPadding, vertical = MireliDesign.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.sm)
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(MireliDesign.Radius.tile))
                .background(if (isNext) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(MireliIcons.Train, null,
                tint = if (isNext) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ClockFormat.format(time), style = MaterialTheme.typography.titleMedium)
            Text(
                "${countdown(now, time)} · ${departure.trip.partySeats()} pax",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(MireliIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun RequestRow(trip: JSONObject, party: JSONObject, onOpenTrip: (String) -> Unit) {
    val seats = party.optInt("seats", 1)
    val remaining = party.optInt("remaining", 0)
    Row(
        Modifier.fillMaxWidth().clickable { onOpenTrip(trip.optString("id")) }
            .padding(horizontal = MireliDesign.PanelPadding, vertical = MireliDesign.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.sm)
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(MireliIcons.PersonOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                party.optString("name").ifBlank { "SGR Passenger" },
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append("$seats pax · ")
                    val drop = party.optString("stageName").takeIf { it.isNotBlank() }
                    if (drop != null) append("To $drop · ") else append("Terminus transfer · ")
                    append(if (remaining > 0) "$remaining waiting" else "All aboard")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (remaining > 0) MireliStatusChip("New", ChipTone.Alert)
        Icon(MireliIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp))
    }
}
