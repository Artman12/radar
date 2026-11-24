package com.onemanarmy.radar.domain.logic

import com.onemanarmy.radar.domain.model.AirThreat
import com.onemanarmy.radar.domain.model.AppSettings
import com.onemanarmy.radar.domain.model.PointAlertState
import com.onemanarmy.radar.domain.model.SavedPoint
import com.onemanarmy.radar.domain.model.ThreatNightModeBehavior
import com.onemanarmy.radar.domain.model.ThreatSoundPattern
import com.onemanarmy.radar.domain.model.ThreatType
import com.onemanarmy.radar.domain.model.ThreatVibrationPattern
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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

    @Test
    fun supportsExtended100KmRadiusAndClampsAboveMax() {
        // ~90 km north of Kyiv center
        val threat90Km = threatAt("t_90km", 51.260, 30.5234)
        // ~112 km north of Kyiv center
        val threat112Km = threatAt("t_112km", 51.458, 30.5234)

        val at100Km = AppSettings(
            threatRadiusNotificationEnabled = true,
            threatNotificationRadiusKm = 100
        )
        val res100 = ThreatRadiusEvaluator.evaluate(listOf(kyivHome), listOf(threat90Km), emptyMap(), at100Km)
        assertEquals(1, res100.notifications.size)
        assertEquals("t_90km", res100.notifications.first().threat.id)

        // Values above MAX_RADIUS_KM (100) must clamp to 100 km
        val aboveMax = AppSettings(
            threatRadiusNotificationEnabled = true,
            threatNotificationRadiusKm = 150
        )
        val resClamped = ThreatRadiusEvaluator.evaluate(listOf(kyivHome), listOf(threat112Km), emptyMap(), aboveMax)
        assertTrue(resClamped.notifications.isEmpty())
    }

    @Test
    fun nonLinearRadiusStopsMapRoundTripsAndIntermediateValues() {
        assertEquals(0, ThreatRadiusEvaluator.radiusKmForStopIndex(0))
        assertEquals(10, ThreatRadiusEvaluator.radiusKmForStopIndex(10))
        assertEquals(100, ThreatRadiusEvaluator.radiusKmForStopIndex(99))

        assertEquals(0, ThreatRadiusEvaluator.nearestRadiusStopIndex(-5))
        assertEquals(1, ThreatRadiusEvaluator.nearestRadiusStopIndex(1))
        assertEquals(10, ThreatRadiusEvaluator.nearestRadiusStopIndex(10))
        // 17 is closer to 15 (diff 2) than 20 (diff 3)
        assertEquals(15, ThreatRadiusEvaluator.radiusKmForStopIndex(ThreatRadiusEvaluator.nearestRadiusStopIndex(17)))
        // 68 is closer to 75 (diff 7) than 60 (diff 8)
        assertEquals(75, ThreatRadiusEvaluator.radiusKmForStopIndex(ThreatRadiusEvaluator.nearestRadiusStopIndex(68)))
        assertEquals(20, ThreatRadiusEvaluator.nearestRadiusStopIndex(150))
    }

    @Test
    fun nightModeSoundAndVibrationUsesNightVolumeDuringQuietHours() {
        val settings = AppSettings(
            threatRadiusNotificationEnabled = true,
            threatNotificationRadiusKm = 10,
            threatVibrationPattern = ThreatVibrationPattern.RAPID_BURST,
            threatSoundPattern = ThreatSoundPattern.SIREN_PULSE,
            threatSoundVolumePercent = 90,
            threatNightModeBehavior = ThreatNightModeBehavior.SOUND_AND_VIBRATION,
            threatNightSoundVolumePercent = 25
        )
        val threat = threatAt("t_night_vol", 50.4701, 30.5234)

        val dayResult = ThreatRadiusEvaluator.evaluate(listOf(kyivHome), listOf(threat), emptyMap(), settings, isQuietHours = false)
        assertEquals(1, dayResult.notifications.size)
        assertEquals(90, dayResult.notifications.first().soundVolumePercent)
        assertEquals(ThreatSoundPattern.SIREN_PULSE, dayResult.notifications.first().soundPattern)
        assertEquals(ThreatVibrationPattern.RAPID_BURST, dayResult.notifications.first().vibrationPattern)

        val nightResult = ThreatRadiusEvaluator.evaluate(listOf(kyivHome), listOf(threat), emptyMap(), settings, isQuietHours = true)
        assertEquals(1, nightResult.notifications.size)
        assertEquals(25, nightResult.notifications.first().soundVolumePercent)
        assertEquals(ThreatSoundPattern.SIREN_PULSE, nightResult.notifications.first().soundPattern)
        assertEquals(ThreatVibrationPattern.RAPID_BURST, nightResult.notifications.first().vibrationPattern)
    }

    @Test
    fun nightModeVibrationOnlyMutesSoundAndFallsBackWhenDayVibroIsOff() {
        val threat = threatAt("t_vibro", 50.4701, 30.5234)
        val settingsWithPattern = AppSettings(
            threatRadiusNotificationEnabled = true,
            threatNotificationRadiusKm = 10,
            threatVibrationPattern = ThreatVibrationPattern.SOS_URGENT,
            threatSoundPattern = ThreatSoundPattern.ALARM_BEEP,
            threatNightModeBehavior = ThreatNightModeBehavior.VIBRATION_ONLY
        )
        val res1 = ThreatRadiusEvaluator.evaluate(listOf(kyivHome), listOf(threat), emptyMap(), settingsWithPattern, isQuietHours = true)
        assertEquals(1, res1.notifications.size)
        assertEquals(ThreatVibrationPattern.SOS_URGENT, res1.notifications.first().vibrationPattern)
        assertEquals(ThreatSoundPattern.OFF, res1.notifications.first().soundPattern)
        assertEquals(0, res1.notifications.first().soundVolumePercent)

        val settingsWithVibroOff = settingsWithPattern.copy(threatVibrationPattern = ThreatVibrationPattern.OFF)
        val res2 = ThreatRadiusEvaluator.evaluate(listOf(kyivHome), listOf(threat), emptyMap(), settingsWithVibroOff, isQuietHours = true)
        assertEquals(1, res2.notifications.size)
        assertEquals(ThreatVibrationPattern.QUAD_HARD, res2.notifications.first().vibrationPattern)
        assertEquals(ThreatSoundPattern.OFF, res2.notifications.first().soundPattern)
    }

    @Test
    fun nightModeSilentBannerDisablesSoundAndVibration() {
        val threat = threatAt("t_silent", 50.4701, 30.5234)
        val settings = AppSettings(
            threatRadiusNotificationEnabled = true,
            threatNotificationRadiusKm = 10,
            threatVibrationPattern = ThreatVibrationPattern.QUAD_HARD,
            threatSoundPattern = ThreatSoundPattern.BUZZER,
            threatNightModeBehavior = ThreatNightModeBehavior.SILENT_BANNER
        )
        val res = ThreatRadiusEvaluator.evaluate(listOf(kyivHome), listOf(threat), emptyMap(), settings, isQuietHours = true)
        assertEquals(1, res.notifications.size)
        assertEquals(ThreatVibrationPattern.OFF, res.notifications.first().vibrationPattern)
        assertEquals(ThreatSoundPattern.OFF, res.notifications.first().soundPattern)
        assertEquals(0, res.notifications.first().soundVolumePercent)
    }

    @Test
    fun nightModeDisabledSuppressesThreatNotificationsAndResetsState() {
        val threat = threatAt("t_disabled", 50.4701, 30.5234)
        val prev = mapOf("home" to PointAlertState("home", false, "", 0L, isThreatInRadius = true))
        val settings = AppSettings(
            threatRadiusNotificationEnabled = true,
            threatNotificationRadiusKm = 10,
            threatNightModeBehavior = ThreatNightModeBehavior.DISABLED
        )
        val res = ThreatRadiusEvaluator.evaluate(listOf(kyivHome), listOf(threat), prev, settings, isQuietHours = true)
        assertTrue(res.notifications.isEmpty())
        assertFalse(res.updatedStates["home"]?.isThreatInRadius ?: true)
    }

    @Test
    fun outsidePointScheduleSuppressesRadiusNotificationAndClearsState() {
        val kyivZone = TimeZone.of("Europe/Kyiv")
        val workPoint = kyivHome.copy(
            id = "work",
            name = "Work",
            scheduleEnabled = true,
            scheduleStartHour = 9,
            scheduleStartMinute = 0,
            scheduleEndHour = 18,
            scheduleEndMinute = 0,
            scheduleActiveDaysIso = setOf(1, 2, 3, 4, 5)
        )
        val settings = AppSettings(
            threatRadiusNotificationEnabled = true,
            threatNotificationRadiusKm = 10
        )
        val threat = threatAt("t_sched", 50.4701, 30.5234)
        val prev = mapOf("work" to PointAlertState("work", false, "", 0L, isThreatInRadius = true))

        val evening = LocalDateTime(2026, 11, 16, 20, 0).toInstant(kyivZone)
        val outsideResult = ThreatRadiusEvaluator.evaluate(
            trackedPoints = listOf(workPoint),
            threats = listOf(threat),
            previousStates = prev,
            settings = settings,
            instant = evening,
            timeZone = kyivZone
        )
        assertTrue(outsideResult.notifications.isEmpty())
        assertFalse(outsideResult.updatedStates["work"]?.isThreatInRadius ?: true)

        val noon = LocalDateTime(2026, 11, 16, 12, 0).toInstant(kyivZone)
        val insideResult = ThreatRadiusEvaluator.evaluate(
            trackedPoints = listOf(workPoint),
            threats = listOf(threat),
            previousStates = outsideResult.updatedStates,
            settings = settings,
            instant = noon,
            timeZone = kyivZone
        )
        assertEquals(1, insideResult.notifications.size)
        assertTrue(insideResult.updatedStates["work"]?.isThreatInRadius == true)
    }
}
