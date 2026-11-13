package com.mireli.driver.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal object SgrTimetable {
    val zone: ZoneId = ZoneId.of("Africa/Nairobi")

    enum class Movement { Arrival, Departure }

    data class Service(
        val name: String,
        val movement: Movement,
        val clock: LocalTime,
        val dayOffset: Long,
        val serviceDays: Set<Int>,
    )

    data class Event(val service: Service, val at: Instant)

    fun eventsOn(services: List<Service>, date: LocalDate): List<Event> =
        services.mapNotNull { service ->
            val originDate = date.minusDays(service.dayOffset)
            if (originDate.dayOfWeek.value % 7 !in service.serviceDays) return@mapNotNull null
            Event(service, date.atTime(service.clock).atZone(zone).toInstant())
        }.sortedBy(Event::at)

    fun upcoming(services: List<Service>, now: Instant): List<Event> {
        val today = now.atZone(zone).toLocalDate()
        return (-1L..8L).flatMap { offset -> eventsOn(services, today.plusDays(offset)) }
            .filter { it.at >= now.minusSeconds(45 * 60) }
    }
}
