package com.mireli.driver

import com.mireli.driver.data.activeTripSnapshot
import com.mireli.driver.domain.StatementPeriod
import com.mireli.driver.ui.primaryNavigationStop
import com.mireli.driver.ui.seatsWaiting
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DriverDashboardTest {
    @Test fun offlineTripKeepsOnlyTheCurrentStopAndSeatSummary() {
        val trip = JSONObject("""
            {
              "id":"active","version":2,"phase":"in_progress","direction":"FROM_TERMINUS",
              "routeName":"Mombasa to Nyali",
              "stages":[{"id":"station","name":"Terminus","latitude":-4,"longitude":39},
                        {"id":"stop","name":"Nyali","latitude":-4,"longitude":39.7}],
              "passengers":[{"name":"Private name","phone":"Private number","homeAddress":"Private address",
                             "stageId":"stop","seats":2,"remaining":0}]
            }
        """)
        val future = JSONObject(trip.toString()).put("id", "future").put("phase", "assigned")
        val snapshot = JSONArray().put(future).put(trip).activeTripSnapshot("test-driver")!!
        val overview = snapshot.getJSONObject("trip")
        assertEquals("active", overview.getString("id"))
        assertEquals("Nyali", overview.primaryNavigationStop()?.name)
        assertEquals(0, overview.seatsWaiting())
        assertEquals(0, overview.getJSONArray("passengers").length())
        assertFalse(snapshot.toString().contains("Private"))
        assertNull(JSONArray().put(trip.put("phase", "completed")).activeTripSnapshot("test-driver"))
    }

    @Test fun statementsUseNairobiBoundariesAndTheCorrectSettlementTimestamp() {
        val now = Instant.parse("2026-11-02T14:00:00Z")
        val line = JSONObject()
            .put("status", "completed")
            .put("initiatedAt", "2026-10-31T20:59:59Z")
            .put("completedAt", "2026-11-01T21:00:00Z")
        assertTrue(StatementPeriod.Week.includes(line, now))
        assertTrue(StatementPeriod.Month.includes(line, now))
        assertFalse(StatementPeriod.Week.includes(line.put("status", "queued"), now))
        assertFalse(StatementPeriod.Month.includes(line, now))
        assertTrue(StatementPeriod.All.includes(line, now))
    }
}
