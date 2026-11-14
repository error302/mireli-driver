package com.mireli.driver.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Keep just enough of the driver's highest-priority trip to render its offline overview. */
internal fun JSONArray.activeTripSnapshot(driverId: String): JSONObject? {
    val phaseOrder = listOf("in_progress", "at_pickup", "accepted", "assigned")
    val trip = (0 until length()).mapNotNull(::optJSONObject)
        .filter { it.optString("phase") in phaseOrder }
        .minWithOrNull(compareBy<JSONObject> { phaseOrder.indexOf(it.optString("phase")) }.thenBy {
            runCatching { Instant.parse(it.optString("departureAt")) }.getOrDefault(Instant.MAX)
        }) ?: return null

    val summary = JSONObject()
    listOf("id", "version", "phase", "direction", "departureAt", "routeName").forEach { key ->
        summary.put(key, trip.opt(key))
    }
    val stages = JSONArray()
    trip.optJSONArray("stages")?.let { source ->
        for (index in 0 until source.length()) {
            val stage = source.optJSONObject(index) ?: continue
            val waypoint = JSONObject()
            listOf("id", "name", "latitude", "longitude").forEach { key -> waypoint.put(key, stage.opt(key)) }
            stages.put(waypoint)
        }
    }
    val passengers = trip.optJSONArray("passengers")
    val parties = (0 until (passengers?.length() ?: 0)).mapNotNull { passengers?.optJSONObject(it) }
    val countsByStop = parties.groupBy { (it.opt("stageId") as? String) to it.optBoolean("homePickup") }
    val counts = JSONArray()
    countsByStop.forEach { (key, bookings) ->
        counts.put(JSONObject()
            .put("stageId", key.first)
            .put("homePickup", key.second)
            .put("seats", bookings.sumOf { it.optInt("seats") })
            .put("remaining", bookings.sumOf { it.optInt("remaining") }))
    }
    summary.put("stages", stages).put("passengers", JSONArray())
        .put("offlineSeatCounts", counts)
    return JSONObject().put("driverId", driverId).put("trip", summary)
}
