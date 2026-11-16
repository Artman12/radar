package com.onemanarmy.radar.domain.model

import kotlinx.serialization.Serializable

/**
 * Aerial threat categories parsed from NEPTUN OSINT radar feed.
 */
@Serializable
enum class ThreatType {
    UAV,
    JET_SHAHED,
    FPV,
    RECON,
    CRUISE_MISSILE,
    BALLISTIC,
    KAB,
    AVIATION,
    UNKNOWN;

    companion object {
        fun fromApiValue(raw: String?): ThreatType = when (raw?.lowercase()?.trim()) {
            "jet_shahed", "shahed_jet", "shahed-jet", "shahed238", "shahed_238", "shahed-238",
            "jet_uav", "uav_jet", "reactive_shahed", "reactive_uav", "geran3", "geran_3", "geran-3" -> JET_SHAHED
            "uav", "shahed", "shahed136", "shahed_136", "shahed-136", "shahed131", "drone" -> UAV
            "fpv", "fpv_drone" -> FPV
            "recon", "recon_uav" -> RECON
            "missile", "cruise_missile", "cruise" -> CRUISE_MISSILE
            "ballistic", "ballistic_missile" -> BALLISTIC
            "kab", "guided_bomb" -> KAB
            "aviation", "aircraft", "jet", "mig31k", "mig-31k" -> AVIATION
            else -> UNKNOWN
        }

        fun resolveFromApi(
            rawType: String?,
            subtype: String? = null,
            title: String? = null,
            explanation: String? = null,
            speedKmh: Double? = null
        ): ThreatType {
            val baseType = fromApiValue(rawType)
            val normalizedSubtype = subtype?.lowercase()?.trim()

            if (baseType == UAV) {
                val isJetSubtype = normalizedSubtype in JET_SHAHED_SUBTYPES
                if (isJetSubtype) return JET_SHAHED

                val combinedText = buildString {
                    if (!title.isNullOrBlank()) append(title.lowercase()).append(' ')
                    if (!explanation.isNullOrBlank()) append(explanation.lowercase())
                }
                val isJetShahedByText = combinedText.contains("реактив") ||
                    combinedText.contains("турбореакт") ||
                    combinedText.contains("238") ||
                    combinedText.contains("герань-3") ||
                    combinedText.contains("geran-3") ||
                    combinedText.contains("geran 3") ||
                    combinedText.contains("jet shahed") ||
                    combinedText.contains("jet uav") ||
                    combinedText.contains("turbojet")
                val isJetShahedBySpeed = speedKmh != null && speedKmh >= 260.0

                return if (isJetShahedByText || isJetShahedBySpeed) JET_SHAHED else UAV
            }

            return baseType
        }

        private val JET_SHAHED_SUBTYPES = setOf(
            "jet",
            "jet_shahed",
            "shahed_jet",
            "shahed-jet",
            "shahed238",
            "shahed_238",
            "shahed-238",
            "jet_uav",
            "uav_jet",
            "reactive",
            "reactive_shahed",
            "reactive_uav",
            "geran3",
            "geran_3",
            "geran-3"
        )
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
