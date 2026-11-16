package com.onemanarmy.radar.data.repository

import com.onemanarmy.radar.data.remote.MapaUaRadarApi
import com.onemanarmy.radar.data.remote.NeptunRadarApi
import com.onemanarmy.radar.domain.logic.GeoUtils
import com.onemanarmy.radar.domain.model.AirThreat
import com.onemanarmy.radar.domain.model.SavedPoint
import com.onemanarmy.radar.domain.model.ThreatDataSource
import com.onemanarmy.radar.domain.model.ThreatWithProximity

/**
 * Fetches OSINT aerial threats from the selected radar source (NEPTUN or MAPA.UA)
 * and computes proximity against user's tracked points.
 */
class RadarRepository(
    private val neptunRadarApi: NeptunRadarApi,
    private val mapaUaRadarApi: MapaUaRadarApi
) {
    suspend fun fetchRawThreats(
        source: ThreatDataSource = ThreatDataSource.NEPTUN
    ): Result<List<AirThreat>> = when (source) {
        ThreatDataSource.NEPTUN -> neptunRadarApi.fetchThreats()
        ThreatDataSource.MAPA_UA -> mapaUaRadarApi.fetchThreats()
    }

    suspend fun fetchThreatsWithProximity(
        trackedPoints: List<SavedPoint>,
        warningRadiusKm: Double,
        source: ThreatDataSource = ThreatDataSource.NEPTUN
    ): Result<List<ThreatWithProximity>> {
        return fetchRawThreats(source).map { threats ->
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
