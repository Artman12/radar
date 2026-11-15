package com.onemanarmy.airalerts.data.repository

import com.onemanarmy.airalerts.data.local.AppPreferencesRepository
import com.onemanarmy.airalerts.data.remote.NeptunAlertsApi
import com.onemanarmy.airalerts.data.remote.UkraineAlarmApi
import com.onemanarmy.airalerts.domain.logic.AlertDeduplicator
import com.onemanarmy.airalerts.domain.logic.NotificationDecision
import com.onemanarmy.airalerts.domain.logic.QuietHoursEvaluator
import com.onemanarmy.airalerts.domain.logic.RegionMatcher
import com.onemanarmy.airalerts.domain.model.ActiveAlertInfo
import com.onemanarmy.airalerts.domain.model.AlertLevel
import com.onemanarmy.airalerts.domain.model.AlertType
import com.onemanarmy.airalerts.domain.model.AppSettings
import com.onemanarmy.airalerts.domain.model.PointStatus
import com.onemanarmy.airalerts.domain.model.ResolvedLocation
import com.onemanarmy.airalerts.domain.model.SavedPoint
import com.onemanarmy.airalerts.platform.GeocodedAddress
import com.onemanarmy.airalerts.platform.PlatformGeocoder
import com.onemanarmy.airalerts.platform.PlatformLocationClient
import com.onemanarmy.airalerts.platform.PlatformNotifier
import com.onemanarmy.airalerts.resources.Res
import com.onemanarmy.airalerts.resources.alert_type_air
import com.onemanarmy.airalerts.resources.alert_type_artillery
import com.onemanarmy.airalerts.resources.alert_type_chemical
import com.onemanarmy.airalerts.resources.alert_type_nuclear
import com.onemanarmy.airalerts.resources.alert_type_unknown
import com.onemanarmy.airalerts.resources.alert_type_urban_fights
import com.onemanarmy.airalerts.resources.notif_alert_started_body
import com.onemanarmy.airalerts.resources.notif_alert_started_title
import com.onemanarmy.airalerts.resources.notif_all_clear_body
import com.onemanarmy.airalerts.resources.notif_all_clear_title
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.getString

data class PollResult(
    val success: Boolean,
    val decisions: List<NotificationDecision> = emptyList(),
    val trackedStatuses: List<PointStatus> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Coordinates alert polling (UkraineAlarm API v3 when key is configured, or NEPTUN keyless alerts API),
 * GPS/location region mapping, deduplication, and notifications.
 */
class AlertMonitoringRepository(
    private val ukraineAlarmApi: UkraineAlarmApi,
    private val neptunAlertsApi: NeptunAlertsApi,
    private val prefsRepo: AppPreferencesRepository,
    private val locationClient: PlatformLocationClient,
    private val geocoder: PlatformGeocoder,
    private val notifier: PlatformNotifier
) {
    companion object {
        const val GPS_POINT_ID = "gps_current_location"
    }

    val settingsFlow: Flow<AppSettings> = prefsRepo.settingsFlow
    val savedPointsFlow: Flow<List<SavedPoint>> = prefsRepo.savedPointsFlow
    val activeAlertsFlow: Flow<List<ActiveAlertInfo>> = prefsRepo.lastActiveAlertsFlow

    /**
     * Reactive list of all tracked points (GPS anchor if enabled + user saved points) paired with alert status.
     */
    val trackedPointStatusesFlow: Flow<List<PointStatus>> = combine(
        prefsRepo.settingsFlow,
        prefsRepo.savedPointsFlow,
        prefsRepo.cachedRegionsFlow,
        prefsRepo.lastActiveAlertsFlow
    ) { settings, savedPoints, cachedRegions, activeAlerts ->
        val allPoints = buildTrackedPoints(settings, savedPoints)
        allPoints.map { point ->
            val matchingAlerts = activeAlerts.filter { alert ->
                RegionMatcher.isPointAffectedByAlert(
                    point = point,
                    alert = alert,
                    cachedRegions = cachedRegions
                )
            }
            val highestLevel = when {
                matchingAlerts.any { it.level == AlertLevel.RED } -> AlertLevel.RED
                matchingAlerts.any { it.level == AlertLevel.YELLOW } -> AlertLevel.YELLOW
                else -> AlertLevel.NONE
            }
            val earliestStart = matchingAlerts
                .mapNotNull { it.startedAtIso }
                .minOrNull()

            PointStatus(
                point = point,
                isAlertActive = matchingAlerts.isNotEmpty(),
                highestLevel = highestLevel,
                activeAlerts = matchingAlerts,
                earliestStartedAtIso = earliestStart,
                lastCheckedEpochMs = settings.lastKnownAlertCheckEpochMs
            )
        }
    }

    suspend fun refreshRegionsIfNeeded(force: Boolean = false) {
        if (!ukraineAlarmApi.isUsingOfficialKey) return
        val existing = prefsRepo.cachedRegionsFlow.first()
        if (existing.isNotEmpty() && !force) return
        ukraineAlarmApi.fetchRegions()
            .onSuccess { regions ->
                if (regions.isNotEmpty()) {
                    prefsRepo.saveRegionsCache(regions)
                }
            }
    }

    suspend fun resolveLocationInstantOffline(
        latitude: Double,
        longitude: Double,
        fallbackLabel: String = "Point"
    ): ResolvedLocation {
        val cachedRegions = prefsRepo.cachedRegionsFlow.first()
        return buildResolvedLocation(
            latitude = latitude,
            longitude = longitude,
            address = null,
            fallbackLabel = fallbackLabel,
            cachedRegions = cachedRegions
        )
    }

    suspend fun resolveLocationFromAddress(
        address: GeocodedAddress,
        fallbackLabel: String = "Point"
    ): ResolvedLocation {
        val cachedRegions = prefsRepo.cachedRegionsFlow.first()
        return buildResolvedLocation(
            latitude = address.latitude,
            longitude = address.longitude,
            address = address,
            fallbackLabel = fallbackLabel,
            cachedRegions = cachedRegions
        )
    }

    suspend fun resolveLocationForCoordinates(
        latitude: Double,
        longitude: Double,
        fallbackLabel: String = "Point"
    ): ResolvedLocation {
        val cachedRegions = prefsRepo.cachedRegionsFlow.first()
        val address = runCatching { geocoder.reverseGeocode(latitude, longitude) }.getOrNull()
        return buildResolvedLocation(
            latitude = latitude,
            longitude = longitude,
            address = address,
            fallbackLabel = fallbackLabel,
            cachedRegions = cachedRegions
        )
    }

    private fun buildResolvedLocation(
        latitude: Double,
        longitude: Double,
        address: GeocodedAddress?,
        fallbackLabel: String,
        cachedRegions: List<com.onemanarmy.airalerts.domain.model.RegionItem>
    ): ResolvedLocation {
        val matched = RegionMatcher.matchRegion(
            latitude = latitude,
            longitude = longitude,
            localityName = address?.locality,
            districtName = address?.district,
            adminAreaName = address?.adminArea,
            cachedRegions = cachedRegions
        )
        val oblast = RegionMatcher.findOblastByName(address?.adminArea)?.ukName
            ?: RegionMatcher.findOblastByName(matched.name)?.ukName
            ?: RegionMatcher.oblastCentroids.firstOrNull { it.regionId == matched.regionId }?.ukName
            ?: address?.adminArea.orEmpty()
        val district = address?.district.orEmpty()
        val displayRegion = when {
            district.isNotBlank() && oblast.isNotBlank() &&
                RegionMatcher.normalizeName(district) != RegionMatcher.normalizeName(oblast) -> {
                "$district, $oblast"
            }
            else -> matched.name.ifBlank { oblast.ifBlank { fallbackLabel } }
        }
        val label = address?.locality?.takeIf { it.isNotBlank() }
            ?: district.takeIf { it.isNotBlank() }
            ?: matched.name.ifBlank { fallbackLabel }

        return ResolvedLocation(
            latitude = latitude,
            longitude = longitude,
            regionId = matched.regionId,
            regionName = displayRegion,
            suggestedLabel = label,
            districtName = district,
            oblastName = oblast
        )
    }

    suspend fun searchPlaces(query: String): List<GeocodedAddress> {
        return geocoder.searchAddress(query)
    }

    suspend fun refreshGpsLocation(): ResolvedLocation? {
        val coords = locationClient.getCurrentCoordinates() ?: return null
        val cachedRegions = prefsRepo.cachedRegionsFlow.first()
        val currentSettings = prefsRepo.settingsFlow.first()

        // Seed GPS location immediately with fast offline oblast match when no location is saved yet.
        if (currentSettings.gpsLatitude == null || currentSettings.gpsPointRegionName.isNullOrBlank()) {
            val instant = buildResolvedLocation(
                latitude = coords.latitude,
                longitude = coords.longitude,
                address = null,
                fallbackLabel = "My Location",
                cachedRegions = cachedRegions
            )
            prefsRepo.updateSettings {
                it.copy(
                    gpsLatitude = instant.latitude,
                    gpsLongitude = instant.longitude,
                    gpsPointRegionId = instant.regionId,
                    gpsPointRegionName = instant.regionName
                )
            }
        }

        val resolved = resolveLocationForCoordinates(coords.latitude, coords.longitude, "My Location")
        prefsRepo.updateSettings {
            it.copy(
                gpsLatitude = resolved.latitude,
                gpsLongitude = resolved.longitude,
                gpsPointRegionId = resolved.regionId,
                gpsPointRegionName = resolved.regionName
            )
        }
        return resolved
    }

    /**
     * Polls alert status from UkraineAlarm API v3 (when API key is present) or NEPTUN keyless alerts API.
     */
    suspend fun pollAlertsOnce(
        forceFullFetch: Boolean = false,
        dispatchNotifications: Boolean = true,
        gpsDisplayName: String = "My Location"
    ): PollResult {
        val now = Clock.System.now()
        val nowMs = now.toEpochMilliseconds()
        val settings = prefsRepo.settingsFlow.first()
        val existingAlerts = prefsRepo.lastActiveAlertsFlow.first()

        // Refresh GPS concurrently with alert fetch so GPS latency never slows down alert polling.
        val (remoteIndex, activeAlerts) = coroutineScope {
            val gpsJob = if (settings.gpsTrackingEnabled) {
                async {
                    val timeoutMs = if (settings.gpsLatitude == null) 4_000L else 2_500L
                    withTimeoutOrNull(timeoutMs) {
                        runCatching { refreshGpsLocation() }
                    }
                }
            } else {
                null
            }

            val alertsResult = if (ukraineAlarmApi.isUsingOfficialKey) {
                val official = pollViaUkraineAlarm(settings, existingAlerts, forceFullFetch)
                if (official.isSuccess) {
                    official
                } else {
                    // Automatic resilience fallback to NEPTUN alerts if official endpoint fails.
                    pollViaNeptunAlerts(settings, existingAlerts, forceFullFetch)
                }
            } else {
                pollViaNeptunAlerts(settings, existingAlerts, forceFullFetch)
            }

            gpsJob?.await()
            alertsResult
        }.getOrElse { err ->
            prefsRepo.updateSettings { it.copy(lastApiError = err.message ?: "Network error") }
            return PollResult(
                success = false,
                trackedStatuses = trackedPointStatusesFlow.first(),
                errorMessage = err.message
            )
        }

        prefsRepo.updateSettings {
            it.copy(
                lastActionIndex = remoteIndex,
                lastKnownAlertCheckEpochMs = nowMs,
                lastApiError = null
            )
        }

        val updatedSettings = prefsRepo.settingsFlow.first()
        val savedPoints = prefsRepo.savedPointsFlow.first()
        val cachedRegions = prefsRepo.cachedRegionsFlow.first()
        val trackedPoints = buildTrackedPoints(updatedSettings, savedPoints, gpsDisplayName)
        val previousStates = prefsRepo.alertStatesFlow.first().toMutableMap()
        val isQuietHours = QuietHoursEvaluator.isInQuietHours(updatedSettings, now)
        val decisions = mutableListOf<NotificationDecision>()

        for (point in trackedPoints) {
            val matchingAlerts = activeAlerts.filter { alert ->
                RegionMatcher.isPointAffectedByAlert(
                    point = point,
                    alert = alert,
                    cachedRegions = cachedRegions
                )
            }
            val result = AlertDeduplicator.evaluateTransition(
                point = point,
                activeAlerts = matchingAlerts,
                previousState = previousStates[point.id],
                settings = updatedSettings,
                isQuietHours = isQuietHours,
                nowEpochMs = nowMs
            )
            previousStates[point.id] = result.updatedState
            if (result.decision != NotificationDecision.None) {
                decisions += result.decision
            }
        }

        prefsRepo.saveAlertStates(previousStates)

        if (dispatchNotifications) {
            decisions.forEachIndexed { idx, decision ->
                when (decision) {
                    is NotificationDecision.AlertStarted -> {
                        val typeLabel = formatAlertTypeLabel(decision.primaryType)
                        notifier.showAlertNotification(
                            notificationId = 2000 + decision.point.id.hashCode() + idx,
                            title = getString(Res.string.notif_alert_started_title, decision.point.name),
                            body = getString(Res.string.notif_alert_started_body, typeLabel, decision.regionName),
                            silent = decision.silent,
                            softPulseOnly = decision.softPulseOnly,
                            preset = updatedSettings.vibrationPreset,
                            channelVersion = updatedSettings.notificationChannelVersion
                        )
                    }

                    is NotificationDecision.AllClear -> {
                        notifier.showAlertNotification(
                            notificationId = 2000 + decision.point.id.hashCode() + idx,
                            title = getString(Res.string.notif_all_clear_title, decision.point.name),
                            body = getString(Res.string.notif_all_clear_body, decision.regionName),
                            silent = decision.silent,
                            softPulseOnly = decision.softPulseOnly,
                            preset = updatedSettings.vibrationPreset,
                            channelVersion = updatedSettings.notificationChannelVersion
                        )
                    }

                    NotificationDecision.None -> Unit
                }
            }
        }

        return PollResult(
            success = true,
            decisions = decisions,
            trackedStatuses = trackedPointStatusesFlow.first()
        )
    }

    private suspend fun pollViaUkraineAlarm(
        currentSettings: AppSettings,
        existingAlerts: List<ActiveAlertInfo>,
        forceFullFetch: Boolean
    ): Result<Pair<Long, List<ActiveAlertInfo>>> {
        val statusResult = ukraineAlarmApi.fetchAlertStatus()
        val remoteIndex = statusResult.getOrElse { return Result.failure(it) }

        val shouldFetchFull = forceFullFetch ||
            remoteIndex != currentSettings.lastActionIndex ||
            (remoteIndex == 0L && existingAlerts.isEmpty())

        val activeAlerts = if (shouldFetchFull) {
            ukraineAlarmApi.fetchActiveAlerts()
                .getOrElse { return Result.failure(it) }
                .also { fetched -> prefsRepo.saveLastActiveAlerts(fetched) }
        } else {
            existingAlerts
        }
        return Result.success(remoteIndex to activeAlerts)
    }

    private suspend fun pollViaNeptunAlerts(
        currentSettings: AppSettings,
        existingAlerts: List<ActiveAlertInfo>,
        forceFullFetch: Boolean
    ): Result<Pair<Long, List<ActiveAlertInfo>>> {
        val snapshot = neptunAlertsApi.fetchAlertsSnapshot()
            .getOrElse { return Result.failure(it) }

        if (forceFullFetch || snapshot.version != currentSettings.lastActionIndex || existingAlerts.isEmpty()) {
            prefsRepo.saveLastActiveAlerts(snapshot.alerts)
        }
        return Result.success(snapshot.version to snapshot.alerts)
    }

    private fun buildTrackedPoints(
        settings: AppSettings,
        savedPoints: List<SavedPoint>,
        gpsDisplayName: String = "My Location"
    ): List<SavedPoint> {
        val list = mutableListOf<SavedPoint>()
        if (settings.gpsTrackingEnabled &&
            settings.gpsLatitude != null &&
            settings.gpsLongitude != null &&
            !settings.gpsPointRegionId.isNullOrBlank()
        ) {
            val regionName = settings.gpsPointRegionName ?: gpsDisplayName
            val parts = regionName.split(",").map { it.trim() }
            val inferredDistrict = if (parts.size >= 2) parts.first() else ""
            val inferredOblast = if (parts.size >= 2) parts.last() else regionName
            list += SavedPoint(
                id = GPS_POINT_ID,
                name = gpsDisplayName,
                latitude = settings.gpsLatitude,
                longitude = settings.gpsLongitude,
                regionId = settings.gpsPointRegionId,
                regionName = regionName,
                notifyEnabled = true,
                isGpsAnchor = true,
                districtName = inferredDistrict,
                oblastName = inferredOblast
            )
        }
        list += savedPoints
        return list
    }

    private suspend fun formatAlertTypeLabel(type: AlertType): String = when (type) {
        AlertType.AIR -> getString(Res.string.alert_type_air)
        AlertType.ARTILLERY -> getString(Res.string.alert_type_artillery)
        AlertType.URBAN_FIGHTS -> getString(Res.string.alert_type_urban_fights)
        AlertType.CHEMICAL -> getString(Res.string.alert_type_chemical)
        AlertType.NUCLEAR -> getString(Res.string.alert_type_nuclear)
        AlertType.UNKNOWN -> getString(Res.string.alert_type_unknown)
    }
}
