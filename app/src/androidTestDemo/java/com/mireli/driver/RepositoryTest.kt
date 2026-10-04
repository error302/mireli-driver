package com.mireli.driver

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.mireli.driver.data.RepositoryFactory
import com.mireli.driver.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun cleanPreview() = runBlocking { RepositoryFactory.create(context).reset() }

    @Test fun repeatedCommandHasOneEffect() = runBlocking {
        val repo = RepositoryFactory.create(context)
        val trip = repo.trips.value.first()
        val first = repo.execute(trip.id, 0, "same-command", TripCommand.Accept)
        assertEquals(first, repo.execute(trip.id, 0, "same-command", TripCommand.Accept))
        assertEquals(1, repo.trips.value.first().version)
    }

    @Test fun reusedCommandWithDifferentBodyIsRejected() = runBlocking {
        val repo = RepositoryFactory.create(context)
        val trip = repo.trips.value.first()
        repo.execute(trip.id, 0, "same-command", TripCommand.Accept)
        assertTrue(repo.execute(trip.id, 1, "same-command", TripCommand.Arrive) is Change.Rejected)
    }

    @Test fun acceptedTripSurvivesRepositoryRecreation() = runBlocking {
        val repo = RepositoryFactory.create(context)
        repo.execute(repo.trips.value.first().id, 0, "accept", TripCommand.Accept)
        val recreated = RepositoryFactory.create(context)
        assertEquals(TripStage.ACCEPTED, recreated.trips.value.first().stage)
        assertEquals(1, recreated.trips.value.first().version)
    }
}
