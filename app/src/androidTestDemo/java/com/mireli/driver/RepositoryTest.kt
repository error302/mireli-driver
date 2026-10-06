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
    @Test fun offlineBoardingSurvivesRecreationAndReplaysExactlyOnce() = runBlocking {
        val repo = RepositoryFactory.create(context)
        val id = repo.trips.value.first().id
        repo.execute(id, 0, "accept-offline-test", TripCommand.Accept)
        repo.execute(id, 1, "arrive-offline-test", TripCommand.Arrive)
        repo.setConnected(false)
        val command = TripCommand.Board("p1", "1042", 1)
        assertTrue(repo.execute(id, 2, "durable-board", command) is Change.Queued)
        assertEquals(0, repo.trips.value.first().boardedSeats)
        val restarted = RepositoryFactory.create(context)
        assertEquals(1, restarted.pendingCount.value)
        assertFalse(restarted.connected.value)
        restarted.setConnected(true)
        assertEquals(1, restarted.trips.value.first().boardedSeats)
        assertEquals(0, restarted.pendingCount.value)
        val afterRestart = RepositoryFactory.create(context)
        assertTrue(afterRestart.execute(id, 2, "durable-board", command) is Change.Applied)
        assertEquals(1, afterRestart.trips.value.first().boardedSeats)
        assertEquals(3, afterRestart.trips.value.first().version)
    }
    @Test fun assignmentAcceptanceCannotBeQueuedOffline() = runBlocking {
        val repo = RepositoryFactory.create(context)
        repo.setConnected(false)
        assertTrue(repo.execute(repo.trips.value.first().id, 0, "offline-accept", TripCommand.Accept) is Change.Rejected)
        assertEquals(0, repo.pendingCount.value)
    }
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
