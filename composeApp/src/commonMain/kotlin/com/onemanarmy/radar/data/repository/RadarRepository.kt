package com.onemanarmy.radar.data.repository

import com.onemanarmy.radar.data.remote.MapaUaRadarApi
import com.onemanarmy.radar.data.remote.NeptunRadarApi
import com.onemanarmy.radar.domain.logic.GeoUtils
import com.onemanarmy.radar.domain.model.AirThreat
import com.onemanarmy.radar.domain.model.SavedPoint
import com.onemanarmy.radar.domain.model.ThreatDataSource
import com.onemanarmy.radar.domain.model.ThreatWithProximity

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Fetches OSINT aerial threats from the selected radar source (NEPTUN or MAPA.UA)
 * and computes proximity against user's tracked points.
 *
 * Enforces per-source request coalescing and a short-TTL response/failure cache so rapid UI
 * switching or overlapping foreground/background polls cannot spam upstream APIs.
 */
class RadarRepository(
    private val neptunRadarApi: NeptunRadarApi,
    private val mapaUaRadarApi: MapaUaRadarApi,
    private val minRequestIntervalMs: Long = DEFAULT_MIN_REQUEST_INTERVAL_MS,
    private val nowEpochMs: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    companion object {
        const val DEFAULT_MIN_REQUEST_INTERVAL_MS = 10_000L
    }

    private data class SourceState(
        val mutex: Mutex = Mutex(),
        var lastAttemptAtMs: Long = Long.MIN_VALUE,
        var lastResult: Result<List<AirThreat>>? = null,
        var lastSuccessAtMs: Long = Long.MIN_VALUE,
        var lastSuccessfulThreats: List<AirThreat>? = null
    )

    private val statesBySource = ThreatDataSource.entries.associateWith { SourceState() }

    suspend fun fetchRawThreats(
        source: ThreatDataSource = ThreatDataSource.NEPTUN
    ): Result<List<AirThreat>> {
        val state = statesBySource.getValue(source)
        return state.mutex.withLock {
            val now = nowEpochMs()
            val lastSuccess = state.lastSuccessfulThreats
            if (lastSuccess != null &&
                state.lastSuccessAtMs != Long.MIN_VALUE &&
                (now - state.lastSuccessAtMs) in 0L until minRequestIntervalMs
            ) {
                return@withLock Result.success(lastSuccess)
            }

            val lastResult = state.lastResult
            if (lastResult != null &&
                lastResult.isFailure &&
                state.lastAttemptAtMs != Long.MIN_VALUE &&
                (now - state.lastAttemptAtMs) in 0L until minRequestIntervalMs
            ) {
                return@withLock lastResult
            }

            val freshResult = when (source) {
                ThreatDataSource.NEPTUN -> neptunRadarApi.fetchThreats()
                ThreatDataSource.MAPA_UA -> mapaUaRadarApi.fetchThreats()
            }
            val completedAt = nowEpochMs()
            state.lastAttemptAtMs = completedAt
            state.lastResult = freshResult
            freshResult.onSuccess { threats ->
                state.lastSuccessAtMs = completedAt
                state.lastSuccessfulThreats = threats
            }
            freshResult
        }
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
