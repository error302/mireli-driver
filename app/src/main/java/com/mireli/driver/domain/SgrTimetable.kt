package com.mireli.driver.domain

import java.time.Instant
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

    fun upcoming(services: List<Service>, now: Instant): List<Event> {
        val today = now.atZone(zone).toLocalDate()
        return (-1L..7L).flatMap { offset ->
            val originDate = today.plusDays(offset)
            services.mapNotNull { service ->
                if (originDate.dayOfWeek.value % 7 !in service.serviceDays) return@mapNotNull null
                val at = originDate.plusDays(service.dayOffset).atTime(service.clock).atZone(zone).toInstant()
                if (at < now.minusSeconds(45 * 60)) null else Event(service, at)
            }
        }.sortedBy { it.at }
    }
}
