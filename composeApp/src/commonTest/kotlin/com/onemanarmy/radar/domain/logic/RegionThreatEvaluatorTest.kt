package com.onemanarmy.radar.domain.logic

import com.onemanarmy.radar.domain.model.ActiveAlertInfo
import com.onemanarmy.radar.domain.model.AdminLevel
import com.onemanarmy.radar.domain.model.AirThreat
import com.onemanarmy.radar.domain.model.AlertLevel
import com.onemanarmy.radar.domain.model.AlertType
import com.onemanarmy.radar.domain.model.RegionPolygon
import com.onemanarmy.radar.domain.model.RegionThreatLevel
import com.onemanarmy.radar.domain.model.ThreatType
import com.onemanarmy.radar.domain.model.ThreatWithProximity
import com.onemanarmy.radar.domain.model.UkraineMapBoundaries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegionThreatEvaluatorTest {

    private val evaluator = RegionThreatEvaluator()

    private fun boxPolygon(
        id: String,
        name: String,
        raion: String,
        oblast: String,
        level: AdminLevel,
        minLat: Float,
        maxLat: Float,
        minLon: Float,
        maxLon: Float
    ): RegionPolygon {
        val ring = floatArrayOf(
            minLat, minLon,
            maxLat, minLon,
            maxLat, maxLon,
            minLat, maxLon,
            minLat, minLon
        )
        return RegionPolygon(
            id = id,
            name = name,
            raionName = raion,
            oblastName = oblast,
            level = level,
            minLat = minLat,
            maxLat = maxLat,
            minLon = minLon,
            maxLon = maxLon,
            centerLat = (minLat + maxLat) / 2f,
            centerLon = (minLon + maxLon) / 2f,
            rings = listOf(ring)
        )
    }

    private val sampleBoundaries = UkraineMapBoundaries(
        countryRings = emptyList(),
        oblasts = listOf(
            boxPolygon("o_0", "Київська область", "", "Київська область", AdminLevel.OBLAST, 49.5f, 51.0f, 29.5f, 32.0f),
            boxPolygon("o_1", "м. Київ", "", "м. Київ", AdminLevel.OBLAST, 50.3f, 50.6f, 30.3f, 30.7f),
            boxPolygon("o_2", "Житомирська область", "", "Житомирська область", AdminLevel.OBLAST, 49.8f, 51.5f, 27.5f, 29.5f)
        ),
        raions = listOf(
            boxPolygon("r_0", "Броварський район", "Броварський район", "Київська область", AdminLevel.RAION, 50.3f, 50.8f, 30.7f, 31.3f),
            boxPolygon("r_1", "Звягельський район", "Звягельський район", "Житомирська область", AdminLevel.RAION, 50.4f, 50.8f, 27.4f, 28.0f),
            boxPolygon("r_2", "Житомирський район", "Житомирський район", "Житомирська область", AdminLevel.RAION, 50.0f, 50.5f, 28.4f, 29.0f)
        ),
        hromadas = listOf(
            // 0: Brovary hromada (Kyivska oblast)
            boxPolygon("hromada_0", "Броварська", "Броварський район", "Київська область", AdminLevel.HROMADA, 50.45f, 50.60f, 30.75f, 30.95f),
            // 1: Baryshivka hromada (same raion, further east)
            boxPolygon("hromada_1", "Баришівська", "Броварський район", "Київська область", AdminLevel.HROMADA, 50.30f, 50.42f, 31.20f, 31.45f),
            // 2: Kyiv city (must NOT match Київська область alerts)
            boxPolygon("hromada_2", "Київ", "м. Київ", "м. Київ", AdminLevel.HROMADA, 50.35f, 50.55f, 30.35f, 30.65f),
            // 3: Zviahel hromada (Zhytomyrska oblast)
            boxPolygon("hromada_3", "Звягельська", "Звягельський район", "Житомирська область", AdminLevel.HROMADA, 50.50f, 50.65f, 27.55f, 27.75f),
            // 4: Zhytomyr hromada (Zhytomyrska oblast)
            boxPolygon("hromada_4", "Житомирська", "Житомирський район", "Житомирська область", AdminLevel.HROMADA, 50.20f, 50.35f, 28.60f, 28.80f)
        )
    )

    @Test
    fun evaluate_oblastAlertDoesNotBleedIntoKyivCityAndUpgradesTargetedHromadaToCritical() {
        val alerts = listOf(
            ActiveAlertInfo(
                regionId = "14",
                regionName = "Київська область",
                type = AlertType.AIR,
                level = AlertLevel.RED,
                oblastName = "Київська область",
                isOblastWide = true
            )
        )
        // Live UAV inside Brovary hromada (50.52, 30.82)
        val threats = listOf(
            ThreatWithProximity(
                threat = AirThreat(
                    id = "t1",
                    type = ThreatType.UAV,
                    title = "Shahed",
                    region = "Київська область",
                    locality = "Бровари",
                    latitude = 50.52,
                    longitude = 30.82
                ),
                nearestPointId = null,
                nearestPointName = null,
                distanceKm = null,
                isWithinWarningRadius = false
            )
        )

        val snapshot = evaluator.evaluate(sampleBoundaries, alerts, threats)

        assertEquals(RegionThreatLevel.CRITICAL, snapshot.hromadaThreats[0]) // Brovary: RED + radar inside
        assertEquals(RegionThreatLevel.HIGH, snapshot.hromadaThreats[1])     // Baryshivka: RED only
        assertEquals(RegionThreatLevel.CLEAR, snapshot.hromadaThreats[2])    // Kyiv city: unaffected by Kyivska oblast
        assertEquals(RegionThreatLevel.CLEAR, snapshot.hromadaThreats[3])
        assertEquals(RegionThreatLevel.CLEAR, snapshot.hromadaThreats[4])
        assertEquals(RegionThreatLevel.CRITICAL, snapshot.oblastThreats[0])  // Kyivska oblast: RED + radar
        assertEquals(RegionThreatLevel.CLEAR, snapshot.oblastThreats[1])     // Kyiv city: clear
        assertTrue(snapshot.oblastInfos[0].isOblastWideAlert)
        assertFalse(snapshot.raionInfos[0].isStandaloneAlert) // Covered by parent oblast-wide alert
    }

    @Test
    fun evaluate_raionAlertSupportsRenamedAliasesAndRadarDoesNotTurnClearRegionYellow() {
        val alerts = listOf(
            ActiveAlertInfo(
                regionId = "neptun_raion:новоград-волинський",
                regionName = "Новоград-Волинський район",
                type = AlertType.AIR,
                level = AlertLevel.YELLOW,
                oblastName = "Житомирська область",
                isOblastWide = false
            )
        )
        // Radar target directly inside Kyiv city while no official alert is active there.
        val threats = listOf(
            ThreatWithProximity(
                threat = AirThreat(
                    id = "t2",
                    type = ThreatType.UAV,
                    title = "Shahed",
                    region = "м. Київ",
                    locality = "Київ",
                    latitude = 50.45,
                    longitude = 30.50
                ),
                nearestPointId = null,
                nearestPointName = null,
                distanceKm = null,
                isWithinWarningRadius = false
            )
        )

        val snapshot = evaluator.evaluate(sampleBoundaries, alerts, threats)

        assertEquals(RegionThreatLevel.ELEVATED, snapshot.hromadaThreats[3]) // Zviahel matches Novohrad-Volynskyi
        assertEquals(RegionThreatLevel.CLEAR, snapshot.hromadaThreats[4])    // Zhytomyr raion stays clear
        assertEquals(RegionThreatLevel.CLEAR, snapshot.hromadaThreats[2])    // Kyiv stays clear (radar alone doesn't paint yellow)
        assertEquals(RegionThreatLevel.CLEAR, snapshot.oblastThreats[1])     // Kyiv city stays clear
        assertEquals(1, snapshot.hromadaInfos[2].nearbyThreatCount)          // Nearby target count is still tracked
        assertEquals(1, snapshot.oblastInfos[1].nearbyThreatCount)
        assertEquals(RegionThreatLevel.CLEAR, snapshot.oblastThreats[2])     // Zhytomyrska stays clear except Zviahel raion
        assertEquals(RegionThreatLevel.ELEVATED, snapshot.raionThreats[1])   // Zviahel raion is elevated
        assertTrue(snapshot.raionInfos[1].isStandaloneAlert)                 // Highlighted on zoomed-out map
        assertFalse(snapshot.hromadaInfos[3].isStandaloneAlert)              // Covered by raion highlight
    }

    @Test
    fun isPointInRings_respectsPolygonWithEnclaveHole() {
        val outer = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f, 0f, 0f)
        val innerHole = floatArrayOf(3f, 3f, 7f, 3f, 7f, 7f, 3f, 7f, 3f, 3f)
        val rings = listOf(outer, innerHole)

        assertTrue(GeoUtils.isPointInRings(1.5, 1.5, rings))
        assertFalse(GeoUtils.isPointInRings(5.0, 5.0, rings)) // Inside enclave hole -> outside polygon
        assertFalse(GeoUtils.isPointInRings(12.0, 5.0, rings))
    }
}
