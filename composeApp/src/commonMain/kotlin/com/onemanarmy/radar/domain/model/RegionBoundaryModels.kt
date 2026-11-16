package com.onemanarmy.radar.domain.model

/**
 * Administrative subdivision level inside Ukraine.
 */
enum class AdminLevel {
    OBLAST,
    RAION,
    HROMADA
}

/**
 * Real-time threat level computed for each administrative region by combining
 * official alerts (oblast, raion, or community) with live radar target proximity.
 */
enum class RegionThreatLevel {
    CLEAR,
    ELEVATED,
    HIGH,
    CRITICAL
}

/**
 * Geographic bounds of Ukraine (including Crimea and coastal waters).
 */
object UkraineBounds {
    const val MIN_LAT = 44.1846
    const val MAX_LAT = 52.3791
    const val MIN_LON = 22.1377
    const val MAX_LON = 40.2276

    // Slightly padded viewport limits so border areas can be centered comfortably.
    const val VIEWPORT_NORTH_LAT = 52.75
    const val VIEWPORT_SOUTH_LAT = 43.90
    const val VIEWPORT_WEST_LON = 21.60
    const val VIEWPORT_EAST_LON = 40.80

    const val CENTER_LAT = 48.38
    const val CENTER_LON = 31.17
    const val COUNTRY_ZOOM = 5.8
}

/**
 * Compact polygon geometry for an oblast, raion, or hromada.
 * Each ring in [rings] is stored as packed [lat0, lon0, lat1, lon1, ...] Float pairs
 * to minimize memory footprint and GC overhead during map rendering.
 */
class RegionPolygon(
    val id: String,
    val name: String,
    val raionName: String,
    val oblastName: String,
    val level: AdminLevel,
    val minLat: Float,
    val maxLat: Float,
    val minLon: Float,
    val maxLon: Float,
    val centerLat: Float,
    val centerLon: Float,
    val rings: List<FloatArray>
)

/**
 * Full offline vector atlas of Ukraine's national border and 3-tier administrative subdivisions.
 */
class UkraineMapBoundaries(
    val countryRings: List<FloatArray>,
    val oblasts: List<RegionPolygon>,
    val raions: List<RegionPolygon>,
    val hromadas: List<RegionPolygon>
)

/**
 * Detailed threat evaluation metadata for an individual region (used for map tap inspection).
 */
data class RegionThreatInfo(
    val threatLevel: RegionThreatLevel,
    val alertLevel: AlertLevel = AlertLevel.NONE,
    val hasActiveAlert: Boolean = false,
    val nearbyThreatCount: Int = 0,
    val isOblastWideAlert: Boolean = false,
    val isStandaloneAlert: Boolean = false
)

/**
 * Indexed threat levels aligned 1:1 with [UkraineMapBoundaries] lists for O(1) rendering lookup.
 */
data class RegionThreatSnapshot(
    val hromadaThreats: List<RegionThreatLevel>,
    val raionThreats: List<RegionThreatLevel>,
    val oblastThreats: List<RegionThreatLevel>,
    val hromadaInfos: List<RegionThreatInfo>,
    val raionInfos: List<RegionThreatInfo> = emptyList(),
    val oblastInfos: List<RegionThreatInfo> = emptyList(),
    val criticalHromadaCount: Int,
    val highHromadaCount: Int,
    val elevatedHromadaCount: Int
)
