package com.onemanarmy.airalerts.ui.components

import com.onemanarmy.airalerts.domain.model.AirThreat
import com.onemanarmy.airalerts.domain.model.ThreatType
import com.onemanarmy.airalerts.domain.model.ThreatWithProximity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActiveThreatsSummaryTest {

    private fun threat(
        id: String,
        type: ThreatType,
        count: Int = 1,
        distanceKm: Double? = null,
        isWithinWarningRadius: Boolean = false
    ) = ThreatWithProximity(
        threat = AirThreat(
            id = id,
            type = type,
            title = id,
            region = "Київська область",
            locality = "",
            latitude = 50.45,
            longitude = 30.52,
            count = count
        ),
        nearestPointId = if (distanceKm != null) "home" else null,
        nearestPointName = if (distanceKm != null) "Дім" else null,
        distanceKm = distanceKm,
        isWithinWarningRadius = isWithinWarningRadius
    )

    @Test
    fun summarizeThreatsByType_sumsCountsInSeverityOrderAndTracksNearbyWarning() {
        val items = listOf(
            threat("u1", ThreatType.UAV, count = 2, distanceKm = 80.0, isWithinWarningRadius = false),
            threat("u2", ThreatType.UAV, count = 3, distanceKm = 12.0, isWithinWarningRadius = true),
            threat("b1", ThreatType.BALLISTIC, count = 1, distanceKm = 150.0, isWithinWarningRadius = false),
            threat("c1", ThreatType.CRUISE_MISSILE, count = 4, distanceKm = 200.0, isWithinWarningRadius = false)
        )

        val summary = summarizeThreatsByType(items, highlightNearbyThreats = true)

        assertEquals(
            listOf(ThreatType.BALLISTIC, ThreatType.CRUISE_MISSILE, ThreatType.UAV),
            summary.map { it.type }
        )
        assertEquals(listOf(1, 4, 5), summary.map { it.count })
        assertFalse(summary[0].hasNearbyWarning)
        assertFalse(summary[1].hasNearbyWarning)
        assertTrue(summary[2].hasNearbyWarning)
    }

    @Test
    fun summarizeThreatsByType_respectsHighlightNearbyFlagWhenDisabled() {
        val items = listOf(
            threat("u1", ThreatType.UAV, count = 1, distanceKm = 5.0, isWithinWarningRadius = true)
        )

        val summary = summarizeThreatsByType(items, highlightNearbyThreats = false)

        assertEquals(1, summary.size)
        assertFalse(summary[0].hasNearbyWarning)
    }

    @Test
    fun sortThreatsForList_placesNearbyWarningsFirstThenClosestThenPriority() {
        val items = listOf(
            threat("far_uav", ThreatType.UAV, distanceKm = 200.0, isWithinWarningRadius = false),
            threat("near_fpv", ThreatType.FPV, distanceKm = 15.0, isWithinWarningRadius = true),
            threat("mid_ballistic", ThreatType.BALLISTIC, distanceKm = 50.0, isWithinWarningRadius = false),
            threat("no_dist_kab", ThreatType.KAB, distanceKm = null, isWithinWarningRadius = false)
        )

        val sorted = sortThreatsForList(items, highlightNearbyThreats = true)

        assertEquals(
            listOf("near_fpv", "mid_ballistic", "far_uav", "no_dist_kab"),
            sorted.map { it.threat.id }
        )
    }
}
