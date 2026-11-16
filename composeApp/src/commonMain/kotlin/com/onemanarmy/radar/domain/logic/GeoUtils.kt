package com.onemanarmy.radar.domain.logic

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure Kotlin geographic utilities for distance and bearing calculation.
 */
object GeoUtils {
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Computes great-circle distance in kilometers between two WGS84 coordinates using the Haversine formula.
     */
    fun haversineDistanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        if (lat1 == lat2 && lon1 == lon2) return 0.0

        val dLat = toRadians(lat2 - lat1)
        val dLon = toRadians(lon2 - lon1)
        val rLat1 = toRadians(lat1)
        val rLat2 = toRadians(lat2)

        val a = sin(dLat / 2).pow(2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Computes initial compass bearing in degrees [0, 360) from point 1 to point 2.
     */
    fun bearingDegrees(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val rLat1 = toRadians(lat1)
        val rLat2 = toRadians(lat2)
        val dLon = toRadians(lon2 - lon1)

        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        val deg = atan2(y, x) * 180.0 / PI
        return (deg + 360.0) % 360.0
    }

    private fun toRadians(degrees: Double): Double = degrees * PI / 180.0

    /**
     * Even-odd ray-casting point-in-polygon check over packed [lat, lon, ...] rings.
     * Naturally handles both multi-polygons (disjoint outer rings) and polygons with enclave holes.
     */
    fun isPointInRings(lat: Double, lon: Double, rings: List<FloatArray>): Boolean {
        var inside = false
        for (ring in rings) {
            val len = ring.size
            if (len < 6) continue
            var j = len - 2
            var i = 0
            while (i < len) {
                val yi = ring[i].toDouble()
                val xi = ring[i + 1].toDouble()
                val yj = ring[j].toDouble()
                val xj = ring[j + 1].toDouble()

                val intersects = ((yi > lat) != (yj > lat)) &&
                    (lon < (xj - xi) * (lat - yi) / ((yj - yi) + 1e-12) + xi)
                if (intersects) {
                    inside = !inside
                }
                j = i
                i += 2
            }
        }
        return inside
    }
}
