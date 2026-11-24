package com.onemanarmy.radar.domain.logic

import com.onemanarmy.radar.domain.model.ActiveAlertInfo
import com.onemanarmy.radar.domain.model.AlertLevel
import com.onemanarmy.radar.domain.model.AlertType
import com.onemanarmy.radar.domain.model.AppSettings
import com.onemanarmy.radar.domain.model.NightModeBehavior
import com.onemanarmy.radar.domain.model.PointAlertState
import com.onemanarmy.radar.domain.model.SavedPoint
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AlertDeduplicatorTest {

    private val homePoint = SavedPoint(
        id = "home",
        name = "Home",
        latitude = 48.5,
        longitude = 34.6,
        regionId = "9",
        regionName = "Dnipropetrovska oblast",
        notifyEnabled = true
    )

    private val airAlert = ActiveAlertInfo(
        regionId = "9",
        regionName = "Dnipropetrovska oblast",
        type = AlertType.AIR,
        level = AlertLevel.RED,
        startedAtIso = "2026-11-15T18:00:00Z"
    )

    @Test
    fun inactiveToActiveEmitsAlertStarted() {
        val result = AlertDeduplicator.evaluateTransition(
            point = homePoint,
            activeAlerts = listOf(airAlert),
            previousState = PointAlertState("home", false, "", 1000L),
            settings = AppSettings(),
            isQuietHours = false,
            nowEpochMs = 2000L
        )
        val decision = assertIs<NotificationDecision.AlertStarted>(result.decision)
        assertEquals(AlertType.AIR, decision.primaryType)
        assertTrue(result.updatedState.isAlertActive)
    }

    @Test
    fun repeatedSameAlertIsDeduplicated() {
        val first = AlertDeduplicator.evaluateTransition(
            point = homePoint,
            activeAlerts = listOf(airAlert),
            previousState = null,
            settings = AppSettings(),
            isQuietHours = false,
            nowEpochMs = 1000L
        )
        assertIs<NotificationDecision.AlertStarted>(first.decision)

        val second = AlertDeduplicator.evaluateTransition(
            point = homePoint,
            activeAlerts = listOf(airAlert),
            previousState = first.updatedState,
            settings = AppSettings(),
            isQuietHours = false,
            nowEpochMs = 2000L
        )
        assertEquals(NotificationDecision.None, second.decision)
    }

    @Test
    fun activeToInactiveEmitsAllClearNormally() {
        val first = AlertDeduplicator.evaluateTransition(
            point = homePoint,
            activeAlerts = listOf(airAlert),
            previousState = null,
            settings = AppSettings(),
            isQuietHours = false,
            nowEpochMs = 1000L
        )
        val clear = AlertDeduplicator.evaluateTransition(
            point = homePoint,
            activeAlerts = emptyList(),
            previousState = first.updatedState,
            settings = AppSettings(),
            isQuietHours = false,
            nowEpochMs = 3000L
        )
        assertIs<NotificationDecision.AllClear>(clear.decision)
        assertFalse(clear.updatedState.isAlertActive)
    }

    @Test
    fun filterAllClearSuppressesClearDuringQuietHours() {
        val settings = AppSettings(nightModeBehavior = NightModeBehavior.FILTER_ALL_CLEAR)
        val prev = PointAlertState("home", true, "9:AIR:2026-11-15T18:00:00Z", 1000L)

        val clear = AlertDeduplicator.evaluateTransition(
            point = homePoint,
            activeAlerts = emptyList(),
            previousState = prev,
            settings = settings,
            isQuietHours = true,
            nowEpochMs = 4000L
        )
        assertEquals(NotificationDecision.None, clear.decision)
        assertFalse(clear.updatedState.isAlertActive)
    }

    @Test
    fun fullSilenceSetsSilentFlagDuringQuietHours() {
        val settings = AppSettings(nightModeBehavior = NightModeBehavior.FULL_SILENCE)
        val result = AlertDeduplicator.evaluateTransition(
            point = homePoint,
            activeAlerts = listOf(airAlert),
            previousState = null,
            settings = settings,
            isQuietHours = true,
            nowEpochMs = 1000L
        )
        val decision = assertIs<NotificationDecision.AlertStarted>(result.decision)
        assertTrue(decision.silent)
        assertFalse(decision.softPulseOnly)
    }

    @Test
    fun normalNightBehaviorIgnoresQuietHoursForHromadaAlerts() {
        val settings = AppSettings(nightModeBehavior = NightModeBehavior.NORMAL)
        val started = AlertDeduplicator.evaluateTransition(
            point = homePoint,
            activeAlerts = listOf(airAlert),
            previousState = null,
            settings = settings,
            isQuietHours = true,
            nowEpochMs = 1000L
        )
        val startDecision = assertIs<NotificationDecision.AlertStarted>(started.decision)
        assertFalse(startDecision.silent)
        assertFalse(startDecision.softPulseOnly)

        val cleared = AlertDeduplicator.evaluateTransition(
            point = homePoint,
            activeAlerts = emptyList(),
            previousState = started.updatedState,
            settings = settings,
            isQuietHours = true,
            nowEpochMs = 2000L
        )
        val clearDecision = assertIs<NotificationDecision.AllClear>(cleared.decision)
        assertFalse(clearDecision.silent)
        assertFalse(clearDecision.softPulseOnly)
    }

    @Test
    fun disabledNightBehaviorSuppressesHromadaNotificationsDuringQuietHours() {
        val settings = AppSettings(nightModeBehavior = NightModeBehavior.DISABLED)
        val startedAtNight = AlertDeduplicator.evaluateTransition(
            point = homePoint,
            activeAlerts = listOf(airAlert),
            previousState = null,
            settings = settings,
            isQuietHours = true,
            nowEpochMs = 1000L
        )
        assertEquals(NotificationDecision.None, startedAtNight.decision)
        assertTrue(startedAtNight.updatedState.isAlertActive)

        val clearedAtNight = AlertDeduplicator.evaluateTransition(
            point = homePoint,
            activeAlerts = emptyList(),
            previousState = startedAtNight.updatedState,
            settings = settings,
            isQuietHours = true,
            nowEpochMs = 2000L
        )
        assertEquals(NotificationDecision.None, clearedAtNight.decision)
        assertFalse(clearedAtNight.updatedState.isAlertActive)

        val startedDuringDay = AlertDeduplicator.evaluateTransition(
            point = homePoint,
            activeAlerts = listOf(airAlert),
            previousState = clearedAtNight.updatedState,
            settings = settings,
            isQuietHours = false,
            nowEpochMs = 3000L
        )
        assertIs<NotificationDecision.AlertStarted>(startedDuringDay.decision)
    }

    @Test
    fun mutedPointNeverEmitsNotifications() {
        val mutedPoint = homePoint.copy(notifyEnabled = false)
        val result = AlertDeduplicator.evaluateTransition(
            point = mutedPoint,
            activeAlerts = listOf(airAlert),
            previousState = null,
            settings = AppSettings(),
            isQuietHours = false,
            nowEpochMs = 1000L
        )
        assertEquals(NotificationDecision.None, result.decision)
        assertTrue(result.updatedState.isAlertActive)
    }

    @Test
    fun hromadaNotificationsDisabledSuppressesDecisions() {
        val settings = AppSettings(hromadaAlertNotificationsEnabled = false)
        val result = AlertDeduplicator.evaluateTransition(
            point = homePoint,
            activeAlerts = listOf(airAlert),
            previousState = null,
            settings = settings,
            isQuietHours = false,
            nowEpochMs = 1000L
        )
        assertEquals(NotificationDecision.None, result.decision)
        assertTrue(result.updatedState.isAlertActive)
    }

    @Test
    fun raionToOblastUpgradeForSameAlertTypeAndLevelDoesNotDuplicateNotification() {
        val hromadaPoint = homePoint.copy(hromadaName = "Кам'янська")
        val raionAlert = ActiveAlertInfo(
            regionId = "neptun_raion:кам'янський",
            regionName = "Кам'янський район",
            type = AlertType.AIR,
            level = AlertLevel.RED,
            startedAtIso = "2026-11-15T18:00:00Z"
        )
        val first = AlertDeduplicator.evaluateTransition(
            point = hromadaPoint,
            activeAlerts = listOf(raionAlert),
            previousState = null,
            settings = AppSettings(),
            isQuietHours = false,
            nowEpochMs = 1000L
        )
        val started = assertIs<NotificationDecision.AlertStarted>(first.decision)
        assertEquals("Кам'янська", started.regionName)
        assertEquals(AlertLevel.RED, started.alertLevel)

        val upgraded = AlertDeduplicator.evaluateTransition(
            point = hromadaPoint,
            activeAlerts = listOf(airAlert),
            previousState = first.updatedState,
            settings = AppSettings(),
            isQuietHours = false,
            nowEpochMs = 2000L
        )
        assertEquals(NotificationDecision.None, upgraded.decision)

        val cleared = AlertDeduplicator.evaluateTransition(
            point = hromadaPoint,
            activeAlerts = emptyList(),
            previousState = upgraded.updatedState,
            settings = AppSettings(),
            isQuietHours = false,
            nowEpochMs = 3000L
        )
        val allClear = assertIs<NotificationDecision.AllClear>(cleared.decision)
        assertEquals("Кам'янська", allClear.regionName)
    }

    @Test
    fun inactiveToYellowActiveEmitsYellowAlertStarted() {
        val yellowAlert = ActiveAlertInfo(
            regionId = "neptun_raion:кам'янський",
            regionName = "Кам'янський район",
            type = AlertType.AIR,
            level = AlertLevel.YELLOW,
            startedAtIso = "2026-11-15T18:00:00Z"
        )
        val result = AlertDeduplicator.evaluateTransition(
            point = homePoint,
            activeAlerts = listOf(yellowAlert),
            previousState = null,
            settings = AppSettings(),
            isQuietHours = false,
            nowEpochMs = 1000L
        )
        val decision = assertIs<NotificationDecision.AlertStarted>(result.decision)
        assertEquals(AlertLevel.YELLOW, decision.alertLevel)
        assertEquals(AlertLevel.YELLOW, result.updatedState.highestLevel)
    }

    @Test
    fun yellowToRedEscalationEmitsRedAlertNotification() {
        val yellowAlert = ActiveAlertInfo(
            regionId = "neptun_raion:кам'янський",
            regionName = "Кам'янський район",
            type = AlertType.AIR,
            level = AlertLevel.YELLOW,
            startedAtIso = "2026-11-15T18:00:00Z"
        )
        val first = AlertDeduplicator.evaluateTransition(
            point = homePoint,
            activeAlerts = listOf(yellowAlert),
            previousState = null,
            settings = AppSettings(),
            isQuietHours = false,
            nowEpochMs = 1000L
        )
        val firstDecision = assertIs<NotificationDecision.AlertStarted>(first.decision)
        assertEquals(AlertLevel.YELLOW, firstDecision.alertLevel)

        val escalated = AlertDeduplicator.evaluateTransition(
            point = homePoint,
            activeAlerts = listOf(airAlert),
            previousState = first.updatedState,
            settings = AppSettings(),
            isQuietHours = false,
            nowEpochMs = 2000L
        )
        val redDecision = assertIs<NotificationDecision.AlertStarted>(escalated.decision)
        assertEquals(AlertLevel.RED, redDecision.alertLevel)
        assertEquals(AlertLevel.RED, escalated.updatedState.highestLevel)
    }

    @Test
    fun outsidePointScheduleSuppressesNotificationsButTracksState() {
        val kyivZone = TimeZone.of("Europe/Kyiv")
        // 2026-11-16 is Monday (ISO day 1)
        val officePoint = homePoint.copy(
            id = "office",
            name = "Office",
            scheduleEnabled = true,
            scheduleStartHour = 9,
            scheduleStartMinute = 0,
            scheduleEndHour = 18,
            scheduleEndMinute = 0,
            scheduleActiveDaysIso = setOf(1, 2, 3, 4, 5)
        )

        val outsideEpochMs = LocalDateTime(2026, 11, 16, 20, 0).toInstant(kyivZone).toEpochMilliseconds()
        val insideEpochMs = LocalDateTime(2026, 11, 16, 12, 0).toInstant(kyivZone).toEpochMilliseconds()

        val startedOutside = AlertDeduplicator.evaluateTransition(
            point = officePoint,
            activeAlerts = listOf(airAlert),
            previousState = null,
            settings = AppSettings(),
            isQuietHours = false,
            nowEpochMs = outsideEpochMs,
            timeZone = kyivZone
        )
        assertEquals(NotificationDecision.None, startedOutside.decision)
        assertTrue(startedOutside.updatedState.isAlertActive)

        val clearedOutside = AlertDeduplicator.evaluateTransition(
            point = officePoint,
            activeAlerts = emptyList(),
            previousState = startedOutside.updatedState,
            settings = AppSettings(),
            isQuietHours = false,
            nowEpochMs = outsideEpochMs + 60_000L,
            timeZone = kyivZone
        )
        assertEquals(NotificationDecision.None, clearedOutside.decision)
        assertFalse(clearedOutside.updatedState.isAlertActive)

        val startedInside = AlertDeduplicator.evaluateTransition(
            point = officePoint,
            activeAlerts = listOf(airAlert),
            previousState = clearedOutside.updatedState,
            settings = AppSettings(),
            isQuietHours = false,
            nowEpochMs = insideEpochMs,
            timeZone = kyivZone
        )
        assertIs<NotificationDecision.AlertStarted>(startedInside.decision)
    }
}
