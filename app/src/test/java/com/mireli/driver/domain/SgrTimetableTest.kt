package com.mireli.driver.domain

import java.time.Instant
import java.time.LocalTime
import java.time.LocalDate
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

    @Test fun fullDayBoardRetainsPastEventsAndSortsBothDirections() {
        val arrival = SgrTimetable.Movement.Arrival
        val departure = SgrTimetable.Movement.Departure
        val services = listOf(
            SgrTimetable.Service("Express", departure, LocalTime.of(15, 0), 0, setOf(6)),
            SgrTimetable.Service("Inter-County", arrival, LocalTime.of(14, 0), 0, setOf(6)),
            SgrTimetable.Service("Night Train", arrival, LocalTime.of(3, 55), 1, setOf(5)),
            SgrTimetable.Service("Inter-County", departure, LocalTime.of(8, 0), 0, setOf(6)),
        )
        val events = SgrTimetable.eventsOn(services, LocalDate.of(2026, 11, 14))
        assertEquals(listOf("03:55", "08:00", "14:00", "15:00"),
            events.map { it.at.atZone(SgrTimetable.zone).toLocalTime().toString() })
        assertEquals(listOf(arrival, departure, arrival, departure), events.map { it.service.movement })
    }
}
