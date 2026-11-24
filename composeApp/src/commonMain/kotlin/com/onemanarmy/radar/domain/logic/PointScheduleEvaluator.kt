package com.onemanarmy.radar.domain.logic

import com.onemanarmy.radar.domain.model.SavedPoint
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Evaluates whether monitoring and notifications are active for a [SavedPoint]
 * at a given [instant] according to its configured time-window and days-of-week schedule.
 */
object PointScheduleEvaluator {

    fun isMonitoringActive(
        point: SavedPoint,
        instant: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean {
        if (!point.scheduleEnabled) {
            return true
        }
        if (point.scheduleActiveDaysIso.isEmpty()) {
            return false
        }

        val localDateTime = instant.toLocalDateTime(timeZone)
        val currentMinutes = localDateTime.hour * 60 + localDateTime.minute
        val startMinutes = point.scheduleStartHour * 60 + point.scheduleStartMinute
        val endMinutes = point.scheduleEndHour * 60 + point.scheduleEndMinute
        val todayIso = localDateTime.dayOfWeek.isoDayNumber

        if (startMinutes == endMinutes) {
            // 24-hour monitoring on enabled days.
            return todayIso in point.scheduleActiveDaysIso
        }

        return if (startMinutes < endMinutes) {
            // Same-day window (e.g. 09:00 - 18:00).
            todayIso in point.scheduleActiveDaysIso &&
                currentMinutes in startMinutes until endMinutes
        } else {
            // Overnight window (e.g. 18:00 - 09:00).
            // Before midnight belongs to today's schedule; after midnight belongs to yesterday's schedule.
            if (currentMinutes >= startMinutes) {
                todayIso in point.scheduleActiveDaysIso
            } else if (currentMinutes < endMinutes) {
                val yesterdayIso = localDateTime.date.minus(1, DateTimeUnit.DAY).dayOfWeek.isoDayNumber
                yesterdayIso in point.scheduleActiveDaysIso
            } else {
                false
            }
        }
    }
}
