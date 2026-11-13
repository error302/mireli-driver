package com.mireli.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mireli.driver.domain.NavigationStop
import com.mireli.driver.ui.DriverHomeDashboard
import com.mireli.driver.ui.DriverNavigationScreen
import com.mireli.driver.ui.DriverHomeTopBar
import com.mireli.driver.ui.MireliIcons
import com.mireli.driver.ui.MireliTheme
import com.mireli.driver.ui.StationArrivalsPanel
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Debug-only harness for reviewing the redesigned driver surfaces on a real device.
 * It is excluded from release source sets and talks to nothing.
 */
class DesignPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val simulate = intent?.getBooleanExtra("simulate", false) == true
        val offRoute = intent?.getBooleanExtra("offRoute", false) == true
        val lowAccuracy = intent?.getBooleanExtra("lowAccuracy", false) == true
        // Debug-only: lets a reviewer see the real foreground service, its notification and its
        // stop path without a signed-in trip (the server still closes driver sign-in).
        if (intent?.getBooleanExtra("trackTrip", false) == true) {
            com.mireli.driver.data.DriverSessionStore(this).save(
                com.mireli.driver.data.DriverSession(
                    "harness-${System.currentTimeMillis()}",
                    Instant.now().plusSeconds(86_400).toString()
                )
            )
            com.mireli.driver.service.TripTracking.start(
                this,
                intent.getStringExtra("tripId") ?: "SGR-4821",
                intent.getStringExtra("stopName") ?: "Mombasa Terminus"
            )
        }
        if (intent?.getBooleanExtra("stopTrip", false) == true) {
            com.mireli.driver.service.TripTracking.stop(this)
            com.mireli.driver.data.DriverSessionStore(this).clear()
        }
        setContent {
            MireliTheme {
                if (simulate) SimulatedDriveScreen(
                    onClose = { finish() },
                    offRoute = offRoute,
                    accuracyMetres = if (lowAccuracy) 42f else 6f
                ) else DesignPreview()
            }
        }
    }
}

private fun sampleTrips(): List<JSONObject> {
    fun trip(id: String, minutes: Long, phase: String, route: String, drop: String, partySeats: Int, partyStage: String) =
        JSONObject()
            .put("id", id)
            .put("phase", phase)
            .put("routeName", route)
            .put("direction", "FROM_TERMINUS")
            .put("version", 1)
            .put("departureAt", Instant.now().plusSeconds(minutes * 60).toString())
            .put("stages", JSONArray().apply {
                put(JSONObject().put("id", "st1").put("name", "Mombasa Terminus")
                    .put("latitude", -4.0435).put("longitude", 39.6682))
                put(JSONObject().put("id", "st2").put("name", drop)
                    .put("latitude", -4.0271).put("longitude", 39.7060))
            })
            .put("passengers", JSONArray().apply {
                put(JSONObject().put("id", "p1").put("name", "SGR Passenger").put("seats", partySeats)
                    .put("boarded", 0).put("remaining", partySeats).put("noShow", 0).put("stageName", partyStage))
                if (partySeats > 1) put(JSONObject().put("id", "p2").put("name", "SGR Passenger").put("seats", 1)
                    .put("boarded", 0).put("remaining", 1).put("noShow", 0).put("stageName", "Bamburi"))
            })
    return listOf(
        trip("SGR-4790", -95, "completed", "Mombasa Terminus → Nyali", "Nyali", 2, "Nyali"),
        trip("SGR-4821", 18, "accepted", "Mombasa Terminus → Nyali", "Nyali", 2, "Nyali"),
        trip("SGR-4833", 173, "assigned", "Mombasa Terminus → Bamburi", "Bamburi", 3, "Bamburi"),
        trip("SGR-4850", 383, "assigned", "Mombasa Terminus → Likoni", "Likoni", 1, "Likoni")
    )
}

private fun sampleEarnings(): JSONObject = JSONObject()
    .put("totals", JSONArray().put(JSONObject().put("status", "completed").put("netMinor", 245000)))
    .put("lines", JSONArray()
        .put(JSONObject().put("id", "e1").put("routeName", "Mombasa Terminus → Nyali")
            .put("serviceType", "shared").put("status", "completed")
            .put("grossMinor", 120000).put("commissionMinor", 24000).put("surchargeMinor", 0).put("netMinor", 96000))
        .put(JSONObject().put("id", "e2").put("routeName", "Mombasa Terminus → Bamburi")
            .put("serviceType", "shared").put("status", "completed")
            .put("grossMinor", 180000).put("commissionMinor", 31000).put("surchargeMinor", 0).put("netMinor", 149000)))

@Composable
private fun DesignPreview() {
    val trips = remember { sampleTrips() }
    val earnings = remember { sampleEarnings() }
    var tab by remember { mutableIntStateOf(0) }
    var navStop by remember { mutableStateOf<NavigationStop?>(null) }
    var mapOnly by remember { mutableStateOf(false) }
    if (navStop != null || mapOnly) {
        DriverNavigationScreen(
            tripId = if (navStop != null) "t1" else null,
            stop = navStop,
            onRequestRoute = null,
            onClose = { navStop = null; mapOnly = false }
        )
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                listOf(
                    "Home" to MireliIcons.Home,
                    "Trains" to MireliIcons.TrainFront,
                    "Trips" to MireliIcons.CarFront
                ).forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { Icon(icon, null) }, label = { Text(label) })
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { DriverHomeTopBar(onOpenMap = { mapOnly = true }, onSupport = { }) }
            when (tab) {
                0 -> item {
                    DriverHomeDashboard(
                        name = "James Mwangi",
                        eligible = true,
                        verified = true,
                        applicationStatus = "approved",
                        tripsCurrent = true,
                        trips = trips,
                        activeNavigation = null,
                        onOpenTrips = { tab = 2 },
                        onRefresh = { },
                        onOpenAccount = { },
                        onOpenTrip = { tab = 2 },
                        onStartNavigation = { _, stop -> navStop = stop },
                        onOpenMap = { mapOnly = true }
                    )
                }
                1 -> item {
                    StationArrivalsPanel(
                        trips = trips,
                        eligible = true,
                        stationHint = "Mombasa Terminus",
                        onOpenTrip = { tab = 2 },
                        onOpenTrips = { tab = 2 }
                    )
                }
                else -> item {
                    Text("Trip list is reviewed from the signed-in app.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
