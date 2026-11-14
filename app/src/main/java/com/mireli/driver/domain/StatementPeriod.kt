package com.mireli.driver.domain

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

internal enum class StatementPeriod(val label: String) {
    Week("This week"),
    Month("This month"),
    All("All time");

    fun includes(line: JSONObject, now: Instant): Boolean {
        if (this == All) return true
        val zone = ZoneId.of("Africa/Nairobi")
        val today = now.atZone(zone).toLocalDate()
        val start = when (this) {
            Week -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            Month -> today.withDayOfMonth(1)
            All -> return true
        }.atStartOfDay(zone).toInstant()
        val field = if (line.optString("status") == "completed") "completedAt" else "initiatedAt"
        val stamp = runCatching { Instant.parse(line.getString(field)) }.getOrNull() ?: return false
        return stamp >= start && stamp <= now
    }
}
