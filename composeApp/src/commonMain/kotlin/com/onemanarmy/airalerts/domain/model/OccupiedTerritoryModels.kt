package com.onemanarmy.airalerts.domain.model

import kotlinx.serialization.Serializable

/**
 * Classification of temporarily occupied Ukrainian territories.
 */
@Serializable
enum class OccupiedZoneCategory {
    /** Occupied since 2014 (Crimea, CADR/CALR, Tuzla Island). */
    OCCUPIED_2014,
    /** Occupied since February 2022. */
    OCCUPIED_2022
}

/**
 * Compact polygon geometry for a temporarily occupied territory zone.
 * Each ring in [rings] stores packed `[lat0, lon0, lat1, lon1, ...]` Float pairs.
 */
@Serializable
class OccupiedTerritoryPolygon(
    val category: OccupiedZoneCategory,
    val minLat: Float,
    val maxLat: Float,
    val minLon: Float,
    val maxLon: Float,
    val rings: List<FloatArray>
)

/**
 * Cached snapshot of currently occupied Ukrainian territories.
 */
@Serializable
data class OccupiedTerritoriesSnapshot(
    val sourceHistoryId: Long = 0L,
    val sourceDatetime: String = "",
    val fetchedAtEpochMs: Long = 0L,
    val polygons: List<OccupiedTerritoryPolygon> = emptyList()
)
