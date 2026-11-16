package com.onemanarmy.radar.domain.logic

import com.onemanarmy.radar.domain.model.AppSettings
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuietHoursEvaluatorTest {

    private val kyivZone = TimeZone.of("Europe/Kyiv")

    @Test
    fun disabledQuietHoursAlwaysReturnsFalse() {
        val settings = AppSettings(nightModeEnabled = false)
        val midnight = LocalDateTime(2026, 11, 15, 23, 30).toInstant(kyivZone)
        assertFalse(QuietHoursEvaluator.isInQuietHours(settings, midnight, kyivZone))
    }

    @Test
    fun overnightScheduleMatchesBeforeAndAfterMidnight() {
        val settings = AppSettings(
            nightModeEnabled = true,
            nightStartHour = 23,
            nightStartMinute = 0,
            nightEndHour = 7,
            nightEndMinute = 0,
            nightActiveDaysIso = setOf(1, 2, 3, 4, 5, 6, 7)
        )

        val at2315 = LocalDateTime(2026, 11, 15, 23, 15).toInstant(kyivZone)
        val at0330 = LocalDateTime(2026, 11, 16, 3, 30).toInstant(kyivZone)
        val at0700 = LocalDateTime(2026, 11, 16, 7, 0).toInstant(kyivZone)
        val at1400 = LocalDateTime(2026, 11, 16, 14, 0).toInstant(kyivZone)

        assertTrue(QuietHoursEvaluator.isInQuietHours(settings, at2315, kyivZone))
        assertTrue(QuietHoursEvaluator.isInQuietHours(settings, at0330, kyivZone))
        assertFalse(QuietHoursEvaluator.isInQuietHours(settings, at0700, kyivZone))
        assertFalse(QuietHoursEvaluator.isInQuietHours(settings, at1400, kyivZone))
    }

    @Test
    fun overnightScheduleRespectsPreviousDayAfterMidnight() {
        // 2026-11-15 is Sunday (ISO day 7); 2026-11-16 is Monday (ISO day 1).
        // Only Sunday (7) is enabled -> Monday 02:00 belongs to Sunday night's schedule.
        val settings = AppSettings(
            nightModeEnabled = true,
            nightStartHour = 23,
            nightStartMinute = 0,
            nightEndHour = 7,
            nightEndMinute = 0,
            nightActiveDaysIso = setOf(7)
        )

        val monday0200 = LocalDateTime(2026, 11, 16, 2, 0).toInstant(kyivZone)
        val monday2330 = LocalDateTime(2026, 11, 16, 23, 30).toInstant(kyivZone)

        assertTrue(QuietHoursEvaluator.isInQuietHours(settings, monday0200, kyivZone))
        assertFalse(QuietHoursEvaluator.isInQuietHours(settings, monday2330, kyivZone))
    }

    @Test
    fun sameDayScheduleWorksCorrectly() {
        val settings = AppSettings(
            nightModeEnabled = true,
            nightStartHour = 13,
            nightStartMinute = 0,
            nightEndHour = 15,
            nightEndMinute = 30,
            nightActiveDaysIso = setOf(1, 2, 3, 4, 5, 6, 7)
        )

        val inside = LocalDateTime(2026, 11, 16, 14, 15).toInstant(kyivZone)
        val outside = LocalDateTime(2026, 11, 16, 16, 0).toInstant(kyivZone)

        assertTrue(QuietHoursEvaluator.isInQuietHours(settings, inside, kyivZone))
        assertFalse(QuietHoursEvaluator.isInQuietHours(settings, outside, kyivZone))
    }
}
