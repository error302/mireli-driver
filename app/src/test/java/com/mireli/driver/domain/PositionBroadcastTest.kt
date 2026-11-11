package com.mireli.driver.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PositionBroadcastTest {
    private fun payload(lat: Double = -4.04, lng: Double = 39.66) = PositionBroadcast.payload(
        tripId = "trip-1", latitude = lat, longitude = lng,
        accuracyM = 12f, bearingDeg = 87f, speedMps = 8.3f,
        recordedAtIsoUtc = "2026-11-08T09:15:30.250Z", idempotencyKey = "key-1"
    )

    @Test fun intervalMatchesTheServerThrottleWithDriftMargin() {
        assertEquals(4_000L, PositionBroadcast.MIN_INTERVAL_MS)
        assertTrue(PositionBroadcast.MIN_INTERVAL_MS > 3_000L)
    }

    @Test fun sendsOnlyForActivePhasesWithValidFixesAfterTheInterval() {
        assertTrue(PositionBroadcast.shouldSend("in_progress", true, Long.MIN_VALUE, 4_000))
        assertFalse(PositionBroadcast.shouldSend("in_progress", true, 5_000, 4_000))
        assertTrue(PositionBroadcast.shouldSend("accepted", true, 0, 4_000))
        assertTrue(PositionBroadcast.shouldSend("at_pickup", true, 0, 4_000))
        assertTrue(PositionBroadcast.shouldSend("in_progress", true, 0, 4_000))
        assertFalse(PositionBroadcast.shouldSend("assigned", true, 0, 4_000))
        assertFalse(PositionBroadcast.shouldSend("completed", true, 0, 4_000))
        assertFalse(PositionBroadcast.shouldSend("in_progress", false, 0, 4_000))
        assertFalse(PositionBroadcast.shouldSend("in_progress", true, 3_999, 4_000))
        assertTrue(PositionBroadcast.shouldSend("in_progress", true, 3_999, 8_000))
    }

    @Test fun payloadCarriesTheContractFieldsAndDropsInvalidOptionals() {
        val body = payload()
        assertEquals("trip-1", body.getString("tripId"))
        assertEquals(-4.04, body.getDouble("lat"), 0.0)
        assertEquals(39.66, body.getDouble("lng"), 0.0)
        assertEquals("2026-11-08T09:15:30.250Z", body.getString("recordedAt"))
        assertEquals("key-1", body.getString("idempotencyKey"))
        assertEquals(12.0, body.getDouble("accuracyM"), 0.0)
        assertEquals(87.0, body.getDouble("bearingDeg"), 0.0)
        assertEquals(8.3, body.getDouble("speedMps"), 0.001)
    }

    @Test fun payloadRejectsInvalidPositionsAndNullIsland() {
        assertThrows(IllegalArgumentException::class.java) { payload(lat = 91.0) }
        assertThrows(IllegalArgumentException::class.java) { payload(lng = -181.0) }
        assertThrows(IllegalArgumentException::class.java) { payload(lat = 0.0, lng = 0.0) }
        assertThrows(IllegalArgumentException::class.java) {
            PositionBroadcast.payload(" ", -4.0, 39.0, null, null, null, "2026-11-08T09:15:30Z", "k")
        }
    }

    @Test fun onlyDefinitiveRejectionsStopTheBroadcast() {
        assertTrue(PositionBroadcast.shouldStopOn(403, null))
        assertTrue(PositionBroadcast.shouldStopOn(404, null))
        assertTrue(PositionBroadcast.shouldStopOn(409, "TRIP_NOT_ACTIVE"))
        assertFalse(PositionBroadcast.shouldStopOn(409, "VERSION_CONFLICT"))
        assertFalse(PositionBroadcast.shouldStopOn(408, "TIMEOUT"))
        assertFalse(PositionBroadcast.shouldStopOn(429, "RATE_LIMITED"))
        assertFalse(PositionBroadcast.shouldStopOn(500, null))
        assertTrue(PositionBroadcast.shouldStopOn(401, "SESSION_EXPIRED"))
    }
}
