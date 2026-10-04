package com.mireli.driver.data

import com.mireli.driver.domain.*
import kotlinx.coroutines.flow.StateFlow

interface DriverRepository {
    val trips: StateFlow<List<Trip>>
    suspend fun execute(tripId: String, expectedVersion: Int, commandId: String, command: TripCommand): Change
    suspend fun reset()
}
