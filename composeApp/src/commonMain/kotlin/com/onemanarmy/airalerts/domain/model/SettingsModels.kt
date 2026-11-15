package com.onemanarmy.airalerts.domain.model

import kotlinx.serialization.Serializable

/**
 * Vibration patterns supported by the Android NotificationChannel & VibrationEffect engine.
 */
@Serializable
enum class VibrationPreset {
    NONE,
    ULTRA_SOFT,
    DELICATE_DOUBLE,
    STANDARD
}

/**
 * Notification sound preference applied when creating or recreating the alert channel.
 */
@Serializable
enum class NotificationSoundPreset {
    DEFAULT,
    SOFT_CHIME,
    SILENT
}

/**
 * Notification delivery rule when current local time falls inside Night Mode ("Quiet Hours").
 */
@Serializable
enum class NightModeBehavior {
    FULL_SILENCE,
    ULTRA_SOFT_PULSE_ONLY,
    FILTER_ALL_CLEAR
}

/**
 * Persistent application preferences and runtime metadata.
 */
@Serializable
data class AppSettings(
    val isOnboardingCompleted: Boolean = false,
    val gpsTrackingEnabled: Boolean = false,
    val gpsPointRegionId: String? = null,
    val gpsPointRegionName: String? = null,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val vibrationPreset: VibrationPreset = VibrationPreset.DELICATE_DOUBLE,
    val soundPreset: NotificationSoundPreset = NotificationSoundPreset.DEFAULT,
    val notificationChannelVersion: Int = 1,
    val nightModeEnabled: Boolean = true,
    val nightStartHour: Int = 23,
    val nightStartMinute: Int = 0,
    val nightEndHour: Int = 7,
    val nightEndMinute: Int = 0,
    val nightActiveDaysIso: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val nightModeBehavior: NightModeBehavior = NightModeBehavior.FILTER_ALL_CLEAR,
    val warningRadiusKm: Int = 30,
    val radarNearbyAlertsEnabled: Boolean = true,
    val radarLayerEnabled: Boolean = true,
    val lastActionIndex: Long = 0L,
    val lastKnownAlertCheckEpochMs: Long = 0L,
    val lastApiError: String? = null
)
