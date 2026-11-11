package com.mireli.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mireli.driver.domain.DriverRoute
import com.mireli.driver.domain.NavigationStop
import com.mireli.driver.domain.RouteManeuver
import com.mireli.driver.domain.SimulatedDrive
import com.mireli.driver.domain.decodePolyline6
import com.mireli.driver.ui.DriverNavigationScreen

/**
 * Debug-only moving-location harness.
 *
 * The route geometry below is a real OSRM driving route from Mombasa Terminus to Nyali, fetched
 * once and stored as an encoded polyline. A [SimulatedDrive] replays it at about 30 km/h and
 * produces genuine `Location` objects, so the map, heading, route split, camera and ETA can be
 * reviewed without driving. Nothing here is used by the shipping app.
 */
private const val DEMO_ROUTE_POLYLINE = """rmxuF}|ctjAQC_Gs@AqEJgGI_[q@eTq@}K_A{KwAiMiAuPwA}LwByQoImo@uEca@kAaKuAyLaCySmMuvAuFuc@aBmIcBiKiAaHsAkGkE{SyAwFaE_NuBmHiGqRaGoPwDoJaBaEsB{EuBoEsEwJuG}MyCkGsK{Tq@oA}EgJiBmEqCuEq@sAiCgE}CoE_FaHuG_KeEsFqIgKaIaIiGgF}PuPaRwQcC}BgP}LcD{CcKkJ}MoKqJwHgHsFyH}FsFgFaGsFgNsOaJyIu@o@uDiF[O_CgDyKcPqLaSoCqIgBwEwBkE_EwJsAsDcHmSgJyVaG}MiDcIkLg[aF{NqJmWsD}JeC{GwCgHcJkUiGcMwAsCiB}DuDcG}EoHoFkHiJuKiLuLcVeWgPeQqM{Mca@wd@aAiAoT_UeNqN}SiS{I}HuOwO{FeGwS}UiFmG}HuH_KwLcG_HmQwRwVwVcQoRoFkHgMqPeF{GsEwHqCuEgNaVoIwOaL}R{\mn@}IkOsEeJq[al@_CqEiBkD}BoEoHoPq@{AoCgH}CyHaJ}XaGyUuGy[yBkNwBaOoBwNa@_HmAoJwHcn@a@}DeAkLk@gGc@eFwCqVgCiVkC}TsC_Uc@}BiCwWcF{^wGwi@kBuRsIks@qF}f@z_@^nr@t@`b@QhDExGe@`bAcE"""

private const val DEMO_DISTANCE_METRES = 5260
private const val DEMO_DURATION_SECONDS = 641

private fun demoRoute(): DriverRoute {
    val points = decodePolyline6(DEMO_ROUTE_POLYLINE)
    val last = points.lastIndex
    val steps = listOf(
        Triple(0, last / 3, "Follow the mapped road"),
        Triple(last / 3, (last * 2) / 3, "Continue towards Nyali"),
        Triple((last * 2) / 3, last, "Arrive at Nyali")
    )
    val maneuvers = steps.map { (begin, end, text) ->
        RouteManeuver(
            instruction = text,
            distanceMeters = DEMO_DISTANCE_METRES / steps.size,
            durationSeconds = DEMO_DURATION_SECONDS / steps.size,
            beginShapeIndex = begin,
            endShapeIndex = end
        )
    }
    return DriverRoute(points, DEMO_DISTANCE_METRES, DEMO_DURATION_SECONDS, maneuvers)
}

@Composable
fun SimulatedDriveScreen(
    onClose: () -> Unit,
    offRoute: Boolean = false,
    accuracyMetres: Float = 6f
) {
    val route = remember { demoRoute() }
    val drive = remember(route, offRoute) {
        SimulatedDrive(
            route, metresPerSecond = 8.2, startMetres = 3000.0, accuracyMetres = accuracyMetres,
            // Rehearses the off-route banner without needing to miss a real turn.
            lateralOffsetMetres = if (offRoute) 150.0 else 0.0
        )
    }
    val destination = remember(route) {
        val end = route.geometry.last()
        NavigationStop("demo-nyali", "Nyali", end.latitude, end.longitude)
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        DriverNavigationScreen(
            tripId = "demo-sim",
            stop = destination,
            onRequestRoute = null,
            onClose = onClose,
            nextTrain = "in 12 min",
            trainStation = "Terminus",
            simulated = drive
        )
    }
}
