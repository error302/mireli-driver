package com.mireli.driver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mireli.driver.domain.NavigationStop
import org.json.JSONObject

@Composable
internal fun ActiveTripOverview(
    trip: JSONObject,
    current: Boolean,
    pendingAction: Boolean,
    routeRequest: RouteRequest?,
    showMap: Boolean,
    onStartNavigation: (NavigationStop) -> Unit,
) {
    val phase = trip.optString("phase")
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)) {
            Text(phase.transferLabel(), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                color = if (phase == "in_progress") colors.primary else colors.onSurface)
            trip.departureClock()?.let { time ->
                Text("$time EAT", style = MaterialTheme.typography.numericDetail, color = colors.onSurfaceVariant)
            }
        }
        Text(trip.optString("routeName").ifBlank { "Current trip" }, style = MaterialTheme.typography.titleMedium)
        if (!current && !pendingAction) DepartureNote("Last confirmed assignment")
        TripNavigation(
            trip = trip,
            onRequestRoute = routeRequest,
            onStartNavigation = onStartNavigation,
            compact = true,
            seatsWaiting = trip.seatsWaiting().takeIf { trip.parties().isNotEmpty() },
            enabled = showMap,
        )
    }
}
