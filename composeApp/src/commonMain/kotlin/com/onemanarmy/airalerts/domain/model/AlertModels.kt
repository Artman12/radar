package com.onemanarmy.airalerts.domain.model

import kotlinx.serialization.Serializable

/**
 * Classification of official civil defense alerts returned by UkraineAlarm API v3.
 */
@Serializable
enum class AlertType {
    AIR,
    ARTILLERY,
    URBAN_FIGHTS,
    CHEMICAL,
    NUCLEAR,
    UNKNOWN;

    companion object {
        fun fromApiValue(raw: String?): AlertType = when (raw?.uppercase()) {
            "AIR" -> AIR
            "ARTILLERY" -> ARTILLERY
            "URBAN_FIGHTS" -> URBAN_FIGHTS
            "CHEMICAL" -> CHEMICAL
            "NUCLEAR" -> NUCLEAR
            else -> UNKNOWN
        }
    }
}

/**
 * Alert severity level from UkraineAlarm activeAlertLevels.
 */
@Serializable
enum class AlertLevel {
    RED,
    YELLOW,
    NONE;

    companion object {
        fun fromApiValue(raw: String?): AlertLevel = when (raw?.lowercase()) {
            "red" -> RED
            "yellow" -> YELLOW
            else -> RED
        }
    }
}

/**
 * Administrative hierarchy level in UkraineAlarm regions directory.
 */
@Serializable
enum class RegionType {
    STATE,
    DISTRICT,
    COMMUNITY;

    companion object {
        fun fromApiValue(raw: String?): RegionType = when (raw?.lowercase()) {
            "state" -> STATE
            "district" -> DISTRICT
            else -> COMMUNITY
        }
    }
}

/**
 * Cached region node from UkraineAlarm /api/v3/regions.
 */
@Serializable
data class RegionItem(
    val regionId: String,
    val name: String,
    val engName: String = "",
    val type: RegionType,
    val parentId: String? = null
)

/**
 * Represents a single active alert entry for a region, raion, or oblast.
 */
@Serializable
data class ActiveAlertInfo(
    val regionId: String,
    val regionName: String,
    val regionEngName: String = "",
    val type: AlertType,
    val level: AlertLevel = AlertLevel.RED,
    val startedAtIso: String? = null,
    val oblastName: String = "",
    val isOblastWide: Boolean = false
)

/**
 * User-tracked location point (either pinned manually or backed by GPS).
 */
@Serializable
data class SavedPoint(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val regionId: String,
    val regionName: String,
    val notifyEnabled: Boolean = true,
    val isGpsAnchor: Boolean = false,
    val districtName: String = "",
    val oblastName: String = ""
)

/**
 * Combined runtime status of a tracked point.
 */
data class PointStatus(
    val point: SavedPoint,
    val isAlertActive: Boolean,
    val highestLevel: AlertLevel,
    val activeAlerts: List<ActiveAlertInfo>,
    val earliestStartedAtIso: String?,
    val lastCheckedEpochMs: Long
)

/**
 * Persisted alert state for a point used by deduplication to prevent repeat notifications.
 */
@Serializable
data class PointAlertState(
    val pointId: String,
    val isAlertActive: Boolean,
    val alertSignature: String,
    val lastTransitionEpochMs: Long
)

/**
 * Resolved coordinate-to-region mapping result.
 */
data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
    val regionId: String,
    val regionName: String,
    val suggestedLabel: String,
    val districtName: String = "",
    val oblastName: String = ""
)
