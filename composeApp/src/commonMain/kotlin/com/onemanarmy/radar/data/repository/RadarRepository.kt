package com.onemanarmy.radar.data.repository

import com.onemanarmy.radar.data.remote.NeptunRadarApi
import com.onemanarmy.radar.domain.logic.GeoUtils
import com.onemanarmy.radar.domain.model.AirThreat
import com.onemanarmy.radar.domain.model.SavedPoint
import com.onemanarmy.radar.domain.model.ThreatWithProximity

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
