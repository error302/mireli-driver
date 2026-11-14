package com.mireli.driver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.mireli.driver.domain.NavigationStop
import com.mireli.driver.TrainSchedule
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
    DriverSectionHeader("Mireli Driver") {
        IconButton(onClick = onSupport, modifier = Modifier.size(MireliDesign.TouchTarget)) {
            Icon(MireliIcons.HeadsetMic, "Help and support", tint = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
internal fun DriverIdentity(name: String?, verified: Boolean, hour: Int) {
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DriverAvatar(name, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(greeting, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name ?: "Driver", modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleMedium)
                if (verified) VerifiedBadge(verified = true, compact = true)
            }
            Text("Let’s start earning!", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
    DriverOverviewSurface {
        Text(title, style = MaterialTheme.typography.titleMedium)
        when (status) {
            "draft" -> {
                DepartureNote("Driver details and verification documents")
                TextButton(onClick = onOpenAccount) {
                    Text("Continue onboarding", color = MaterialTheme.colorScheme.secondary)
                }
            }
            null -> TextButton(onClick = onRefresh) { Text("Refresh account", color = MaterialTheme.colorScheme.secondary) }
            else -> TextButton(onClick = onOpenAccount) { Text("View application", color = MaterialTheme.colorScheme.secondary) }
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
    DriverOverviewSurface {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (phase == "in_progress") "Current trip" else "Next assignment", style = MaterialTheme.typography.titleMedium)
            if (departure != null) {
                val day = if (departure.atZone(NairobiZone).toLocalDate() == now.atZone(NairobiZone).toLocalDate())
                    "Today" else DayFormat.format(departure)
                Text(day, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (departure != null) {
                Text(ClockFormat.format(departure), style = MaterialTheme.typography.transferClock, color = colors.onSurface)
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
                color = if (phase == "in_progress") colors.primary else colors.onSurfaceVariant)
        }
        TransferEndpoints(trip, showLabels = false)
        val seats = trip.partySeats()
        if (seats > 0) Text(if (seats == 1) "1 passenger" else "$seats passengers",
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        TextButton(onClick = { onOpenTrip(tripId) }) {
            Text(if (phase == "assigned") "Review & accept" else "Open transfer", color = colors.secondary)
        }
        if (stop != null) {
            TextButton(onClick = { onStartNavigation(tripId, stop) }, modifier = Modifier.align(Alignment.End)) {
                Text(if (phase == "in_progress") "Navigate to drop-off" else "Navigate to pickup")
            }
        }
    }
}

@Composable
private fun SgrTodayPanel(now: Instant, schedule: TrainSchedule, onRefresh: () -> Unit) {
    val services = schedule.services.orEmpty()
    val today = now.atZone(SgrTimetable.zone).toLocalDate()
    val upcoming = remember(services, now.epochSecond / 60) { SgrTimetable.upcoming(services, now) }
    val next = upcoming.filter { it.at >= now }.take(3)
    var fullTimetable by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(MireliIcons.TrainFront, null, Modifier.size(19.dp), tint = colors.secondary)
                Column(Modifier.weight(1f)) {
                    Text("Mombasa Terminus", style = MaterialTheme.typography.titleMedium)
                    Text("Scheduled trains · EAT", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (services.isNotEmpty()) {
                    TextButton(onClick = { fullTimetable = true }, contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.heightIn(min = MireliDesign.TouchTarget)) {
                        Text("Timetable", color = colors.secondary)
                    }
                }
            }
            if (schedule.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (schedule.failed) {
                DepartureNote(if (schedule.services == null) "Train times unavailable." else "Showing last loaded times.")
                TextButton(onClick = onRefresh) { Text("Refresh timetable") }
            }
            if (schedule.services?.isEmpty() == true) DepartureNote("No scheduled services.")
            next.forEach { event ->
                HorizontalDivider(color = colors.outlineVariant)
                TrainTimeRow(event, event.at.atZone(SgrTimetable.zone).toLocalDate() != today)
            }
        }
    }
    if (fullTimetable) {
        val tomorrow = today.plusDays(1)
        val times = remember(services, today) {
            listOf(today, tomorrow).map { date ->
                date to SgrTimetable.eventsOn(services, date)
            }
        }
        AlertDialog(
            onDismissRequest = { fullTimetable = false },
            title = { Text("Mombasa Terminus") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)) {
                    DepartureNote("Kenya Railways · Scheduled times · EAT")
                    times.forEach { (date, events) ->
                        val heading = if (date == today) "Today" else "Tomorrow"
                        Text(heading, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = MireliDesign.Space.sm))
                        if (events.isEmpty()) DepartureNote("No scheduled services.")
                        events.forEach { TrainTimeRow(it, tomorrow = false) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { fullTimetable = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun TrainTimeRow(event: SgrTimetable.Event, tomorrow: Boolean) {
    val colors = MaterialTheme.colorScheme
    val movement = if (event.service.movement == SgrTimetable.Movement.Arrival) "Arrives from Nairobi" else "Departs for Nairobi"
    Row(
        Modifier.fillMaxWidth().padding(vertical = MireliDesign.Space.xs),
        horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(ClockFormat.format(event.at), style = MaterialTheme.typography.timetableClock)
        Column(Modifier.weight(1f)) {
            Text(movement, style = MaterialTheme.typography.titleSmall)
            Text(if (tomorrow) "${event.service.name} · ${DayFormat.format(event.at)}" else event.service.name,
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
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
            PickupRow(trip, party, onOpenTrip)
        }
    }
}

@Composable
private fun PickupRow(trip: JSONObject, party: JSONObject, onOpenTrip: (String) -> Unit) {
    val pending = trip.optString("phase") != "in_progress"
    val seats = party.optInt("seats", 1)
    val clock = trip.departureClock()
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
        Icon(MireliIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp))
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
    onRefresh: () -> Unit,
    onOpenTrips: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onStartNavigation: (String, NavigationStop) -> Unit,
    onOpenMap: () -> Unit,
    showLocationPreview: Boolean = true,
    renewalNote: String? = null,
    schedule: TrainSchedule = TrainSchedule(),
    onRefreshTimetable: () -> Unit = {}
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.md)) {
        val now = rememberNow(15_000L)
        DriverIdentity(name, verified, hour = now.atZone(NairobiZone).hour)
        if (renewalNote != null) {
            DepartureNote(renewalNote)
            TextButton(onClick = onOpenAccount) { Text("Renew documents") }
        }
        if (!eligible) {
            if (renewalNote == null) ApplicationPanel(applicationStatus, onOpenAccount, onRefresh)
            if (showLocationPreview) HomeLocationPreview(onOpenMap)
            SgrTodayPanel(now, schedule, onRefreshTimetable)
            return@Column
        }

        val currentTrips = trips.orEmpty().takeIf { tripsCurrent }.orEmpty()
        val nextTrip = remember(currentTrips) {
            currentTrips.filter { it.isActiveTransfer() }.minWithOrNull(transferOrder())
        }
        if (nextTrip != null) {
            NextTransferCard(nextTrip, now, nextTrip.primaryNavigationStop(), onStartNavigation, onOpenTrip)
        } else if (tripsCurrent) {
            Text("No assignments", style = MaterialTheme.typography.headlineSmall)
        }
        if (showLocationPreview) HomeLocationPreview(onOpenMap)
        if (nextTrip != null) {
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
            if (currentTrips.any { it.isActiveTransfer() && it.parties().isNotEmpty() }) {
                PickupQueue(
                    trips = currentTrips,
                    emptyText = "Passenger groups will appear here when a pickup is due.",
                    onOpenTrips = onOpenTrips,
                    onOpenTrip = onOpenTrip
                )
            }
        }
        SgrTodayPanel(now, schedule, onRefreshTimetable)
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
        Text("Next transfer", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                ClockFormat.format(departure),
                style = MaterialTheme.typography.drivingMetric,
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
