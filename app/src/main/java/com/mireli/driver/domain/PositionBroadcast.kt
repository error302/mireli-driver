package com.mireli.driver.domain

import org.json.JSONObject

/**
 * Pure rules for broadcasting the driver position during an active trip.
 * The server contract (WEB-AGENT-REPORT-2026-11-08 section 8) accepts at most
 * one write per trip per 3 seconds and rejects fixes for trips that are not
 * in accepted / at_pickup / in_progress. The client obeys both limits so the
 * service never produces avoidable rejections.
 */
object PositionBroadcast {
    /** Server-side throttle is 3 s; the client waits 4 s so clock drift cannot trip it. */
    const val MIN_INTERVAL_MS: Long = 4_000L
    val BROADCAST_PHASES = setOf("accepted", "at_pickup", "in_progress")

    fun activePhase(phase: String): Boolean = phase in BROADCAST_PHASES

    /** Only a newer, valid fix inside the active phase may be sent. */
    fun shouldSend(phase: String, fixValid: Boolean, lastSentElapsedMs: Long, nowElapsedMs: Long): Boolean =
        activePhase(phase) && fixValid && (lastSentElapsedMs == Long.MIN_VALUE ||
            nowElapsedMs >= lastSentElapsedMs && nowElapsedMs - lastSentElapsedMs >= MIN_INTERVAL_MS)

    fun payload(
        tripId: String,
        latitude: Double,
        longitude: Double,
        accuracyM: Float?,
        bearingDeg: Float?,
        speedMps: Float?,
        recordedAtIsoUtc: String,
        idempotencyKey: String
    ): JSONObject {
        require(tripId.isNotBlank()) { "tripId is required" }
        require(latitude.isFinite() && latitude in -90.0..90.0) { "latitude out of range" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "longitude out of range" }
        require(!(latitude == 0.0 && longitude == 0.0)) { "null-island fix is not a position" }
        val body = JSONObject()
            .put("tripId", tripId)
            .put("lat", latitude)
            .put("lng", longitude)
            .put("recordedAt", recordedAtIsoUtc)
            .put("idempotencyKey", idempotencyKey)
        accuracyM?.takeIf { it.isFinite() && it >= 0f }?.let { body.put("accuracyM", it.toDouble()) }
        bearingDeg?.takeIf { it.isFinite() && it in 0f..360f }?.let { body.put("bearingDeg", it.toDouble()) }
        speedMps?.takeIf { it.isFinite() && it >= 0f }?.let { body.put("speedMps", it.toDouble()) }
        return body
    }

    /**
     * Decides whether a rejection should stop the broadcast. A trip that is no
     * longer active or no longer ours must end the service; a transient network
     * failure must not.
     */
    fun shouldStopOn(status: Int, code: String?): Boolean =
        status in setOf(401, 403, 404) || (status == 409 && code == "TRIP_NOT_ACTIVE")
}
