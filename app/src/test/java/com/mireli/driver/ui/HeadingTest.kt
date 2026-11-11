package com.mireli.driver.ui

import com.mireli.driver.domain.DriverRoute
import com.mireli.driver.domain.GeoPoint
import com.mireli.driver.domain.RouteManeuver
import com.mireli.driver.domain.SimulatedDrive
import com.mireli.driver.domain.offRouteMetres
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Heading is the piece that is hardest to eyeball on a desk: the car must follow the driver when
 * they actually move, follow the road when they are stopped at a light, and never snap to north
 * when there is nothing better to go on.
 */
class HeadingTest {

    private val northbound = DriverRoute(
        geometry = listOf(GeoPoint(-4.0500, 39.6600), GeoPoint(-4.0400, 39.6600)),
        distanceMeters = 1_112,
        durationSeconds = 480,
        maneuvers = listOf(RouteManeuver("Go", 1_112, 480, 0, 1))
    )

    private val start = GeoPoint(-4.0500, 39.6600)

    @Test
    fun `device bearing wins while the driver is moving`() {
        val heading = resolveHeading(deviceBearing = 87.4f, speedMps = 9f, previousHeading = 0.0, route = northbound, point = start)
        // Float to Double widening is lossy at the sixth decimal, so compare as degrees.
        assertEquals(87.4, heading, 0.01)
    }

    @Test
    fun `a stationary car takes the road's direction, not a stale bearing`() {
        val heading = resolveHeading(deviceBearing = null, speedMps = 0f, previousHeading = 170.0, route = northbound, point = start)
        assertTrue("expected the northbound road bearing, got $heading", heading < 1.0 || heading > 359.0)
    }

    @Test
    fun `below the speed threshold the road still wins`() {
        // 1 m/s is slower than HEADING_MIN_SPEED_MPS: a bearing at walking pace is noise.
        val heading = resolveHeading(deviceBearing = 120f, speedMps = 1f, previousHeading = 0.0, route = northbound, point = start)
        assertTrue("expected road bearing, got $heading", heading < 1.0 || heading > 359.0)
    }

    @Test
    fun `with no route the last heading is kept instead of snapping north`() {
        val heading = resolveHeading(deviceBearing = null, speedMps = 0f, previousHeading = 45.0, route = null, point = start)
        assertEquals(45.0, heading, 1e-6)
    }

    @Test
    fun `a NaN bearing is ignored`() {
        val heading = resolveHeading(deviceBearing = Float.NaN, speedMps = 9f, previousHeading = 12.0, route = null, point = start)
        assertEquals(12.0, heading, 1e-6)
    }

    @Test
    fun `a laterally shifted drive reports itself as off route`() {
        val onRoute = SimulatedDrive(northbound, metresPerSecond = 8.0)
        val drifted = SimulatedDrive(northbound, metresPerSecond = 8.0, lateralOffsetMetres = 150.0)
        assertTrue(offRouteMetres(northbound, onRoute.pointAt(0)) < 1.0)
        val error = offRouteMetres(northbound, drifted.pointAt(0))
        assertTrue("expected about 150 m of lateral error, got $error", error in 140.0..160.0)
    }
}
