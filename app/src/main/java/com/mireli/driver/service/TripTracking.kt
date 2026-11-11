package com.mireli.driver.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * The single owner of the question "is a trip active, and should the phone be reporting it?".
 *
 * The service used to be started by nothing at all. The decision now lives here, is persisted, and
 * is idempotent, so the UI can call [sync] on every state change without spamming start intents.
 * Persisting the active trip is also what lets the service resume after the process is killed.
 */
object TripTracking {
    private const val PREFS = "trip_tracking"
    private const val KEY_TRIP = "tripId"
    private const val KEY_STOP = "stopName"
    private const val KEY_STARTED_AT = "startedAt"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Keeps the service aligned with the current active trip. Safe to call from composition. */
    fun sync(context: Context, tripId: String?, stopName: String?) {
        val current = activeTripId(context)
        when {
            tripId.isNullOrBlank() -> if (current != null) stop(context)
            tripId == current -> Unit
            else -> start(context, tripId, stopName)
        }
    }

    fun start(context: Context, tripId: String, stopName: String?) {
        prefs(context).edit()
            .putString(KEY_TRIP, tripId)
            .putString(KEY_STOP, stopName)
            .putLong(KEY_STARTED_AT, System.currentTimeMillis())
            .apply()
        val intent = Intent(context, TripBroadcastService::class.java)
            .setAction(TripBroadcastService.ACTION_START)
            .putExtra(TripBroadcastService.EXTRA_TRIP_ID, tripId)
            .putExtra(TripBroadcastService.EXTRA_STOP_NAME, stopName)
        // Only ever started while the app is visible, from an explicit tap or an active trip.
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }

    fun stop(context: Context) {
        prefs(context).edit().clear().apply()
        context.stopService(Intent(context, TripBroadcastService::class.java))
    }

    fun activeTripId(context: Context): String? = prefs(context).getString(KEY_TRIP, null)

    fun stopName(context: Context): String? = prefs(context).getString(KEY_STOP, null)

    fun startedAt(context: Context): Long = prefs(context).getLong(KEY_STARTED_AT, 0L)
}
