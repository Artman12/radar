package com.onemanarmy.radar.domain.logic

import com.onemanarmy.radar.domain.model.SavedPoint
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PointScheduleEvaluatorTest {

    private val kyivZone = TimeZone.of("Europe/Kyiv")

    private fun basePoint(
        scheduleEnabled: Boolean = true,
        startHour: Int = 9,
        startMinute: Int = 0,
        endHour: Int = 18,
        endMinute: Int = 0,
        activeDaysIso: Set<Int> = setOf(1, 2, 3, 4, 5)
    ) = SavedPoint(
        id = "office",
        name = "Office",
        latitude = 50.45,
        longitude = 30.52,
        regionId = "31",
        regionName = "м. Київ",
        scheduleEnabled = scheduleEnabled,
        scheduleStartHour = startHour,
        scheduleStartMinute = startMinute,
        scheduleEndHour = endHour,
        scheduleEndMinute = endMinute,
        scheduleActiveDaysIso = activeDaysIso
    )

    @Test
    fun disabledScheduleIsAlwaysActiveEvenWithNoDaysSelected() {
        val point = basePoint(scheduleEnabled = false, activeDaysIso = emptySet())
        val sundayNight = LocalDateTime(2026, 11, 15, 23, 45).toInstant(kyivZone)
        assertTrue(PointScheduleEvaluator.isMonitoringActive(point, sundayNight, kyivZone))
    }

    @Test
    fun enabledScheduleWithEmptyDaysIsNeverActive() {
        val point = basePoint(scheduleEnabled = true, activeDaysIso = emptySet())
        val mondayNoon = LocalDateTime(2026, 11, 16, 12, 0).toInstant(kyivZone)
        assertFalse(PointScheduleEvaluator.isMonitoringActive(point, mondayNoon, kyivZone))
    }

    @Test
    fun sameDayWorkScheduleRespectsHoursAndWeekdays() {
        // 2026-11-16 is Monday (1), 2026-11-15 is Sunday (7)
        val point = basePoint(
            startHour = 9,
            startMinute = 0,
            endHour = 18,
            endMinute = 0,
            activeDaysIso = setOf(1, 2, 3, 4, 5)
        )

        val monday0859 = LocalDateTime(2026, 11, 16, 8, 59).toInstant(kyivZone)
        val monday0900 = LocalDateTime(2026, 11, 16, 9, 0).toInstant(kyivZone)
        val monday1430 = LocalDateTime(2026, 11, 16, 14, 30).toInstant(kyivZone)
        val monday1800 = LocalDateTime(2026, 11, 16, 18, 0).toInstant(kyivZone)
        val sunday1200 = LocalDateTime(2026, 11, 15, 12, 0).toInstant(kyivZone)

        assertFalse(PointScheduleEvaluator.isMonitoringActive(point, monday0859, kyivZone))
        assertTrue(PointScheduleEvaluator.isMonitoringActive(point, monday0900, kyivZone))
        assertTrue(PointScheduleEvaluator.isMonitoringActive(point, monday1430, kyivZone))
        assertFalse(PointScheduleEvaluator.isMonitoringActive(point, monday1800, kyivZone))
        assertFalse(PointScheduleEvaluator.isMonitoringActive(point, sunday1200, kyivZone))
    }

    @Test
    fun overnightHomeScheduleRespectsPreviousDayAfterMidnight() {
        // 2026-11-15 is Sunday (7); 2026-11-16 is Monday (1)
        val point = basePoint(
            startHour = 18,
            startMinute = 0,
            endHour = 9,
            endMinute = 0,
            activeDaysIso = setOf(7)
        )

        val sunday1759 = LocalDateTime(2026, 11, 15, 17, 59).toInstant(kyivZone)
        val sunday2000 = LocalDateTime(2026, 11, 15, 20, 0).toInstant(kyivZone)
        val monday0200 = LocalDateTime(2026, 11, 16, 2, 0).toInstant(kyivZone)
        val monday0900 = LocalDateTime(2026, 11, 16, 9, 0).toInstant(kyivZone)
        val monday2000 = LocalDateTime(2026, 11, 16, 20, 0).toInstant(kyivZone)

        assertFalse(PointScheduleEvaluator.isMonitoringActive(point, sunday1759, kyivZone))
        assertTrue(PointScheduleEvaluator.isMonitoringActive(point, sunday2000, kyivZone))
        assertTrue(PointScheduleEvaluator.isMonitoringActive(point, monday0200, kyivZone))
        assertFalse(PointScheduleEvaluator.isMonitoringActive(point, monday0900, kyivZone))
        assertFalse(PointScheduleEvaluator.isMonitoringActive(point, monday2000, kyivZone))
    }

    @Test
    fun equalStartAndEndTimesMonitorFullDayOnSelectedDaysOnly() {
        val point = basePoint(
            startHour = 0,
            startMinute = 0,
            endHour = 0,
            endMinute = 0,
            activeDaysIso = setOf(1, 3)
        )

        val monday0000 = LocalDateTime(2026, 11, 16, 0, 0).toInstant(kyivZone)
        val tuesday1200 = LocalDateTime(2026, 11, 17, 12, 0).toInstant(kyivZone)
        val wednesday2359 = LocalDateTime(2026, 11, 18, 23, 59).toInstant(kyivZone)

        assertTrue(PointScheduleEvaluator.isMonitoringActive(point, monday0000, kyivZone))
        assertFalse(PointScheduleEvaluator.isMonitoringActive(point, tuesday1200, kyivZone))
        assertTrue(PointScheduleEvaluator.isMonitoringActive(point, wednesday2359, kyivZone))
    }
}
