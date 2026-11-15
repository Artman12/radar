package com.onemanarmy.airalerts.domain.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoUtilsTest {

    @Test
    fun sameCoordinatesYieldZeroDistance() {
        val dist = GeoUtils.haversineDistanceKm(50.4501, 30.5234, 50.4501, 30.5234)
        assertEquals(0.0, dist, 1e-6)
    }

    @Test
    fun kyivToKharkivDistanceIsAccurate() {
        // Kyiv (50.4501, 30.5234) -> Kharkiv (49.9935, 36.2304) ~ 410 km great-circle
        val dist = GeoUtils.haversineDistanceKm(50.4501, 30.5234, 49.9935, 36.2304)
        assertTrue(dist in 405.0..418.0, "Expected ~410 km, got $dist")
    }

    @Test
    fun bearingNorthIsZeroDegrees() {
        val bearing = GeoUtils.bearingDegrees(48.0, 35.0, 49.0, 35.0)
        assertEquals(0.0, bearing, 0.5)
    }

    @Test
    fun bearingEastIsAroundNinetyDegrees() {
        val bearing = GeoUtils.bearingDegrees(48.0, 34.0, 48.0, 35.0)
        assertTrue(bearing in 88.0..92.0, "Expected ~90 deg, got $bearing")
    }
}
