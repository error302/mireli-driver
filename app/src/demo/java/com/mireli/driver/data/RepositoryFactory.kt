package com.mireli.driver.data

import android.content.Context
import com.mireli.driver.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object RepositoryFactory {
    const val isDemo = true
    fun create(context: Context): DriverRepository = PreviewRepository(context)
}
private fun fixtures() = listOf(
    Trip("SGR-1042", "Mombasa SGR", "Nyali · City Mall", "14:10", ServiceType.SHARED, "KDM 204M · Toyota Hiace", 10, 420000,
        listOf(Passenger("p1", "Amina K.", 2, "1042"), Passenger("p2", "Brian M.", 1, "2042"), Passenger("p3", "Grace W.", 3, "3042"))),
    Trip("SGR-1043", "Bamburi · Meeting point", "Mombasa SGR", "17:30", ServiceType.CHARTER, "KDM 204M · Toyota Hiace", 10, 650000,
        listOf(Passenger("p4", "Daniel O. · Group lead", 5, "4042")))
)

/** Synthetic training state only. No customer data or live financial effects. */
private class PreviewRepository(context: Context) : DriverRepository {
    private val prefs = context.applicationContext.getSharedPreferences("preview_state", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutableTrips = MutableStateFlow(restore())
    override val trips: StateFlow<List<Trip>> = mutableTrips
    private val responses = mutableMapOf<String, Pair<String, Change>>()

    private fun restore(): List<Trip> = runCatching {
        val saved = JSONObject(prefs.getString("trips", "{}") ?: "{}")
        fixtures().map { trip ->
            val row = saved.optJSONObject(trip.id) ?: return@map trip
            val people = row.getJSONObject("boarding")
            val restored = trip.copy(stage = TripStage.valueOf(row.getString("stage")), version = row.getInt("version"),
                passengers = trip.passengers.map { it.copy(boarding = Boarding.valueOf(people.getString(it.id))) })
            require(restored.stage !in listOf(TripStage.IN_PROGRESS, TripStage.COMPLETED) ||
                (restored.boardedSeats > 0 && restored.passengers.none { it.boarding == Boarding.WAITING }))
            restored
        }
    }.getOrElse { fixtures() }

    override suspend fun execute(tripId: String, expectedVersion: Int, commandId: String, command: TripCommand): Change =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val fingerprint = "$tripId|$expectedVersion|$command"
                responses[commandId]?.let { (previous, response) ->
                    return@withLock if (previous == fingerprint) response else Change.Rejected("Command ID was already used.")
                }
                val trip = mutableTrips.value.find { it.id == tripId }
                    ?: return@withLock Change.Rejected("Trip was not found.")
                // One active journey at a time, even in preview.
                if (command == TripCommand.Start && mutableTrips.value.any { it.id != tripId && it.stage == TripStage.IN_PROGRESS })
                    return@withLock Change.Rejected("Complete the current journey before starting another.")
                val response = TripRules.apply(trip, expectedVersion, command)
                if (response is Change.Applied) {
                    val next = mutableTrips.value.map { if (it.id == tripId) response.trip else it }
                    if (!save(next)) return@withLock Change.Rejected("Could not save this action. Please retry.")
                    mutableTrips.value = next
                }
                responses[commandId] = fingerprint to response
                response
            }
        }

    private fun save(trips: List<Trip>): Boolean {
        val result = JSONObject()
        trips.forEach { trip ->
            val boarding = JSONObject()
            trip.passengers.forEach { boarding.put(it.id, it.boarding.name) }
            result.put(trip.id, JSONObject().put("stage", trip.stage.name).put("version", trip.version).put("boarding", boarding))
        }
        return prefs.edit().putString("trips", result.toString()).commit()
    }

    override suspend fun reset() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = fixtures()
            check(save(next)) { "Could not reset preview." }
            responses.clear()
            mutableTrips.value = next
        }
    }
}

