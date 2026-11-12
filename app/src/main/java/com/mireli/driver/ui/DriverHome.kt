package com.mireli.driver.ui

import android.content.Context
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.mireli.driver.R
import com.mireli.driver.domain.DriverNavigation
import com.mireli.driver.domain.NavigationStop
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

/** Home top bar: the original Mireli logo, product line and the support bell. */
@Composable
internal fun DriverHomeTopBar(onSupport: () -> Unit) {
    // Same navy-to-teal block as sign-in, so the app opens on the surface the driver just signed into.
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(MireliDesign.Radius.hero))
            .background(Brush.linearGradient(PremiumGradient))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The original logo has a white canvas. Keep that canvas in both themes.
        Surface(shape = RoundedCornerShape(MireliDesign.Radius.tile), color = Color.White) {
            Image(painterResource(R.drawable.mireli_driver_logo), "Mireli original logo",
                Modifier.size(40.dp))
        }
        Spacer(Modifier.width(MireliDesign.Space.sm))
        Column(Modifier.weight(1f)) {
            Text("Mireli Driver", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("SGR last-mile · Mombasa", style = MaterialTheme.typography.labelMedium,
                color = StationMint)
        }
        ThemeIconButton(tint = Color.White)
        IconButton(onClick = onSupport) {
            Icon(MireliIcons.HeadsetMic, "Help and support", tint = Color.White)
        }
    }
}

@Composable
private fun Greeting(name: String?, online: Boolean, verified: Boolean, onOnlineChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.sm)) {
        DriverAvatar(name)
        Column(Modifier.weight(1f)) {
            Text(
                "Good ${partOfDay()}".uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name?.substringBefore(' ') ?: "Driver", style = MaterialTheme.typography.headlineSmall)
                // Only drawn once the server has evaluated every required document
                // as approved, clean and current. No local state can turn it on.
                if (verified) VerifiedBadge(verified = true, compact = true)
            }
        }
        AvailabilityPill(online, onOnlineChange)
    }
}

private fun partOfDay(): String {
    val hour = java.time.LocalTime.now(NairobiZone).hour
    return when (hour) {
        in 0..11 -> "morning"
        in 12..16 -> "afternoon"
        else -> "evening"
    }
}

@Composable
private fun AvailabilityPill(online: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(MireliDesign.Radius.chip))
            .background(if (online) colors.primaryContainer else colors.surfaceVariant)
            .clickable { onChange(!online) }
            .padding(start = 12.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape)
                .background(if (online) colors.primary else colors.onSurfaceVariant)
        )
        Text(
            if (online) "Available" else "Offline",
            style = MaterialTheme.typography.labelLarge,
            color = if (online) colors.onPrimaryContainer else colors.onSurfaceVariant
        )
    }
}

@Composable
private fun EarningsCard(netMinor: Long?, pendingMinor: Long?, completedTrips: Int, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(MireliDesign.Radius.hero))
            .background(Brush.linearGradient(PremiumGradient))
            .clickable(onClick = onOpen)
            .padding(MireliDesign.Space.md),
        verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(MireliIcons.AccountBalanceWallet, null, tint = StationMint,
                modifier = Modifier.size(18.dp))
            Text("Confirmed earnings", style = MaterialTheme.typography.labelMedium,
                color = StationMint, modifier = Modifier.weight(1f))
            Icon(MireliIcons.ChevronRight, "Earnings and payouts",
                tint = Color.White.copy(alpha = .85f), modifier = Modifier.size(18.dp))
        }
        // "KSh" is a unit, not the number: it sits on the baseline at label size so the amount
        // keeps the display weight and the currency does not shout alongside it.
        val amount = netMinor?.let(::money)
        Row {
            if (amount != null && amount.startsWith("KSh ")) {
                Text(
                    "KSh",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = .8f),
                    modifier = Modifier.alignByBaseline().padding(end = 6.dp)
                )
                Text(
                    amount.removePrefix("KSh "),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    modifier = Modifier.alignByBaseline()
                )
            } else {
                Text(
                    amount ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(MireliIcons.Ticket, null, tint = Color.White.copy(alpha = .8f),
                modifier = Modifier.size(14.dp))
            Text(
                buildString {
                    append("$completedTrips completed ")
                    append(if (completedTrips == 1) "journey" else "journeys")
                    if (pendingMinor != null && pendingMinor > 0) append(" · ${money(pendingMinor)} pending")
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = .9f)
            )
        }
    }
}

@Composable
private fun NextTrainHero(
    trip: JSONObject?,
    now: Instant,
    station: String,
    onNavigate: (() -> Unit)?,
    onOpenTrains: () -> Unit
) {
    val departure = trip?.departureInstant()
    val clock = departure?.let(ClockFormat::format)
    val urgent = urgency(now, departure) == Urgency.Now
    val colors = MaterialTheme.colorScheme
    // The hero stays the premium navy-to-teal block in both themes, so the brand reads the same
    // as the sign-in hero the driver just came through.
    val gradient = PremiumGradient
    val onSecondary = Color.White
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(MireliDesign.Radius.hero))
            .background(Brush.linearGradient(gradient))
            .clickable(onClick = onOpenTrains)
            .padding(MireliDesign.Space.md),
        verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(MireliIcons.TrainFront, null, tint = onSecondary, modifier = Modifier.size(20.dp))
            Text(
                if (clock == null) "Next SGR train" else "Next SGR train · $clock",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = .6.sp),
                color = onSecondary.copy(alpha = .8f)
            )
        }
        Text(
            if (departure == null) "No train linked yet" else "Departs ${countdown(now, departure)}",
            style = MaterialTheme.typography.headlineSmall,
            color = onSecondary
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(MireliIcons.Train, null, tint = onSecondary.copy(alpha = .8f), modifier = Modifier.size(16.dp))
            Text(station, style = MaterialTheme.typography.bodyMedium,
                color = onSecondary.copy(alpha = .9f), modifier = Modifier.weight(1f, false))
            if (trip != null) {
                Surface(
                    shape = RoundedCornerShape(MireliDesign.Radius.chip),
                    color = if (urgent) MaterialTheme.colorScheme.tertiaryContainer else Color.White.copy(alpha = .18f)
                ) {
                    Text(
                        "${trip.partySeats()} pax",
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (urgent) MaterialTheme.colorScheme.onTertiaryContainer else onSecondary
                    )
                }
            }
        }
        if (onNavigate != null) {
            Button(
                onClick = onNavigate,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(MireliDesign.Radius.tile),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(MireliIcons.NavArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Navigate to station", fontWeight = FontWeight.SemiBold)
            }
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
                            pending -> "$clock train"
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
                "Your device position, used only while the app is open. It is sent to Mireli's routing service when you ask for directions.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Plain answer to "where do my passengers come from?", in the order a transfer happens. */
@Composable
private fun HowItWorksPanel() {
    var open by rememberSaveable { mutableStateOf(false) }
    MireliPanel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)
        ) {
            Icon(MireliIcons.Ticket, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("How you get passengers", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f))
            Icon(if (open) MireliIcons.ExpandLess else MireliIcons.ExpandMore, "How you get passengers",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Text(
            "Mireli dispatch assigns you the passengers arriving on an SGR service. You never wait for a street hail.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (open) {
            HowStep("1", MireliIcons.Train, "Dispatch sends the transfer",
                "When a train with Mireli passengers is due, the trip appears under Trips with its station and scheduled time.")
            HowStep("2", MireliIcons.Route, "You accept and drive to the terminus",
                "Open the trip for the pickup point, then navigate there.")
            HowStep("3", MireliIcons.Groups, "You meet and board each party",
                "Confirm pickup, board the passengers and record anyone who does not show.")
            HowStep("4", MireliIcons.AccountBalanceWallet, "You complete and get paid",
                "Finished journeys appear in Earnings and settle to the M-Pesa number on your account.")
        }
    }
}

@Composable
private fun HowStep(number: String, icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.sm)) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(number, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StationActions(
    onOpenTrains: () -> Unit,
    onOpenTrips: () -> Unit,
    onOpenEarnings: () -> Unit,
    onOpenAccount: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.sm)) {
        QuickTile("Arrivals", MireliIcons.TrainFront, 0, onOpenTrains, Modifier.weight(1f))
        QuickTile("My trips", MireliIcons.CarFront, 1, onOpenTrips, Modifier.weight(1f))
        QuickTile("Earnings", MireliIcons.ChartBars, 2, onOpenEarnings, Modifier.weight(1f))
        QuickTile("Account", MireliIcons.PersonOutline, 3, onOpenAccount, Modifier.weight(1f))
    }
}

@Composable
private fun QuickTile(label: String, icon: ImageVector, index: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier
            .staggerIn(index)
            .pressScale(interaction)
            .clip(RoundedCornerShape(MireliDesign.Radius.card))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(MireliDesign.Radius.card))
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .heightIn(min = 84.dp)
            .padding(vertical = MireliDesign.Space.sm, horizontal = MireliDesign.Space.sm),
        verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs, Alignment.CenterVertically)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Driver home. Everything is derived from the driver's own assignments and settlement
 * statement; nothing invents a live train position or a payable balance.
 */
@Composable
internal fun DriverHomeDashboard(
    name: String?,
    eligible: Boolean,
    trips: List<JSONObject>?,
    earnings: JSONObject?,
    activeNavigation: (@Composable () -> Unit)?,
    onOpenTrips: () -> Unit,
    onOpenTrains: () -> Unit,
    onOpenEarnings: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onStartNavigation: (String, NavigationStop) -> Unit,
    onOpenMap: () -> Unit
) {
    val context = LocalContext.current
    val now = rememberNow()
    val nextTrip = remember(trips) {
        val active = trips.orEmpty().filter { it.isActiveTransfer() }.sortedWith(transferOrder())
        active.firstOrNull { (it.departureInstant() ?: Instant.MAX).isAfter(Instant.now().minusSeconds(15 * 60)) }
            ?: active.firstOrNull()
    }
    val completed = remember(trips) { trips.orEmpty().count { it.optString("phase") == "completed" } }
    val net = remember(earnings) {
        val totals = earnings?.optJSONArray("totals") ?: return@remember null
        val paid = (0 until totals.length()).mapNotNull(totals::optJSONObject)
            .filter { it.optString("status") == "completed" }
        if (paid.isEmpty()) null else paid.sumOf { it.optLong("netMinor", 0L) }
    }
    val pending = remember(earnings) {
        val totals = earnings?.optJSONArray("totals") ?: return@remember null
        val open = (0 until totals.length()).mapNotNull(totals::optJSONObject)
            .filter { it.optString("status") != "completed" }
            .sumOf { it.optLong("netMinor", 0L) }
        if (open > 0) open else null
    }
    val station = nextTrip?.terminusName() ?: "Mombasa Terminus"
    val stationStop = nextTrip?.navigationStops()?.firstOrNull { it.name == station && it.valid }
    var online by rememberSaveable {
        mutableStateOf(context.getSharedPreferences("availability", Context.MODE_PRIVATE).getBoolean("available", false))
    }
    LaunchedEffect(online) {
        context.getSharedPreferences("availability", Context.MODE_PRIVATE)
            .edit().putBoolean("available", online).apply()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.md)) {
        Greeting(name, online && eligible, eligible) { requested ->
            online = requested
            if (!eligible) onOpenAccount()
        }
        LivePositionCard(onOpenMap)
        EarningsCard(net, pending, completed, onOpenEarnings)
        NextTrainHero(
            trip = nextTrip,
            now = now,
            station = station,
            onNavigate = stationStop?.let { stop ->
                { nextTrip?.optString("id")?.takeIf { it.isNotBlank() }?.let { onStartNavigation(it, stop) } }
            },
            onOpenTrains = onOpenTrains
        )
        activeNavigation?.invoke()
        HowItWorksPanel()
        if (!eligible) {
            MireliPanel(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(MireliIcons.VerifiedUser, null, tint = MaterialTheme.colorScheme.tertiary)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Approval needed before you can accept transfers",
                            style = MaterialTheme.typography.titleSmall)
                        Text("Upload your driver and vehicle documents for compliance review. Until then you can review schedules and trips but cannot board passengers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onOpenAccount, contentPadding = PaddingValues(0.dp)) {
                            Text("Open my documents")
                        }
                    }
                }
            }
        }
        PickupQueue(
            trips = trips.orEmpty(),
            emptyText = if (nextTrip == null)
                "No passengers waiting. Accepted pickup requests appear here with the train they arrive on."
            else "Every request on this transfer is already on board or closed.",
            onOpenTrips = onOpenTrips,
            onOpenTrip = onOpenTrip
        )
        StationActions(onOpenTrains, onOpenTrips, onOpenEarnings, onOpenAccount)
        Text(
            "Location and arrival times come from your dispatch assignments. Train times are scheduled departures, not live railway telemetry.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
        )
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
