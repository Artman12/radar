package com.onemanarmy.airalerts.domain.model

import kotlinx.serialization.Serializable

/**
 * Aerial threat categories parsed from NEPTUN OSINT radar feed.
 */
@Serializable
enum class ThreatType {
    UAV,
    FPV,
    RECON,
    CRUISE_MISSILE,
    BALLISTIC,
    KAB,
    AVIATION,
    UNKNOWN;

    companion object {
        fun fromApiValue(raw: String?): ThreatType = when (raw?.lowercase()?.trim()) {
            "uav", "shahed", "drone" -> UAV
            "fpv", "fpv_drone" -> FPV
            "recon", "recon_uav" -> RECON
            "missile", "cruise_missile", "cruise" -> CRUISE_MISSILE
            "ballistic", "ballistic_missile" -> BALLISTIC
            "kab", "guided_bomb" -> KAB
            "aviation", "aircraft", "jet" -> AVIATION
            else -> UNKNOWN
        }
    }
}

/**
 * Historical track point for an aerial threat trail.
 */
@Serializable
data class ThreatTrailPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampIso: String? = null
)

/**
 * Approximate aerial target telemetry from NEPTUN OSINT feed.
 */
@Serializable
data class AirThreat(
    val id: String,
    val type: ThreatType,
    val title: String,
    val region: String,
    val locality: String,
    val latitude: Double,
    val longitude: Double,
    val headingDeg: Double? = null,
    val speedKmh: Double? = null,
    val count: Int = 1,
    val uncertaintyKm: Int? = null,
    val positionQuality: String? = null,
    val explanation: String = "",
    val updatedAtIso: String = "",
    val trail: List<ThreatTrailPoint> = emptyList(),
    val presumptiveCourse: Boolean = false
)

/**
 * Aerial threat enriched with proximity calculation relative to the user's tracked points.
 */
data class ThreatWithProximity(
    val threat: AirThreat,
    val nearestPointId: String?,
    val nearestPointName: String?,
    val distanceKm: Double?,
    val isWithinWarningRadius: Boolean
)
