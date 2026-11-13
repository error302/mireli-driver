package com.mireli.driver.domain

import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SgrTimetableTest {
    @Test fun overnightArrivalUsesOriginServiceDay() {
        val fridayNight = SgrTimetable.Service("Night Train", SgrTimetable.Movement.Arrival,
            LocalTime.of(3, 55), 1, setOf(5))
        val saturdayDeparture = SgrTimetable.Service("Express", SgrTimetable.Movement.Departure,
            LocalTime.of(8, 0), 0, setOf(6))

        val events = SgrTimetable.upcoming(listOf(fridayNight, saturdayDeparture),
            Instant.parse("2026-11-13T23:00:00Z"))

        assertEquals(Instant.parse("2026-11-14T00:55:00Z"), events.first().at)
        assertEquals(Instant.parse("2026-11-14T05:00:00Z"), events[1].at)
    }

    @Test fun upcomingRollsOverAfterTheLastTrain() {
        val service = SgrTimetable.Service("Express", SgrTimetable.Movement.Arrival,
            LocalTime.of(20, 30), 0, (0..6).toSet())

        val events = SgrTimetable.upcoming(listOf(service), Instant.parse("2026-11-14T20:00:00Z"))

        assertEquals(Instant.parse("2026-11-15T17:30:00Z"), events.first().at)
    }
}
