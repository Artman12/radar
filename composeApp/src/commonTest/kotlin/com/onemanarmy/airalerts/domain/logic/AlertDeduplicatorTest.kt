package com.onemanarmy.airalerts.domain.logic

import com.onemanarmy.airalerts.domain.model.ActiveAlertInfo
import com.onemanarmy.airalerts.domain.model.AlertLevel
import com.onemanarmy.airalerts.domain.model.AlertType
import com.onemanarmy.airalerts.domain.model.AppSettings
import com.onemanarmy.airalerts.domain.model.NightModeBehavior
import com.onemanarmy.airalerts.domain.model.PointAlertState
import com.onemanarmy.airalerts.domain.model.SavedPoint
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
}
