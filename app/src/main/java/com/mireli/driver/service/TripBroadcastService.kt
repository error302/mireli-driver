package com.mireli.driver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.mireli.driver.MainActivity
import com.mireli.driver.R
import com.mireli.driver.data.DriverApi
import com.mireli.driver.data.DriverSessionStore
import com.mireli.driver.domain.PositionBroadcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/**
 * Foreground service that streams the driver position to Mireli while a trip is active.
 *
 * It is started only through [TripTracking] while the app is visible, keeps a persistent
 * notification with the trip destination and a stop action, and stops itself when the trip ends,
 * the session disappears, location is unavailable, or the server rejects the assignment.
 *
 * Contract: POST /api/v1/driver/position (WEB-AGENT-REPORT-2026-11-08 §8). The endpoint is not
 * deployed yet; until the server advertises tracking, the service reports unavailable instead of
 * posting fixes into the void.
 */
class TripBroadcastService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var api: DriverApi
    private lateinit var sessions: DriverSessionStore
    private var tripId: String? = null
    private var lastSentElapsedMs = Long.MIN_VALUE
    private var inFlight = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        api = DriverApi()
        sessions = DriverSessionStore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            TripTracking.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }
        // A sticky restart arrives without extras; the persisted trip is the source of truth.
        val requestedTrip = intent?.getStringExtra(EXTRA_TRIP_ID)
            ?: TripTracking.activeTripId(this)
        if (requestedTrip.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (sessions.load() == null) {
            // Signed out mid-trip: the position would be unattributable.
            TripTracking.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }
        tripId = requestedTrip
        if (!startInForeground(intent?.getStringExtra(EXTRA_STOP_NAME) ?: TripTracking.stopName(this))) {
            TripTracking.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }
        startListening()
        // Keep the trip broadcast alive if the system reclaims the process mid-trip.
        return START_STICKY
    }

    /** Returns false when Android refuses the foreground promotion, so the caller can stop. */
    private fun startInForeground(destination: String?): Boolean {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.tracking_channel), NotificationManager.IMPORTANCE_LOW)
                    .apply {
                        description = getString(R.string.tracking_channel_detail)
                        setShowBadge(false)
                    }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, TripBroadcastService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mireli)
            .setContentTitle(getString(R.string.tracking_title))
            .setContentText(
                if (destination.isNullOrBlank()) getString(R.string.tracking_text)
                else getString(R.string.tracking_text_destination, destination)
            )
            .setContentIntent(open)
            .addAction(0, getString(R.string.tracking_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (_: SecurityException) {
            // Location foreground type not permitted for this build/permission state.
            false
        } catch (_: IllegalStateException) {
            // startForegroundService() was called from the background; the trip can be resumed
            // from the app the next time it is visible.
            false
        }
    }

    private fun startListening() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fine = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            stopSelf()
            return
        }
        val providers = buildList {
            if (fine) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { it in manager.allProviders }
        if (providers.isEmpty()) {
            stopSelf()
            return
        }
        for (provider in providers) {
            runCatching {
                manager.requestLocationUpdates(provider, PositionBroadcast.MIN_INTERVAL_MS, 0f, this, Looper.getMainLooper())
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        val id = tripId ?: return
        val ageMs = (android.os.SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        if (ageMs !in 0 until MAX_FIX_AGE_MS) return
        val point = location.latitude to location.longitude
        val valid = point.first.isFinite() && point.second.isFinite() &&
            point.first in -90.0..90.0 && point.second in -180.0..180.0 &&
            !(point.first == 0.0 && point.second == 0.0)
        val now = android.os.SystemClock.elapsedRealtime()
        if (!PositionBroadcast.shouldSend(PHASE_BROADCAST, valid, lastSentElapsedMs, now)) return
        if (inFlight) return
        inFlight = true
        lastSentElapsedMs = now
        val token = sessions.load()?.token
        if (token == null) {
            inFlight = false
            TripTracking.stop(this)
            stopSelf()
            return
        }
        val body = PositionBroadcast.payload(
            tripId = id,
            latitude = point.first,
            longitude = point.second,
            accuracyM = if (location.hasAccuracy()) location.accuracy else null,
            bearingDeg = if (location.hasBearingAccuracy()) location.bearing else null,
            speedMps = if (location.hasSpeed()) location.speed else null,
            recordedAtIsoUtc = Instant.ofEpochMilli(location.time).toString(),
            idempotencyKey = UUID.randomUUID().toString()
        )
        scope.launch {
            try {
                api.request("/position", "POST", token, json = body)
            } catch (error: com.mireli.driver.data.DriverServiceException) {
                if (PositionBroadcast.shouldStopOn(error.status, error.code)) {
                    TripTracking.stop(this@TripBroadcastService)
                    stopSelf()
                }
                // Transient failures are dropped: the next fix supersedes this one.
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Network loss: keep broadcasting; the next fix carries the latest state.
            } finally {
                inFlight = false
            }
        }
    }

    override fun onProviderDisabled(provider: String) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onDestroy() {
        runCatching {
            (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this)
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TRIP_ID = "tripId"
        const val EXTRA_STOP_NAME = "stopName"
        const val ACTION_START = "com.mireli.driver.action.START_TRACKING"
        const val ACTION_STOP = "com.mireli.driver.action.STOP_TRACKING"

        private const val CHANNEL_ID = "mireli_trip_broadcast"
        private const val NOTIFICATION_ID = 41
        private const val MAX_FIX_AGE_MS = 45_000L

        /**
         * The phase is confirmed by the server on every position write; until the
         * tracking endpoints exist the service assumes the caller only starts it
         * on an active trip and stops on the first server rejection.
         */
        private const val PHASE_BROADCAST = "in_progress"

        /** Feature gate: the server must advertise tracking before the app offers it. */
        fun supported(status: JSONObject): Boolean = status.optBoolean("trackingOpen", false)
    }
}
