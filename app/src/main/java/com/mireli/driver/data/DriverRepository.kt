package com.mireli.driver.data

import com.mireli.driver.domain.*
import kotlinx.coroutines.flow.StateFlow

interface DriverRepository {
    val trips: StateFlow<List<Trip>>
    val pendingCount: StateFlow<Int>
    val connected: StateFlow<Boolean>
    val syncMessage: StateFlow<String?>
    suspend fun setConnected(value: Boolean)
    suspend fun execute(tripId: String, expectedVersion: Int, commandId: String, command: TripCommand): Change
    suspend fun reset()
}
