package com.mireli.driver.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedDriveTest {

    private fun route(vararg points: Pair<Double, Double>): DriverRoute {
        val geometry = points.map { GeoPoint(it.first, it.second) }
        return DriverRoute(
            geometry = geometry,
            distanceMeters = 1_000,
            durationSeconds = 120,
            maneuvers = listOf(RouteManeuver("Go", 1_000, 120, 0, geometry.lastIndex))
        )
    }

    /** A straight north-south line at Mombasa's latitude: easy to reason about in metres. */
    private val northbound = route(
        -4.0500 to 39.6600,
        -4.0400 to 39.6600,
        -4.0300 to 39.6600
    )

    @Test
    fun `position starts at the route and clamps at the end`() {
        val drive = SimulatedDrive(northbound, metresPerSecond = 10.0)
        val start = drive.pointAt(0)
        assertEquals(-4.0500, start.latitude, 1e-9)

        val far = drive.pointAt(10_000_000)
        assertEquals(-4.0300, far.latitude, 1e-9)
        assertEquals(39.6600, far.longitude, 1e-9)
    }

    @Test
    fun `position advances at the requested speed`() {
        val drive = SimulatedDrive(northbound, metresPerSecond = 10.0)
        // 30 seconds at 10 m/s is 300 m, a third of the way down the ~1112 m first leg.
        val point = drive.pointAt(30_000)
        assertTrue(point.latitude > -4.0500 && point.latitude < -4.0400)
        assertEquals(-4.0500 + 0.0027, point.latitude, 2e-4)
    }

    @Test
    fun `heading follows the segment direction`() {
        val drive = SimulatedDrive(northbound, metresPerSecond = 10.0)
        val heading = drive.bearingAt(15_000)
        assertTrue("expected roughly north, got $heading", heading < 5f || heading > 355f)
    }

    @Test
    fun `off route distance is measured to the segment, not the nearest vertex`() {
        // Halfway down a 1.1 km leg, on the centre line: a vertex-based measure would report ~555 m.
        val midLeg = GeoPoint(-4.0450, 39.6600)
        assertTrue("on-route fix reported ${offRouteMetres(northbound, midLeg)} m", offRouteMetres(northbound, midLeg) < 1.0)

        val offRoute = GeoPoint(-4.0450, 39.6700)
        assertTrue(offRouteMetres(northbound, offRoute) > 900.0)
    }

    @Test
    fun `bearing delta takes the short way round`() {
        assertEquals(10.0, bearingDelta(350.0, 0.0), 1e-9)
        assertEquals(-10.0, bearingDelta(0.0, 350.0), 1e-9)
        assertEquals(180.0, bearingDelta(0.0, 180.0), 1e-9)
    }

    @Test
    fun `remaining time follows the distance left, not whole maneuvers`() {
        // 1.1 km route, 480 s total, split at the midpoint: half-way leaves ~556 m and ~240 s.
        val split = DriverRoute(
            geometry = listOf(
                GeoPoint(-4.0500, 39.6600),
                GeoPoint(-4.0450, 39.6600),
                GeoPoint(-4.0400, 39.6600)
            ),
            distanceMeters = 1112,
            durationSeconds = 480,
            maneuvers = listOf(RouteManeuver("Go", 1112, 480, 0, 2))
        )
        val halfWay = estimatedRemainingSeconds(split, 1)
        assertTrue("expected about 240 s, got $halfWay", halfWay in 235..245)
        // Whole-maneuver summation would have reported the full 480 s here.
        assertEquals(480, remainingDurationSeconds(split, 1))
        // At the destination it collapses to a minimum of one second rather than a fake minute.
        assertEquals(1, estimatedRemainingSeconds(split, 2))
    }
}
