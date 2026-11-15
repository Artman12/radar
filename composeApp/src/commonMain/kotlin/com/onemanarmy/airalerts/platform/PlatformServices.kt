package com.onemanarmy.airalerts.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.onemanarmy.airalerts.domain.model.NotificationSoundPreset
import com.onemanarmy.airalerts.domain.model.VibrationPreset
import kotlinx.coroutines.flow.StateFlow

data class Coordinates(
    val latitude: Double,
    val longitude: Double
)

data class GeocodedAddress(
    val displayName: String,
    val locality: String?,
    val district: String?,
    val adminArea: String?,
    val latitude: Double,
    val longitude: Double
)

data class SystemDiagnosticsState(
    val isServiceRunning: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasFineLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasAnyLocationPermission: Boolean = false
)

/**
 * Platform notification & tactile feedback abstraction.
 */
interface PlatformNotifier {
    fun recreateAlertChannel(
        version: Int,
        preset: VibrationPreset,
        soundPreset: NotificationSoundPreset
    )

    fun showAlertNotification(
        notificationId: Int,
        title: String,
        body: String,
        silent: Boolean,
        softPulseOnly: Boolean,
        preset: VibrationPreset,
        channelVersion: Int
    )

    fun previewVibration(preset: VibrationPreset)

    fun sendTestNotification(
        title: String,
        body: String,
        preset: VibrationPreset,
        soundPreset: NotificationSoundPreset,
        channelVersion: Int
    )
}

/**
 * Platform GPS / Fused Location abstraction.
 */
interface PlatformLocationClient {
    suspend fun getCurrentCoordinates(): Coordinates?
}

/**
 * Platform geocoder abstraction with Nominatim fallback.
 */
interface PlatformGeocoder {
    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodedAddress?
    suspend fun searchAddress(query: String): List<GeocodedAddress>
}

/**
 * Controls the platform foreground monitoring service.
 */
interface BackgroundServiceController {
    val isServiceRunning: StateFlow<Boolean>
    fun startMonitoringService()
    fun stopMonitoringService()
}

/**
 * Inspects platform permissions/battery state and launches system settings or permission prompts.
 */
interface SystemDiagnosticsProvider {
    val diagnosticsState: StateFlow<SystemDiagnosticsState>
    fun refreshDiagnostics()
    fun requestNotificationPermission()
    fun requestLocationPermission(includeBackground: Boolean)
    fun requestIgnoreBatteryOptimizations()
    fun openAppNotificationSettings()
    fun openUrl(url: String)
}

/**
 * Platform DataStore factory abstraction.
 */
interface PlatformDataStoreFactory {
    fun createPreferencesDataStore(): DataStore<Preferences>
}
