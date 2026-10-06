package com.mireli.driver.domain

import org.junit.Assert.*
import org.junit.Test

class TripRulesTest {
    @Test fun partialBoardingLeavesRemainderUnresolved() {
        val t = changed(trip(TripStage.AT_PICKUP), TripCommand.Board("p", "1234", 1))
        assertEquals(1, t.boardedSeats)
        assertEquals(1, t.passengers.single().unresolvedSeats)
        assertTrue(TripRules.apply(t, t.version, TripCommand.Start) is Change.Rejected)
    }
    @Test fun remainingNoShowDoesNotUndoBoardedSeats() {
        var t = changed(trip(TripStage.AT_PICKUP), TripCommand.Board("p", "1234", 1))
        t = changed(t, TripCommand.MarkNoShow("p"))
        assertEquals(1, t.boardedSeats)
        assertEquals(1, t.passengers.single().noShowCount)
        assertEquals(TripStage.IN_PROGRESS, changed(t, TripCommand.Start).stage)
    }
    @Test fun cannotBoardMoreThanRemainingOrZero() {
        listOf(0, -1, 3, Int.MAX_VALUE).forEach { count ->
            assertTrue(TripRules.apply(trip(TripStage.AT_PICKUP), 0, TripCommand.Board("p", "1234", count)) is Change.Rejected)
        }
    }
    @Test fun declineRequiresReasonAndCannotBeAcceptedAfterwards() {
        assertTrue(TripRules.apply(trip(), 0, TripCommand.Decline("")) is Change.Rejected)
        val declined = changed(trip(), TripCommand.Decline("Vehicle breakdown"))
        assertEquals(AssignmentState.DECLINED, declined.assignment)
        assertTrue(TripRules.apply(declined, declined.version, TripCommand.Accept) is Change.Rejected)
    }
    @Test fun revokedAndExpiredAssignmentsRejectAllCommands() {
        listOf(AssignmentState.WITHDRAWN, AssignmentState.EXPIRED).forEach { state ->
            assertTrue(TripRules.apply(trip().copy(assignment = state), 0, TripCommand.Accept) is Change.Rejected)
        }
    }
    private fun trip(stage: TripStage = TripStage.ASSIGNED, people: List<Passenger> = listOf(Passenger("p", "Sample", 2, "1234"))) =
        Trip("t", "SGR", "Nyali", "14:10", ServiceType.SHARED, "Sample vehicle", 4, 80000, people, stage)
    private fun changed(t: Trip, c: TripCommand) = (TripRules.apply(t, t.version, c) as Change.Applied).trip

    @Test fun validJourneyPreservesMoneyAndCapacity() {
        var t = trip()
        listOf(TripCommand.Accept, TripCommand.Arrive, TripCommand.Board("p", "1234"), TripCommand.Start, TripCommand.Complete).forEach { t = changed(t, it) }
        assertEquals(TripStage.COMPLETED, t.stage)
        assertEquals(5, t.version)
        assertEquals(2, t.boardedSeats)
        assertEquals(80000L, t.fareMinor)
    }
    @Test fun staleCommandIsRejected() { assertTrue(TripRules.apply(trip(), 3, TripCommand.Accept) is Change.Rejected) }
    @Test fun cannotSkipAcceptance() { assertTrue(TripRules.apply(trip(), 0, TripCommand.Arrive) is Change.Rejected) }
    @Test fun cannotCompleteBeforeDeparture() { assertTrue(TripRules.apply(trip(), 0, TripCommand.Complete) is Change.Rejected) }
    @Test fun wrongCodeDoesNotBoard() { assertTrue(TripRules.apply(trip(TripStage.AT_PICKUP), 0, TripCommand.Board("p", "9999")) is Change.Rejected) }
    @Test fun unknownPassengerCannotBoard() { assertTrue(TripRules.apply(trip(TripStage.AT_PICKUP), 0, TripCommand.Board("other", "1234")) is Change.Rejected) }
    @Test fun duplicateBoardingDoesNotIncreaseCount() {
        val t = changed(trip(TripStage.AT_PICKUP), TripCommand.Board("p", "1234"))
        assertTrue(TripRules.apply(t, t.version, TripCommand.Board("p", "1234")) is Change.Rejected)
        assertEquals(2, t.boardedSeats)
    }
    @Test fun cannotDepartWithUnresolvedPassengers() { assertTrue(TripRules.apply(trip(TripStage.AT_PICKUP), 0, TripCommand.Start) is Change.Rejected) }
    @Test fun cannotDepartEmptyAfterNoShow() {
        val t = changed(trip(TripStage.AT_PICKUP), TripCommand.MarkNoShow("p"))
        assertTrue(TripRules.apply(t, t.version, TripCommand.Start) is Change.Rejected)
    }
    @Test fun boardedPassengerCannotBecomeNoShow() {
        val t = changed(trip(TripStage.AT_PICKUP), TripCommand.Board("p", "1234"))
        assertTrue(TripRules.apply(t, t.version, TripCommand.MarkNoShow("p")) is Change.Rejected)
    }
    @Test fun mixedBoardingAndNoShowCanDepart() {
        var t = trip(TripStage.AT_PICKUP, listOf(Passenger("p", "A", 2, "1234"), Passenger("q", "B", 1, "5678")))
        t = changed(t, TripCommand.Board("p", "1234"))
        t = changed(t, TripCommand.MarkNoShow("q"))
        assertEquals(TripStage.IN_PROGRESS, changed(t, TripCommand.Start).stage)
    }
    @Test(expected = IllegalArgumentException::class) fun overcapacityIsRejected() { trip(people = listOf(Passenger("p", "A", 5, "1234"))) }
    @Test(expected = IllegalArgumentException::class) fun duplicatePassengerIdsAreRejected() { trip(people = listOf(Passenger("p", "A", 1, "1234"), Passenger("p", "B", 1, "5678"))) }
    @Test(expected = IllegalArgumentException::class) fun zeroPartySizeIsRejected() { trip(people = listOf(Passenger("p", "A", 0, "1234"))) }
    @Test(expected = IllegalArgumentException::class) fun seatSumCannotOverflowIntoValidCapacity() {
        trip(people = listOf(Passenger("p", "A", Int.MAX_VALUE, "1234"), Passenger("q", "B", Int.MAX_VALUE, "5678")))
    }
    @Test fun completedJourneyCannotRestart() { assertTrue(TripRules.apply(trip(TripStage.COMPLETED), 0, TripCommand.Start) is Change.Rejected) }
}
