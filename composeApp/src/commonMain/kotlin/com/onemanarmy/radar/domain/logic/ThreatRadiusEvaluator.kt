package com.onemanarmy.radar.domain.logic

import com.onemanarmy.radar.domain.model.AirThreat
import com.onemanarmy.radar.domain.model.AppSettings
import com.onemanarmy.radar.domain.model.PointAlertState
import com.onemanarmy.radar.domain.model.SavedPoint
import kotlin.math.abs

data class ThreatRadiusNotification(
    val point: SavedPoint,
    val threat: AirThreat,
    val distanceKm: Double
)

data class ThreatRadiusEvaluationResult(
    val notifications: List<ThreatRadiusNotification>,
    val updatedStates: Map<String, PointAlertState>
)

/**
 * Evaluates threat-in-radius entry notifications (0..100 km).
 *
 * Rules:
 * - Notifies ONLY on the transition from 0 threats inside radius -> >= 1 threat inside radius
 *   (i.e. only when the first threat enters the radius).
 * - Subsequent threats entering while at least one threat is already in the radius do NOT notify.
 * - Threats leaving the radius reset the state silently without sending a notification.
 */
object ThreatRadiusEvaluator {

    const val MAX_RADIUS_KM = 100
    private const val ZERO_KM_EFFECTIVE_THRESHOLD_KM = 0.5

    /**
     * Non-linear discrete radius stops (km): fine 1 km precision near the target (0..10 km),
     * coarser steps at medium/long range (12..100 km), placing 10 km at the exact midpoint.
     */
    val RADIUS_SLIDER_STOPS: List<Int> = listOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
        12, 15, 20, 25, 30, 40, 50, 60, 75, 100
    )

    fun nearestRadiusStopIndex(radiusKm: Int): Int {
        val clamped = radiusKm.coerceIn(0, MAX_RADIUS_KM)
        return RADIUS_SLIDER_STOPS.indices.minByOrNull { abs(RADIUS_SLIDER_STOPS[it] - clamped) } ?: 0
    }

    fun radiusKmForStopIndex(index: Int): Int {
        return RADIUS_SLIDER_STOPS[index.coerceIn(0, RADIUS_SLIDER_STOPS.lastIndex)]
    }

    fun effectiveRadiusKm(radiusKm: Int): Double {
        return if (radiusKm <= 0) {
            ZERO_KM_EFFECTIVE_THRESHOLD_KM
        } else {
            radiusKm.coerceAtMost(MAX_RADIUS_KM).toDouble()
        }
    }

    fun evaluate(
        trackedPoints: List<SavedPoint>,
        threats: List<AirThreat>,
        previousStates: Map<String, PointAlertState>,
        settings: AppSettings
    ): ThreatRadiusEvaluationResult {
        val updatedStates = previousStates.toMutableMap()
        val thresholdKm = effectiveRadiusKm(settings.threatNotificationRadiusKm)

        val candidates = mutableListOf<ThreatRadiusNotification>()

        for (point in trackedPoints) {
            val prev = updatedStates[point.id]
            if (!settings.threatRadiusNotificationEnabled || !point.notifyEnabled) {
                if (prev != null && prev.isThreatInRadius) {
                    updatedStates[point.id] = prev.copy(isThreatInRadius = false)
                }
                continue
            }

            var closestThreat: AirThreat? = null
            var minDistanceKm = Double.MAX_VALUE
            for (threat in threats) {
                val distKm = GeoUtils.haversineDistanceKm(
                    point.latitude,
                    point.longitude,
                    threat.latitude,
                    threat.longitude
                )
                if (distKm <= thresholdKm && distKm < minDistanceKm) {
                    minDistanceKm = distKm
                    closestThreat = threat
                }
            }

            val isNowInRadius = closestThreat != null
            val wasInRadius = prev?.isThreatInRadius ?: false

            val baseState = prev ?: PointAlertState(
                pointId = point.id,
                isAlertActive = false,
                alertSignature = "",
                lastTransitionEpochMs = 0L,
                isThreatInRadius = false
            )
            updatedStates[point.id] = baseState.copy(isThreatInRadius = isNowInRadius)

            if (!wasInRadius && isNowInRadius && closestThreat != null) {
                candidates += ThreatRadiusNotification(
                    point = point,
                    threat = closestThreat,
                    distanceKm = minDistanceKm
                )
            }
        }

        // If overlapping points (e.g. GPS + saved Home at same location) trigger on the same threat,
        // emit only one aggressive notification for the closest point.
        val deduplicated = candidates
            .groupBy { it.threat.id }
            .values
            .map { group -> group.minBy { it.distanceKm } }

        return ThreatRadiusEvaluationResult(
            notifications = deduplicated,
            updatedStates = updatedStates
        )
    }
}
