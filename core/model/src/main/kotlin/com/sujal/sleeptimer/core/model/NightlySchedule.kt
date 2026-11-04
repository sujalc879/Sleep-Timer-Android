package com.sujal.sleeptimer.core.model

import java.time.Instant
import java.time.ZoneId

/** One start per local date: gaps move forward; overlaps use the earlier offset. */
fun nextNightlyOccurrence(settings: AppSettings, now: Instant, zone: ZoneId): Instant? {
    if (!settings.nightlyEnabled || settings.nightlyDays.isEmpty()) return null
    val today = now.atZone(zone).toLocalDate()
    return (0..DAYS_AHEAD).asSequence().map { today.plusDays(it.toLong()) }
        .filter { it.dayOfWeek.value in settings.nightlyDays }
        .map { it.atTime(settings.nightlyHour, settings.nightlyMinute).atZone(zone).toInstant() }
        .firstOrNull { it > now }
}

private const val DAYS_AHEAD = 7
