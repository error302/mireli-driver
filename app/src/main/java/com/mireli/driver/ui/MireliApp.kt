package com.mireli.driver.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mireli.driver.DriverViewModel
import com.mireli.driver.R
import com.mireli.driver.data.RepositoryFactory
import com.mireli.driver.domain.*
import java.text.NumberFormat
import java.util.Locale

private val Navy = Color(0xFF063555)
private val Teal = Color(0xFF007F79)
private val Mist = Color(0xFFF3F5F8)
private val Ink = Color(0xFF142D40)
private val Muted = Color(0xFF5A6E7C)
private val Palette = lightColorScheme(primary = Teal, secondary = Navy, background = Mist,
    surface = Color.White, onSurface = Ink, onBackground = Ink, surfaceVariant = Color(0xFFEAF0F3))

fun money(minor: Long): String = "KSh " + NumberFormat.getNumberInstance(Locale.forLanguageTag("en-KE")).apply {
    minimumFractionDigits = if (minor % 100L == 0L) 0 else 2
    maximumFractionDigits = 2
}.format(java.math.BigDecimal.valueOf(minor, 2))

private fun TripStage.label() = when (this) {
    TripStage.ASSIGNED -> "Awaiting acceptance"
    TripStage.ACCEPTED -> "Ready for pickup"
    TripStage.AT_PICKUP -> "Boarding passengers"
    TripStage.IN_PROGRESS -> "Journey in progress"
    TripStage.COMPLETED -> "Completed"
}

@Composable
fun MireliApp(vm: DriverViewModel = viewModel()) {
    val trips by vm.trips.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val pendingCount by vm.pendingCount.collectAsStateWithLifecycle()
    val syncMessage by vm.syncMessage.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var help by rememberSaveable { mutableStateOf(false) }
    var onboarding by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val selected = trips.find { it.id == selectedId }
    val listState = rememberLazyListState()
    LaunchedEffect(tab, selectedId, selected?.stage) { listState.scrollToItem(0) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.dismissMessage() } }
    BackHandler(selectedId != null) { selectedId = null }
    MaterialTheme(colorScheme = Palette, shapes = Shapes(
        small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))) {
        if (!RepositoryFactory.isDemo) {
            ConnectionRequired()
            return@MaterialTheme
        }
        if (onboarding) {
            OnboardingScreen(onClose = { onboarding = false })
            return@MaterialTheme
        }
        Scaffold(containerColor = Mist, snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (selected == null) NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    listOf("Today" to MireliIcons.GridView, "Trips" to MireliIcons.Route,
                        "Earnings" to MireliIcons.AccountBalanceWallet, "Account" to MireliIcons.PersonOutline)
                        .forEachIndexed { index, (title, icon) ->
                            NavigationBarItem(selected = tab == index, onClick = { tab = index },
                                icon = { Icon(icon, null) }, label = { Text(title) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFFDDF2EE)))
                        }
                }
            }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding),
                state = listState,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (selected != null) IconButton(onClick = { selectedId = null }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                        } else {
                            Image(painterResource(R.drawable.mireli_driver_logo), "Mireli", Modifier.size(44.dp))
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("mireli", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
                            Text("D R I V E R", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                        IconButton(onClick = { help = true }, modifier = Modifier.background(Color.White, CircleShape)) {
                            Icon(MireliIcons.HeadsetMic, "Help and support", tint = Navy)
                        }
                    }
                }
                item {
                    Surface(color = Color(0xFFE4EDF6), shape = RoundedCornerShape(12.dp)) {
                        Text("PREVIEW · Sample trips, no live bookings or tracking",
                            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            color = Navy, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (!connected || pendingCount > 0 || syncMessage != null) item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (pendingCount > 0) "$pendingCount action waiting to sync · not confirmed" else if (!connected) "Offline simulation" else "Sync result", fontWeight = FontWeight.Bold)
                            Text(syncMessage ?: "Only boarding can be saved while the local simulator is offline. Accepting, starting and completing need confirmation.")
                            if (!connected) Button(onClick = { vm.setConnected(true) }, enabled = !busy) { Text("Resume sample sync") }
                        }
                    }
                }
                if (selected != null) {
                    item { Heading(selected.id, if (selected.actionable) selected.stage.label() else selected.assignment.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    if (!selected.actionable) item { InfoCard(MireliIcons.Info, "Assignment unavailable", selected.declineReason ?: "Dispatch has changed this assignment.") }
                    item { RouteCard(selected) }
                    item { ProgressCard(selected) }
                    if (selected.stage == TripStage.AT_PICKUP) {
                        item { Heading("Passenger manifest", selected.boardedSeats.toString() + " of " + selected.bookedSeats + " passengers boarded") }
                        items(selected.passengers, key = { it.id }) { passenger ->
                            PassengerCard(passenger, busy || pendingCount > 0) { vm.command(selected, it) }
                        }
                    } else item {
                        InfoCard(MireliIcons.Groups, selected.bookedSeats.toString() + " passengers",
                            if (selected.stage == TripStage.COMPLETED) "Journey complete. Your preview history is saved."
                            else "The passenger manifest opens when you arrive at pickup.")
                    }
                    item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { TripAction(selected, busy || pendingCount > 0) { vm.command(selected, it) } } }
                    item { InfoCard(MireliIcons.Info, "Preview journey",
                        "GPS, dispatch contact, identity verification and live payments are not connected.") }
                } else when (tab) {
                    0 -> {
                        item { Heading("A good day to drive.", "Your Mombasa SGR schedule, at a glance.") }
                        item { InfoCard(MireliIcons.VerifiedUser, "Driver preview", "Explore the journey before going live.") }
                        item { Button(onClick = { onboarding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Start driver onboarding") } }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Stat("Scheduled", trips.count { it.actionable && it.stage != TripStage.COMPLETED }.toString(), Modifier.weight(1f))
                                Stat("Completed", trips.count { it.stage == TripStage.COMPLETED }.toString(), Modifier.weight(1f))
                            }
                        }
                        item { SectionTitle("Your next journey", "SGR TRANSFERS") }
                        val next = trips.firstOrNull { it.actionable && it.stage != TripStage.COMPLETED }
                        if (next != null) item { JourneyCard(next) { selectedId = next.id } }
                        else item { InfoCard(MireliIcons.CheckCircleOutline, "All journeys completed", "Reset sample trips from Account to try again.") }
                        item { InfoCard(MireliIcons.Train, "Built around the train", "Check your reporting time and agreed meeting point before every departure.") }
                    }
                    1 -> {
                        item { Heading("Your trips", "Upcoming journeys and completed transfers.") }
                        items(trips, key = { it.id }) { trip -> JourneyCard(trip) { selectedId = trip.id } }
                    }
                    2 -> {
                        item { Heading("Earnings", "A clear view of every journey.") }
                        item {
                            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Navy, Teal)), RoundedCornerShape(26.dp)).padding(24.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("COMPLETED SAMPLE FARES", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelMedium)
                                    Text(money(trips.filter { it.stage == TripStage.COMPLETED }.sumOf { it.fareMinor }), color = Color.White,
                                        fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Illustrative gross fares · not a payable balance", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        item { InfoCard(MireliIcons.AccountBalance, "Payouts are not connected", "Verified earnings, deductions and M-Pesa settlements must come from Mireli's shared ledger.") }
                        items(ServiceType.entries) { service ->
                            val completed = trips.filter { it.stage == TripStage.COMPLETED && it.service == service }
                            InfoCard(MireliIcons.Route, if (service == ServiceType.SHARED) "Shared transfers" else "Chartered transfers",
                                "${completed.size} completed · ${completed.sumOf { it.boardedSeats }} passengers · ${money(completed.sumOf { it.fareMinor })} sample gross")
                        }
                        items(trips.filter { it.stage == TripStage.COMPLETED }, key = { it.id }) { trip ->
                            InfoCard(MireliIcons.CheckCircleOutline, trip.id, money(trip.fareMinor) + " · sample gross fare")
                        }
                    }
                    else -> {
                        item { Heading("Your account", "Ready for the road. Built around you.") }
                        item { InfoCard(MireliIcons.PersonOutline, "Preview driver", "This sample identity is not an approved Mireli driver account.") }
                        item { SectionTitle("Driver readiness", "BEFORE LAUNCH") }
                        item { Button(onClick = { onboarding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Driver onboarding & documents") } }
                        item { InfoCard(MireliIcons.Badge, "Application checklist", "Driver details, vehicle details, encrypted document drafts and expiry validation. Live submission and review remain unconnected.") }
                        item { InfoCard(MireliIcons.PrivacyTip, "Privacy & account deletion", "Live policies and request service pending") }
                        item { InfoCard(MireliIcons.LocationOn, "Location access", "Not requested in preview") }
                        item {
                            Card {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text("Simulate offline", fontWeight = FontWeight.SemiBold); Text("Test saved boarding and restart recovery. This controls only the sample authority.", style = MaterialTheme.typography.bodySmall) }
                                    Switch(checked = !connected, onCheckedChange = { vm.setConnected(!it) }, enabled = !busy)
                                }
                            }
                        }
                        item { InfoCard(MireliIcons.PhoneAndroid, "App version", "0.2.0 · Native Android test build") }
                        item { OutlinedButton(onClick = vm::reset, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Reset sample trips") } }
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
        }
        if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("Driver support") },
            text = { Text("This preview is not monitored by dispatch and cannot send emergency alerts. Live support details will be configured before launch. In an emergency, contact local emergency services directly.") },
            confirmButton = { TextButton(onClick = { help = false }) { Text("Understood") } })
    }
}
@Composable private fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable private fun SectionTitle(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(detail, color = Teal, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
    }
}
@Composable private fun Stat(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Color.White) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Navy)
            Text(label, color = Muted)
        }
    }
}
@Composable private fun JourneyCard(trip: Trip, open: () -> Unit) {
    Card(onClick = open, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(trip.reportingTime, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Navy)
                Surface(color = Color(0xFFE4F2EE), shape = RoundedCornerShape(8.dp)) {
                    Text(if (trip.service == ServiceType.SHARED) "Shared transfer" else "Private charter",
                        Modifier.padding(8.dp), color = Teal, style = MaterialTheme.typography.labelMedium)
                }
            }
            RouteStops(trip)
            HorizontalDivider(color = Mist)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (trip.actionable) trip.stage.label() else trip.assignment.name.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Medium)
                    Text(trip.bookedSeats.toString() + " passengers · " + trip.id, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Open trip", tint = Teal)
            }
        }
    }
}
@Composable private fun RouteStops(trip: Trip) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MireliIcons.TripOrigin, null, tint = Teal, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp)); Text(trip.origin, fontWeight = FontWeight.SemiBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MireliIcons.LocationOn, null, tint = Navy, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp)); Text(trip.destination, fontWeight = FontWeight.SemiBold)
        }
    }
}
@Composable private fun RouteCard(trip: Trip) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("REPORT AT " + trip.reportingTime, color = Teal, style = MaterialTheme.typography.labelLarge)
            RouteStops(trip)
            HorizontalDivider(color = Mist)
            Text(trip.vehicle, color = Muted)
            Text(trip.bookedSeats.toString() + " / " + trip.capacity + " seats", color = Muted)
        }
    }
}
@Composable private fun ProgressCard(trip: Trip) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE5F2EF))) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("JOURNEY PROGRESS", style = MaterialTheme.typography.labelMedium, color = Teal)
            LinearProgressIndicator(progress = { trip.stage.ordinal / 4f }, modifier = Modifier.fillMaxWidth(), color = Teal, trackColor = Color.White)
            Text(trip.stage.label(), color = Navy, fontWeight = FontWeight.SemiBold)
        }
    }
}
@Composable private fun InfoCard(icon: ImageVector, title: String, detail: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = Teal)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
@Composable private fun PassengerCard(passenger: Passenger, busy: Boolean, execute: (TripCommand) -> Unit) {
    var boardingDialog by rememberSaveable(passenger.id) { mutableStateOf(false) }
    var noShowDialog by rememberSaveable(passenger.id) { mutableStateOf(false) }
    var code by rememberSaveable(passenger.id) { mutableStateOf("") }
    var count by rememberSaveable(passenger.id) { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(passenger.name, fontWeight = FontWeight.SemiBold)
            Text("${passenger.boardedCount} of ${passenger.seats} boarded · ${passenger.noShowCount} no-show · ${passenger.unresolvedSeats} waiting", color = Muted)
            if (passenger.unresolvedSeats > 0) {
                Text("Sample boarding code: " + passenger.code, style = MaterialTheme.typography.bodySmall, color = Teal)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { code = ""; count = passenger.unresolvedSeats.toString(); boardingDialog = true }, enabled = !busy) { Text("Board party") }
                    TextButton(onClick = { noShowDialog = true }, enabled = !busy) { Text("No-show") }
                }
            }
        }
    }
    if (boardingDialog) AlertDialog(onDismissRequest = { boardingDialog = false },
        title = { Text("Confirm boarding") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Record only passengers physically present. Remaining seats stay unresolved until boarded or marked absent.")
                OutlinedTextField(value = code, onValueChange = { code = it.take(8) }, label = { Text("Boarding code") }, singleLine = true)
                OutlinedTextField(value = count, onValueChange = { count = it.filter(Char::isDigit).take(2) }, label = { Text("Passengers boarding now") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { execute(TripCommand.Board(passenger.id, code, count.toIntOrNull())); boardingDialog = false }, enabled = code.isNotBlank() && count.toIntOrNull() in 1..passenger.unresolvedSeats && !busy) { Text("Confirm boarding") } },
        dismissButton = { TextButton(onClick = { boardingDialog = false }) { Text("Cancel") } })
    if (noShowDialog) AlertDialog(onDismissRequest = { noShowDialog = false },
        title = { Text("Record no-show?") },
        text = { Text("This marks only the ${passenger.unresolvedSeats} remaining passengers absent in the preview. Already boarded passengers remain present. Live no-shows must follow dispatch's grace and contact rules.") },
        confirmButton = { TextButton(onClick = { execute(TripCommand.MarkNoShow(passenger.id)); noShowDialog = false }, enabled = !busy) { Text("Record no-show") } },
        dismissButton = { TextButton(onClick = { noShowDialog = false }) { Text("Cancel") } })
}
@Composable private fun TripAction(trip: Trip, busy: Boolean, execute: (TripCommand) -> Unit) {
    var confirm by rememberSaveable(trip.id, trip.stage) { mutableStateOf(false) }
    var decline by rememberSaveable(trip.id) { mutableStateOf(false) }
    var reason by rememberSaveable(trip.id) { mutableStateOf("") }
    if (!trip.actionable) return
    if (trip.assignment == AssignmentState.PENDING) {
        OutlinedButton(onClick = { decline = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Decline assignment") }
    }
    if (decline) AlertDialog(onDismissRequest = { decline = false }, title = { Text("Decline this assignment?") },
        text = { Column { Text("Explain why you cannot take this journey. This test records the reason locally; no dispatcher is notified.")
            OutlinedTextField(reason, { reason = it.take(200) }, label = { Text("Reason for declining") }) } },
        confirmButton = { TextButton(onClick = { execute(TripCommand.Decline(reason)); decline = false }, enabled = !busy && reason.trim().length >= 5) { Text("Confirm decline") } },
        dismissButton = { TextButton(onClick = { decline = false }) { Text("Keep assignment") } })
    val action = when (trip.stage) {
        TripStage.ASSIGNED -> "Accept assignment" to TripCommand.Accept
        TripStage.ACCEPTED -> "I have arrived" to TripCommand.Arrive
        TripStage.AT_PICKUP -> "Start journey" to TripCommand.Start
        TripStage.IN_PROGRESS -> "Complete journey" to TripCommand.Complete
        TripStage.COMPLETED -> null
    }
    if (action != null) {
        Button(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
            Text(if (busy) "Saving…" else action.first, fontWeight = FontWeight.SemiBold)
        }
        if (confirm) AlertDialog(onDismissRequest = { confirm = false },
            title = { Text(action.first + "?") },
            text = { Text(when (trip.stage) {
                TripStage.IN_PROGRESS -> "Confirm every boarded passenger reached the agreed destination. This updates the sample journey only."
                TripStage.AT_PICKUP -> "Check every passenger's outcome and make sure everyone is safely seated before departing."
                TripStage.ACCEPTED -> "Confirm you are at the agreed meeting point. The manifest will open."
                else -> "Confirm you can complete this sample assignment with the listed vehicle."
            }) },
            confirmButton = { TextButton(onClick = { execute(action.second); confirm = false }, enabled = !busy) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
    }
}
@Composable private fun ConnectionRequired() {
    Surface(Modifier.fillMaxSize(), color = Mist) {
        Column(Modifier.safeDrawingPadding().padding(28.dp), verticalArrangement = Arrangement.Center) {
            Image(painterResource(R.drawable.mireli_driver_logo), "Mireli Driver", Modifier.size(100.dp))
            Spacer(Modifier.height(24.dp))
            Heading("Welcome to Mireli Driver", "Your next journey starts here.")
            Spacer(Modifier.height(24.dp))
            InfoCard(MireliIcons.Lock, "Organization setup required",
                "Live driver sign-in is not available in this build. Mireli's booking service, driver verification and support must be connected before accepting journeys.")
            Spacer(Modifier.height(20.dp))
            Text("This build does not collect location, identity documents or payment information.", color = Muted)
        }
    }
}
