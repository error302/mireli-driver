package com.mireli.driver.data

import android.content.Context
import com.mireli.driver.domain.*
import kotlinx.coroutines.flow.MutableStateFlow

/** Fail closed until an authenticated Mireli Web adapter has been implemented and verified. */
object RepositoryFactory {
    const val isDemo = false
    fun create(context: Context): DriverRepository = UnconfiguredRepository()
}
private class UnconfiguredRepository : DriverRepository {
    override val trips = MutableStateFlow<List<Trip>>(emptyList())
    override suspend fun execute(tripId: String, expectedVersion: Int, commandId: String, command: TripCommand): Change =
        Change.Rejected("Your organization connection is not configured.")
    override suspend fun reset() = Unit
}
