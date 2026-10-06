package com.mireli.driver.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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

private val previewMutex = Mutex()

private class PreviewDatabase(context: Context) : SQLiteOpenHelper(context.applicationContext, "preview_commands.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE state (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE commands (id TEXT PRIMARY KEY, owner TEXT NOT NULL, trip TEXT NOT NULL, version INTEGER NOT NULL, body TEXT NOT NULL, status TEXT NOT NULL, result TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Explicit preview migration required") }
}

/** Synthetic authority + durable local queue. Not a network adapter or production offline service. */
private class PreviewRepository(context: Context) : DriverRepository {
    private val db = PreviewDatabase(context).writableDatabase
    init {
        db.execSQL("INSERT OR IGNORE INTO state(key,value) VALUES('trips', ?)", arrayOf(context.getSharedPreferences("preview_state", Context.MODE_PRIVATE).getString("trips", "{}") ?: "{}"))
        db.execSQL("INSERT OR IGNORE INTO state(key,value) VALUES('connected','true')")
    }
    private val mutableTrips = MutableStateFlow(restore())
    override val trips: StateFlow<List<Trip>> = mutableTrips
    override val connected = MutableStateFlow(read("connected") == "true")
    override val pendingCount = MutableStateFlow(pending())
    override val syncMessage = MutableStateFlow<String?>(null)
    private fun read(key: String): String = db.rawQuery("SELECT value FROM state WHERE key=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else "{}" }
    private fun write(key: String, value: String) { db.execSQL("INSERT OR REPLACE INTO state(key,value) VALUES(?,?)", arrayOf(key, value)) }
    private fun pending(): Int = db.rawQuery("SELECT COUNT(*) FROM commands WHERE owner='preview' AND status='pending'", null).use { it.moveToFirst(); it.getInt(0) }
    private fun refresh() { mutableTrips.value = restore(); pendingCount.value = pending(); connected.value = read("connected") == "true" }

    private fun restore(): List<Trip> = decodeTrips(JSONObject(read("trips")))
    private fun decodeTrips(saved: JSONObject): List<Trip> =
        fixtures().map { trip ->
            val row = saved.optJSONObject(trip.id) ?: return@map trip
            val people = row.getJSONObject("boarding")
            val stage = TripStage.valueOf(row.getString("stage"))
            val restored = trip.copy(stage = stage, version = row.getInt("version"),
                assignment = AssignmentState.valueOf(row.optString("assignment", if (stage == TripStage.ASSIGNED) "PENDING" else "ACCEPTED")),
                declineReason = if (row.isNull("declineReason")) null else row.getString("declineReason"),
                passengers = trip.passengers.map {
                    val state = Boarding.valueOf(people.getString(it.id))
                    it.copy(boarding = state, boardedCount = row.optJSONObject("boardedCounts")?.optInt(it.id) ?: if (state == Boarding.BOARDED) it.seats else 0,
                        noShowCount = row.optJSONObject("noShowCounts")?.optInt(it.id) ?: if (state == Boarding.NO_SHOW) it.seats else 0)
                })
            require(restored.stage !in listOf(TripStage.IN_PROGRESS, TripStage.COMPLETED) ||
                (restored.boardedSeats > 0 && restored.passengers.none { it.unresolvedSeats > 0 }))
            restored
        }

    override suspend fun execute(tripId: String, expectedVersion: Int, commandId: String, command: TripCommand): Change =
        withContext(Dispatchers.IO) {
            previewMutex.withLock {
                require(commandId.isNotBlank() && commandId.length <= 100)
                val body = encodeCommand(command).toString()
                db.rawQuery("SELECT trip,version,body,status,result FROM commands WHERE id=? AND owner='preview'", arrayOf(commandId)).use { cursor ->
                    if (cursor.moveToFirst()) {
                        if (cursor.getString(0) != tripId || cursor.getInt(1) != expectedVersion || cursor.getString(2) != body)
                            return@withLock Change.Rejected("Command ID was already used.")
                        return@withLock receipt(commandId, tripId, cursor.getString(3), cursor.getString(4))
                    }
                }
                refresh()
                if (!connected.value && command !is TripCommand.Board) return@withLock Change.Rejected("This action requires a connection. Only boarding may wait to sync.")
                if (pending() > 0) return@withLock Change.Rejected("Sync your saved boarding action before recording another change.")
                val trip = mutableTrips.value.find { it.id == tripId }
                    ?: return@withLock Change.Rejected("Trip was not found.")
                val preflight = TripRules.apply(trip, expectedVersion, command)
                if (preflight is Change.Rejected) return@withLock preflight
                // Commit the command BEFORE delivering it to the synthetic authority.
                db.execSQL("INSERT INTO commands(id,owner,trip,version,body,status) VALUES(?,'preview',?,?,?,'pending')",
                    arrayOf<Any>(commandId, tripId, expectedVersion, body))
                pendingCount.value = pending()
                if (!connected.value) return@withLock Change.Queued(commandId)
                val response = deliver(commandId, tripId, expectedVersion, command)
                refresh()
                response
            }
        }

    private fun receipt(id: String, trip: String, status: String, result: String?): Change = when (status) {
        "done" -> Change.Applied(decodeTrips(JSONObject(requireNotNull(result))).first { it.id == trip })
        "failed" -> Change.Rejected(result ?: "The action was rejected.")
        else -> Change.Queued(id)
    }
    private fun deliver(id: String, tripId: String, version: Int, command: TripCommand): Change {
        db.beginTransaction()
        try {
            val all = restore()
            val trip = all.first { it.id == tripId }
            val response = if (command == TripCommand.Start && all.any { it.id != tripId && it.stage == TripStage.IN_PROGRESS })
                Change.Rejected("Complete the current journey before starting another.") else TripRules.apply(trip, version, command)
            when (response) {
                is Change.Applied -> {
                    val snapshot = encodeTrips(all.map { if (it.id == tripId) response.trip else it }).toString()
                    write("trips", snapshot)
                    db.execSQL("UPDATE commands SET status='done',result=? WHERE id=?", arrayOf(snapshot, id))
                }
                is Change.Rejected -> db.execSQL("UPDATE commands SET status='failed',result=? WHERE id=?", arrayOf(response.reason, id))
                is Change.Queued -> error("Synthetic authority cannot queue a command")
            }
            db.setTransactionSuccessful()
            return response
        } finally { db.endTransaction() }
    }

    override suspend fun setConnected(value: Boolean) = withContext(Dispatchers.IO) {
        previewMutex.withLock {
            write("connected", value.toString())
            syncMessage.value = null
            if (value) {
                val rows = mutableListOf<Array<String>>()
                db.rawQuery("SELECT id,trip,version,body FROM commands WHERE owner='preview' AND status='pending' ORDER BY rowid", null).use { cursor ->
                    while (cursor.moveToNext()) rows.add(arrayOf(cursor.getString(0), cursor.getString(1), cursor.getInt(2).toString(), cursor.getString(3)))
                }
                rows.forEach { row ->
                    val outcome = deliver(row[0], row[1], row[2].toInt(), decodeCommand(JSONObject(row[3])))
                    syncMessage.value = when (outcome) {
                        is Change.Applied -> "Saved boarding confirmed by the local simulator. No live server was contacted."
                        is Change.Rejected -> "Sync needs attention: ${outcome.reason}"
                        is Change.Queued -> "Waiting to sync"
                    }
                }
            }
            refresh()
        }
    }

    private fun encodeTrips(trips: List<Trip>): JSONObject {
        val result = JSONObject()
        trips.forEach { trip ->
            val boarding = JSONObject()
            val counts = JSONObject()
            val noShows = JSONObject()
            trip.passengers.forEach { boarding.put(it.id, it.boarding.name) }
            trip.passengers.forEach { counts.put(it.id, it.boardedCount); noShows.put(it.id, it.noShowCount) }
            result.put(trip.id, JSONObject().put("stage", trip.stage.name).put("version", trip.version).put("boarding", boarding)
                .put("boardedCounts", counts).put("noShowCounts", noShows).put("assignment", trip.assignment.name)
                .put("declineReason", trip.declineReason ?: JSONObject.NULL))
        }
        return result
    }

    override suspend fun reset() = withContext(Dispatchers.IO) {
        previewMutex.withLock {
            db.beginTransaction()
            try { write("trips", encodeTrips(fixtures()).toString()); write("connected", "true")
                db.execSQL("DELETE FROM commands WHERE owner='preview'"); db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            syncMessage.value = null
            refresh()
        }
    }
    private fun encodeCommand(command: TripCommand): JSONObject = JSONObject().apply {
        when (command) {
            TripCommand.Accept -> put("type", "accept")
            TripCommand.Arrive -> put("type", "arrive")
            TripCommand.Start -> put("type", "start")
            TripCommand.Complete -> put("type", "complete")
            is TripCommand.Decline -> { put("type", "decline"); put("reason", command.reason) }
            is TripCommand.MarkNoShow -> { put("type", "no_show"); put("passenger", command.passengerId) }
            is TripCommand.Board -> { put("type", "board"); put("passenger", command.passengerId); put("code", command.code); put("count", command.count ?: JSONObject.NULL) }
        }
    }
    private fun decodeCommand(json: JSONObject): TripCommand = when (json.getString("type")) {
        "accept" -> TripCommand.Accept; "arrive" -> TripCommand.Arrive; "start" -> TripCommand.Start; "complete" -> TripCommand.Complete
        "decline" -> TripCommand.Decline(json.getString("reason"))
        "no_show" -> TripCommand.MarkNoShow(json.getString("passenger"))
        "board" -> TripCommand.Board(json.getString("passenger"), json.getString("code"), if (json.isNull("count")) null else json.getInt("count"))
        else -> error("Unknown stored action")
    }
}
