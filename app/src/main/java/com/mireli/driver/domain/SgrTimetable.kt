package com.mireli.driver.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Published Madaraka Express schedule for Mombasa Terminus.
 *
 * This is public timetable information, not live railway telemetry: the app says
 * "published" everywhere it is shown and never claims a train is on time. Update
 * [services] when Kenya Railways revises the timetable; confirm with the operator
 * before changing what the driver is told.
 */
object SgrTimetable {
    data class Service(val id: String, val label: String, val fromMombasa: Boolean,
        val departsClock: String, val arrivesClock: String)

    /** Kenya Railways published Inter-County Madaraka Express services (EAT). */
    val services = listOf(
        Service("n1", "Nairobi → Mombasa (Madaraka Express)", fromMombasa = false,
            departsClock = "08:00", arrivesClock = "14:00"),
        Service("n2", "Mombasa → Nairobi (Madaraka Express)", fromMombasa = true,
            departsClock = "15:30", arrivesClock = "21:36"),
    )

    data class Event(val service: Service, val kind: String, val at: Instant, val clock: String)

    private fun todayAt(clock: String, zone: ZoneId, now: Instant): Instant {
        val parts = clock.split(":")
        val local = LocalDateTime.ofInstant(now, zone).withHour(parts[0].toInt()).withMinute(parts[1].toInt()).withSecond(0)
        return local.atZone(zone).toInstant()
    }

    /**
     * Upcoming published events at Mombasa Terminus, soonest first. A service appears as
     * "Arriving" when the inbound train is due today and "Departing" for the outbound one.
     */
    fun upcoming(now: Instant, zone: ZoneId = ZoneId.of("Africa/Nairobi"), limit: Int = 3): List<Event> {
        val events = services.flatMap { service ->
            buildList {
                if (!service.fromMombasa) add(Event(service, "Arriving", todayAt(service.arrivesClock, zone, now), service.arrivesClock))
                add(Event(service, "Departing", todayAt(service.departsClock, zone, now), service.departsClock))
            }
        }.filter { it.at.isAfter(now.minusSeconds(30 * 60)) }
            .sortedBy { it.at }
        return events.take(limit)
    }
}
