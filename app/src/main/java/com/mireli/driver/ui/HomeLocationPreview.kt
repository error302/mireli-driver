package com.mireli.driver.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun HomeLocationPreview(onOpenMap: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(locationPermissionGranted(context)) }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        granted = locationPermissionGranted(context)
    }
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = locationPermissionGranted(context)
    }
    val position = rememberDriverPosition()
    val age by produceState(position.ageSeconds, position.fix) {
        while (position.fix != null) {
            value = position.ageSeconds
            delay(5_000)
        }
        value = null
    }
    HomeLocationCard(
        position = position,
        age = age,
        granted = granted,
        onOpenMap = onOpenMap,
        onEnableLocation = {
            requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        },
        onOpenSettings = { settings.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
    )
}

@Composable
private fun HomeLocationCard(
    position: DriverPosition,
    age: Long?,
    granted: Boolean,
    onOpenMap: () -> Unit,
    onEnableLocation: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val freshAge = age?.takeIf { granted && it in 0 until LOCATION_STALE_MS / 1000 }
    val fix = position.fix.takeIf { freshAge != null }
    val deviceLocationOff = granted && position.error?.startsWith("Turn on device location") == true
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("home_location_preview"),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLow,
    ) {
        Column {
            NavigationMap(
                target = null,
                location = position.point.takeIf { fix != null },
                fix = fix,
                route = null,
                modifier = Modifier.fillMaxWidth().height(148.dp).background(colors.surfaceContainer),
                interactive = false,
                controls = false,
                homePreview = true,
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(MireliIcons.Map, null, Modifier.size(19.dp), tint = colors.secondary)
                    Text("Your location", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onOpenMap, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("Open map", color = colors.secondary)
                    }
                }
                if (fix != null && freshAge != null) {
                    val accuracy = position.accuracy?.takeIf { it.isFinite() && it >= 0f }?.roundToInt()
                    val freshness = if (freshAge < 15) "Updated just now" else "Updated ${freshAge}s ago"
                    Text(listOfNotNull(accuracy?.let { "GPS ±$it m" }, freshness).joinToString(" · "),
                        Modifier.padding(bottom = 8.dp), color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.numericDetail)
                } else {
                    val status = when {
                        !granted -> "Location permission needed"
                        deviceLocationOff -> "Device location is off"
                        position.fix != null -> "Waiting for an updated location"
                        else -> "Finding your location…"
                    }
                    Text("Mombasa area · $status", Modifier.padding(bottom = 6.dp),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    if (!granted || deviceLocationOff) {
                        TextButton(
                            onClick = if (deviceLocationOff) onOpenSettings else onEnableLocation,
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        ) { Text(if (deviceLocationOff) "Location settings" else "Enable location", color = colors.secondary) }
                    }
                }
            }
        }
    }
}
