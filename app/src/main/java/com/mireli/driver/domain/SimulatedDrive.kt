package com.mireli.driver.domain

import android.location.Location
import android.os.SystemClock
import kotlin.math.cos

/**
 * Drives a recorded route at a chosen speed and produces fake [Location] fixes from it.
 *
 * This exists only for the debug preview harness: it is how the map, the heading resolution, the
 * route split and the ETA can be exercised on a desk instead of in traffic. Nothing in the shipping
 * flow constructs one, and the fixes are labelled with the [Location.getProvider] `mireli-demo`.
 */
class SimulatedDrive(
    val route: DriverRoute,
    metresPerSecond: Double = 8.2,
    private val startMetres: Double = 0.0,
    private val accuracyMetres: Float = 6f,
    /** Shifts every fix sideways, to rehearse the off-route state without leaving the desk. */
    private val lateralOffsetMetres: Double = 0.0
) {
    private val speed = metresPerSecond.coerceAtLeast(0.5)

    private val cumulative: DoubleArray = run {
        val running = DoubleArray(route.geometry.size)
        for (index in 1 until route.geometry.size) {
            running[index] = running[index - 1] + distanceMeters(route.geometry[index - 1], route.geometry[index])
        }
        running
    }

    val totalMetres: Double get() = cumulative.lastOrNull() ?: 0.0

    /** Distance already covered at [elapsedMs], clamped to the route. */
    fun travelledMetres(elapsedMs: Long): Double =
        (startMetres + speed * (elapsedMs.coerceAtLeast(0L) / 1000.0)).coerceIn(0.0, totalMetres)

    fun pointAt(elapsedMs: Long): GeoPoint {
        val target = travelledMetres(elapsedMs)
        if (route.geometry.size < 2) return route.geometry.first()
        var index = 1
        while (index < cumulative.size - 1 && cumulative[index] < target) index++
        val from = route.geometry[index - 1]
        val to = route.geometry[index]
        val segment = (cumulative[index] - cumulative[index - 1]).takeIf { it > 0.01 } ?: return to
        val fraction = ((target - cumulative[index - 1]) / segment).coerceIn(0.0, 1.0)
        val latitude = from.latitude + (to.latitude - from.latitude) * fraction
        val longitude = from.longitude + (to.longitude - from.longitude) * fraction
        if (lateralOffsetMetres == 0.0) return GeoPoint(latitude, longitude)
        return GeoPoint(latitude, longitude + lateralOffsetMetres / metresPerDegreeLongitude(latitude))
    }

    /** True bearing of the segment the car is on, so turns show up as real heading changes. */
    fun bearingAt(elapsedMs: Long): Float {
        val point = pointAt(elapsedMs)
        val index = nearestRoutePointIndex(route, point)
        val ahead = route.geometry.getOrNull(index + 1) ?: route.geometry.last()
        val behind = route.geometry.getOrNull(index) ?: route.geometry.first()
        if (ahead == behind) return 0f
        return bearingBetween(behind, ahead).toFloat()
    }

    fun fixAt(elapsedMs: Long): Location {
        val point = pointAt(elapsedMs)
        // Captured before the apply block: inside it `speed` and `bearing` would resolve to the
        // Location's own properties, which silently produced 0 km/h.
        val simulatedSpeed = speed.toFloat()
        val simulatedBearing = bearingAt(elapsedMs)
        return Location("mireli-demo").apply {
            latitude = point.latitude
            longitude = point.longitude
            accuracy = accuracyMetres
            speed = simulatedSpeed
            bearing = simulatedBearing
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            time = System.currentTimeMillis()
        }
    }
}

/** Bearing in degrees clockwise from north between two points. */
internal fun bearingBetween(from: GeoPoint, to: GeoPoint): Double {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val y = Math.sin(dLon) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
    return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
}

/** Shortest signed turn from one bearing to another, in (-180, 180]. */
internal fun bearingDelta(from: Double, to: Double): Double {
    var delta = (to - from) % 360.0
    if (delta > 180.0) delta -= 360.0
    if (delta <= -180.0) delta += 360.0
    return delta
}

/**
 * Metres of lateral error between a fix and the route, used for off-route detection.
 *
 * Measured to the nearest *segment*, not the nearest vertex: on a long straight road the nearest
 * vertex can be a kilometre away while the car is sitting on the centre line, which would trigger
 * false reroutes.
 */
internal fun offRouteMetres(route: DriverRoute, point: GeoPoint): Double {
    val geometry = route.geometry
    if (geometry.isEmpty()) return Double.MAX_VALUE
    if (geometry.size == 1) return distanceMeters(geometry.first(), point)
    var best = Double.MAX_VALUE
    for (index in 0 until geometry.lastIndex) {
        val candidate = distanceToSegmentMetres(point, geometry[index], geometry[index + 1])
        if (candidate < best) best = candidate
    }
    return best
}

/** Perpendicular distance to a segment, via a local flat projection (fine at city scale). */
private fun distanceToSegmentMetres(point: GeoPoint, from: GeoPoint, to: GeoPoint): Double {
    val metresPerLat = 111_320.0
    val metresPerLon = metresPerDegreeLongitude(point.latitude)
    val ax = 0.0
    val ay = 0.0
    val bx = (to.longitude - from.longitude) * metresPerLon
    val by = (to.latitude - from.latitude) * metresPerLat
    val px = (point.longitude - from.longitude) * metresPerLon
    val py = (point.latitude - from.latitude) * metresPerLat
    val lengthSquared = bx * bx + by * by
    val t = if (lengthSquared < 1e-6) 0.0 else ((px * bx + py * by) / lengthSquared).coerceIn(0.0, 1.0)
    val dx = px - bx * t
    val dy = py - by * t
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

/** Rough metres-per-degree helper shared by the simulation and the accuracy ring. */
internal fun metresPerDegreeLongitude(latitude: Double): Double =
    111_320.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.05)
