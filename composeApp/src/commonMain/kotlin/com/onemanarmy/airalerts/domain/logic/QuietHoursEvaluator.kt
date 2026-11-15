package com.onemanarmy.airalerts.domain.logic

import com.onemanarmy.airalerts.domain.model.AppSettings
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Evaluates whether a given instant falls inside the configured Night Mode ("Quiet Hours") schedule.
 */
object QuietHoursEvaluator {

    fun isInQuietHours(
        settings: AppSettings,
        instant: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean {
        if (!settings.nightModeEnabled || settings.nightActiveDaysIso.isEmpty()) {
            return false
        }

        val localDateTime = instant.toLocalDateTime(timeZone)
        val currentMinutes = localDateTime.hour * 60 + localDateTime.minute
        val startMinutes = settings.nightStartHour * 60 + settings.nightStartMinute
        val endMinutes = settings.nightEndHour * 60 + settings.nightEndMinute
        val todayIso = localDateTime.dayOfWeek.isoDayNumber

        if (startMinutes == endMinutes) {
            // 24-hour quiet schedule for enabled days.
            return todayIso in settings.nightActiveDaysIso
        }

        return if (startMinutes < endMinutes) {
            // Same-day window (e.g. 13:00 - 15:00).
            todayIso in settings.nightActiveDaysIso &&
                currentMinutes in startMinutes until endMinutes
        } else {
            // Overnight window (e.g. 23:00 - 07:00).
            // Before midnight belongs to today's schedule; after midnight belongs to yesterday's schedule.
            if (currentMinutes >= startMinutes) {
                todayIso in settings.nightActiveDaysIso
            } else if (currentMinutes < endMinutes) {
                val yesterdayIso = localDateTime.date.minus(1, DateTimeUnit.DAY).dayOfWeek.isoDayNumber
                yesterdayIso in settings.nightActiveDaysIso
            } else {
                false
            }
        }
    }
}
