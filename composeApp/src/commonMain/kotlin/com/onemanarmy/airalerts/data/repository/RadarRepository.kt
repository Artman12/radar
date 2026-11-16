package com.onemanarmy.airalerts.data.repository

import com.onemanarmy.airalerts.data.remote.NeptunRadarApi
import com.onemanarmy.airalerts.domain.logic.GeoUtils
import com.onemanarmy.airalerts.domain.model.AirThreat
import com.onemanarmy.airalerts.domain.model.SavedPoint
import com.onemanarmy.airalerts.domain.model.ThreatWithProximity

/**
 * Fetches NEPTUN OSINT threats and computes proximity against user's tracked points.
 */
class RadarRepository(
    private val neptunRadarApi: NeptunRadarApi
) {
    suspend fun fetchRawThreats(): Result<List<AirThreat>> = neptunRadarApi.fetchThreats()

    suspend fun fetchThreatsWithProximity(
        trackedPoints: List<SavedPoint>,
        warningRadiusKm: Double
    ): Result<List<ThreatWithProximity>> {
        return neptunRadarApi.fetchThreats().map { threats ->
            enrichProximity(threats, trackedPoints, warningRadiusKm)
        }
    }

    fun enrichProximity(
        threats: List<AirThreat>,
        trackedPoints: List<SavedPoint>,
        warningRadiusKm: Double
    ): List<ThreatWithProximity> {
        if (threats.isEmpty()) return emptyList()
        return threats.map { threat ->
            var nearestPoint: SavedPoint? = null
            var minDistance = Double.MAX_VALUE
            for (point in trackedPoints) {
                val distance = GeoUtils.haversineDistanceKm(
                    point.latitude,
                    point.longitude,
                    threat.latitude,
                    threat.longitude
                )
                if (distance < minDistance) {
                    minDistance = distance
                    nearestPoint = point
                }
            }

            val resolvedDistance = if (nearestPoint != null) minDistance else null
            ThreatWithProximity(
                threat = threat,
                nearestPointId = nearestPoint?.id,
                nearestPointName = nearestPoint?.name,
                distanceKm = resolvedDistance,
                isWithinWarningRadius = resolvedDistance != null && resolvedDistance <= warningRadiusKm
            )
        }
    }
}
