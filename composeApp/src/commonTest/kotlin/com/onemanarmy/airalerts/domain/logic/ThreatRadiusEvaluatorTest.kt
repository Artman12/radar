package com.onemanarmy.airalerts.domain.logic

import com.onemanarmy.airalerts.domain.model.AirThreat
import com.onemanarmy.airalerts.domain.model.AppSettings
import com.onemanarmy.airalerts.domain.model.PointAlertState
import com.onemanarmy.airalerts.domain.model.SavedPoint
import com.onemanarmy.airalerts.domain.model.ThreatType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreatRadiusEvaluatorTest {

    private val kyivHome = SavedPoint(
        id = "home",
        name = "Home",
        latitude = 50.4501,
        longitude = 30.5234,
        regionId = "31",
        regionName = "м. Київ",
        notifyEnabled = true
    )

    private fun threatAt(id: String, lat: Double, lon: Double): AirThreat = AirThreat(
        id = id,
        type = ThreatType.UAV,
        title = "Shahed",
        region = "м. Київ",
        locality = "Київ",
        latitude = lat,
        longitude = lon
    )

    @Test
    fun notifiesOnlyWhenFirstThreatEntersRadius_subsequentThreatsSilent_leavingSilent() {
        val settings = AppSettings(
            threatRadiusNotificationEnabled = true,
            threatNotificationRadiusKm = 5
        )
        var states = emptyMap<String, PointAlertState>()

        // 1. Threat outside 5 km (~11 km north)
        val farThreat = threatAt("t1", 50.5501, 30.5234)
        val poll1 = ThreatRadiusEvaluator.evaluate(listOf(kyivHome), listOf(farThreat), states, settings)
        assertTrue(poll1.notifications.isEmpty())
        states = poll1.updatedStates
        assertFalse(states["home"]?.isThreatInRadius ?: false)

        // 2. First threat enters 5 km (~3.3 km north) -> must notify
        val nearThreat1 = threatAt("t1", 50.4801, 30.5234)
        val poll2 = ThreatRadiusEvaluator.evaluate(listOf(kyivHome), listOf(nearThreat1), states, settings)
        assertEquals(1, poll2.notifications.size)
        assertEquals("t1", poll2.notifications.first().threat.id)
        states = poll2.updatedStates
        assertTrue(states["home"]?.isThreatInRadius == true)

        // 3. Subsequent second threat enters while t1 is already inside -> must NOT notify
        val nearThreat2 = threatAt("t2", 50.4601, 30.5234)
        val poll3 = ThreatRadiusEvaluator.evaluate(
            listOf(kyivHome),
            listOf(nearThreat1, nearThreat2),
            states,
            settings
        )
        assertTrue(poll3.notifications.isEmpty())
        states = poll3.updatedStates
        assertTrue(states["home"]?.isThreatInRadius == true)

        // 4. First threat leaves, second threat still inside -> must NOT notify
        val poll4 = ThreatRadiusEvaluator.evaluate(listOf(kyivHome), listOf(nearThreat2), states, settings)
        assertTrue(poll4.notifications.isEmpty())
        states = poll4.updatedStates
        assertTrue(states["home"]?.isThreatInRadius == true)

        // 5. All threats leave radius -> must NOT notify, resets state
        val poll5 = ThreatRadiusEvaluator.evaluate(listOf(kyivHome), emptyList(), states, settings)
        assertTrue(poll5.notifications.isEmpty())
        states = poll5.updatedStates
        assertFalse(states["home"]?.isThreatInRadius ?: true)

        // 6. New threat enters after radius became empty -> must notify again
        val nearThreat3 = threatAt("t3", 50.4701, 30.5234)
        val poll6 = ThreatRadiusEvaluator.evaluate(listOf(kyivHome), listOf(nearThreat3), states, settings)
        assertEquals(1, poll6.notifications.size)
        assertEquals("t3", poll6.notifications.first().threat.id)
    }

    @Test
    fun zeroKmRadiusTriggersWithin500MetersOnly() {
        val settings = AppSettings(
            threatRadiusNotificationEnabled = true,
            threatNotificationRadiusKm = 0
        )

        // ~1.1 km away -> outside 0 km (<500 m) threshold
        val oneKmThreat = threatAt("t_1km", 50.4601, 30.5234)
        val res1 = ThreatRadiusEvaluator.evaluate(listOf(kyivHome), listOf(oneKmThreat), emptyMap(), settings)
        assertTrue(res1.notifications.isEmpty())

        // ~220 m away -> inside 0 km (<500 m) threshold
        val overheadThreat = threatAt("t_220m", 50.4521, 30.5234)
        val res2 = ThreatRadiusEvaluator.evaluate(listOf(kyivHome), listOf(overheadThreat), res1.updatedStates, settings)
        assertEquals(1, res2.notifications.size)
    }

    @Test
    fun overlappingPointsSharingSameThreatEmitSingleNotification() {
        val gpsPoint = kyivHome.copy(id = "gps_current_location", name = "My Location", isGpsAnchor = true)
        val settings = AppSettings(
            threatRadiusNotificationEnabled = true,
            threatNotificationRadiusKm = 5
        )
        val threat = threatAt("t_overlap", 50.4701, 30.5234)

        val result = ThreatRadiusEvaluator.evaluate(listOf(gpsPoint, kyivHome), listOf(threat), emptyMap(), settings)
        assertEquals(1, result.notifications.size)
        assertTrue(result.updatedStates["gps_current_location"]?.isThreatInRadius == true)
        assertTrue(result.updatedStates["home"]?.isThreatInRadius == true)
    }

    @Test
    fun disabledSettingEmitsNothingAndClearsInRadiusFlag() {
        val prev = mapOf("home" to PointAlertState("home", false, "", 0L, isThreatInRadius = true))
        val settings = AppSettings(
            threatRadiusNotificationEnabled = false,
            threatNotificationRadiusKm = 10
        )
        val threat = threatAt("t_near", 50.4601, 30.5234)

        val result = ThreatRadiusEvaluator.evaluate(listOf(kyivHome), listOf(threat), prev, settings)
        assertTrue(result.notifications.isEmpty())
        assertFalse(result.updatedStates["home"]?.isThreatInRadius ?: true)
    }
}
