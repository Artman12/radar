package com.onemanarmy.airalerts.data.repository

import com.onemanarmy.airalerts.data.local.AppPreferencesRepository
import com.onemanarmy.airalerts.data.local.UkraineBoundaryRepository
import com.onemanarmy.airalerts.data.remote.NeptunAlertsApi
import com.onemanarmy.airalerts.data.remote.UkraineAlarmApi
import com.onemanarmy.airalerts.domain.logic.AlertDeduplicator
import com.onemanarmy.airalerts.domain.logic.NotificationDecision
import com.onemanarmy.airalerts.domain.logic.PlaceSearchMatcher
import com.onemanarmy.airalerts.domain.logic.QuietHoursEvaluator
import com.onemanarmy.airalerts.domain.logic.RegionMatcher
import com.onemanarmy.airalerts.domain.logic.ThreatRadiusEvaluator
import com.onemanarmy.airalerts.domain.model.ActiveAlertInfo
import com.onemanarmy.airalerts.domain.model.AirThreat
import com.onemanarmy.airalerts.domain.model.AlertLevel
import com.onemanarmy.airalerts.domain.model.AppSettings
import com.onemanarmy.airalerts.domain.model.PointStatus
import com.onemanarmy.airalerts.domain.model.ResolvedLocation
import com.onemanarmy.airalerts.domain.model.SavedPoint
import com.onemanarmy.airalerts.domain.model.ThreatType
import com.onemanarmy.airalerts.platform.GeocodedAddress
import com.onemanarmy.airalerts.platform.PlatformGeocoder
import com.onemanarmy.airalerts.platform.PlatformLocationClient
import com.onemanarmy.airalerts.platform.PlatformNotifier
import com.onemanarmy.airalerts.resources.Res
import com.onemanarmy.airalerts.resources.notif_alert_started_body
import com.onemanarmy.airalerts.resources.notif_alert_started_title
import com.onemanarmy.airalerts.resources.notif_all_clear_body
import com.onemanarmy.airalerts.resources.notif_all_clear_title
import com.onemanarmy.airalerts.resources.notif_hromada_suffix
import com.onemanarmy.airalerts.resources.notif_threat_radius_body
import com.onemanarmy.airalerts.resources.notif_threat_radius_title
import com.onemanarmy.airalerts.resources.threat_type_aviation
import com.onemanarmy.airalerts.resources.threat_type_ballistic
import com.onemanarmy.airalerts.resources.threat_type_cruise_missile
import com.onemanarmy.airalerts.resources.threat_type_fpv
import com.onemanarmy.airalerts.resources.threat_type_jet_shahed
import com.onemanarmy.airalerts.resources.threat_type_kab
import com.onemanarmy.airalerts.resources.threat_type_recon
import com.onemanarmy.airalerts.resources.threat_type_uav
import com.onemanarmy.airalerts.resources.threat_type_unknown
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.getString
import kotlin.math.roundToInt

data class PollResult(
    val success: Boolean,
    val decisions: List<NotificationDecision> = emptyList(),
    val trackedStatuses: List<PointStatus> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Coordinates alert polling (UkraineAlarm API v3 when key is configured, or NEPTUN keyless alerts API),
 * GPS/location region & hromada mapping, deduplication, and notifications (both hromada status
 * and aggressive 0-10 km threat-in-radius entry alerts).
 */
class AlertMonitoringRepository(
    private val ukraineAlarmApi: UkraineAlarmApi,
    private val neptunAlertsApi: NeptunAlertsApi,
    private val radarRepo: RadarRepository,
    private val boundaryRepo: UkraineBoundaryRepository,
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
        prefsRepo.lastActiveAlertsFlow,
        boundaryRepo.boundariesFlow
    ) { settings, savedPoints, cachedRegions, activeAlerts, _ ->
        val allPoints = buildTrackedPoints(settings, savedPoints).map { enrichPointWithHromada(it) }
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
        boundaryRepo.loadIfNeeded()
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
        boundaryRepo.loadIfNeeded()
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
        boundaryRepo.loadIfNeeded()
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
        val hromadaPolygon = boundaryRepo.findHromadaAt(latitude, longitude)
        val matched = RegionMatcher.matchRegion(
            latitude = latitude,
            longitude = longitude,
            localityName = address?.locality,
            districtName = address?.district?.takeIf { it.isNotBlank() } ?: hromadaPolygon?.raionName,
            adminAreaName = address?.adminArea?.takeIf { it.isNotBlank() } ?: hromadaPolygon?.oblastName,
            cachedRegions = cachedRegions
        )
        val oblast = hromadaPolygon?.oblastName?.takeIf { it.isNotBlank() }
            ?: RegionMatcher.findOblastByName(address?.adminArea)?.ukName
            ?: RegionMatcher.findOblastByName(matched.name)?.ukName
            ?: RegionMatcher.oblastCentroids.firstOrNull { it.regionId == matched.regionId }?.ukName
            ?: address?.adminArea.orEmpty()
        val district = address?.district?.takeIf { it.isNotBlank() }
            ?: hromadaPolygon?.raionName.orEmpty()
        val hromadaName = hromadaPolygon?.name.orEmpty()
        val displayRegion = when {
            district.isNotBlank() && oblast.isNotBlank() &&
                RegionMatcher.normalizeName(district) != RegionMatcher.normalizeName(oblast) -> {
                "$district, $oblast"
            }
            else -> matched.name.ifBlank { oblast.ifBlank { fallbackLabel } }
        }
        val label = address?.locality?.takeIf { it.isNotBlank() }
            ?: hromadaName.takeIf { it.isNotBlank() }
            ?: district.takeIf { it.isNotBlank() }
            ?: matched.name.ifBlank { fallbackLabel }

        return ResolvedLocation(
            latitude = latitude,
            longitude = longitude,
            regionId = matched.regionId,
            regionName = displayRegion,
            suggestedLabel = label,
            districtName = district,
            oblastName = oblast,
            hromadaName = hromadaName
        )
    }

    suspend fun searchLocalPlaces(query: String): List<GeocodedAddress> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        val boundaries = boundaryRepo.loadIfNeeded()
        return PlaceSearchMatcher.search(trimmed, boundaries)
    }

    suspend fun searchPlaces(query: String): List<GeocodedAddress> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        val local = searchLocalPlaces(trimmed)
        val remote = withTimeoutOrNull(3_000L) {
            runCatching { geocoder.searchAddress(trimmed) }.getOrDefault(emptyList())
        }.orEmpty()
        return PlaceSearchMatcher.mergeResults(local, remote)
    }

    suspend fun refreshGpsLocation(): ResolvedLocation? {
        val coords = locationClient.getCurrentCoordinates() ?: return null
        boundaryRepo.loadIfNeeded()
        val cachedRegions = prefsRepo.cachedRegionsFlow.first()
        val currentSettings = prefsRepo.settingsFlow.first()

        // Seed GPS location immediately with fast offline oblast/hromada match when no location is saved yet.
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

    private val pollMutex = Mutex()
    private val threatRadiusMutex = Mutex()
    private var lastSuccessfulPollMs: Long = 0L

    /**
     * Polls alert status from UkraineAlarm API v3 (when API key is present) or NEPTUN keyless alerts API.
     */
    suspend fun pollAlertsOnce(
        forceFullFetch: Boolean = false,
        dispatchNotifications: Boolean = true,
        gpsDisplayName: String = "My Location"
    ): PollResult {
        if (!forceFullFetch && !dispatchNotifications) {
            val age = Clock.System.now().toEpochMilliseconds() - lastSuccessfulPollMs
            if (age in 0L..1_500L) {
                return PollResult(
                    success = true,
                    trackedStatuses = trackedPointStatusesFlow.first()
                )
            }
        }
        return pollMutex.withLock {
            if (!forceFullFetch && !dispatchNotifications) {
                val age = Clock.System.now().toEpochMilliseconds() - lastSuccessfulPollMs
                if (age in 0L..1_500L) {
                    return@withLock PollResult(
                        success = true,
                        trackedStatuses = trackedPointStatusesFlow.first()
                    )
                }
            }
            pollAlertsOnceInternal(
                forceFullFetch = forceFullFetch,
                dispatchNotifications = dispatchNotifications,
                gpsDisplayName = gpsDisplayName
            )
        }
    }

    private suspend fun pollAlertsOnceInternal(
        forceFullFetch: Boolean,
        dispatchNotifications: Boolean,
        gpsDisplayName: String
    ): PollResult {
        boundaryRepo.loadIfNeeded()
        val now = Clock.System.now()
        val nowMs = now.toEpochMilliseconds()
        val settings = prefsRepo.settingsFlow.first()
        val existingAlerts = prefsRepo.lastActiveAlertsFlow.first()

        // Refresh GPS concurrently with a tight bound so slow GPS never blocks alert loading.
        val (remoteIndex, activeAlerts) = coroutineScope {
            val gpsJob = if (settings.gpsTrackingEnabled) {
                async {
                    val timeoutMs = if (settings.gpsLatitude == null) 600L else 250L
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

        lastSuccessfulPollMs = Clock.System.now().toEpochMilliseconds()

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
            .map { enrichPointWithHromada(it) }
        val isQuietHours = QuietHoursEvaluator.isInQuietHours(updatedSettings, now)
        val decisions = mutableListOf<NotificationDecision>()

        prefsRepo.updateAlertStates { currentStates ->
            val mutable = currentStates.toMutableMap()
            decisions.clear()
            for (point in trackedPoints) {
                val matchingAlerts = activeAlerts.filter { alert ->
                    RegionMatcher.isPointAffectedByAlert(
                        point = point,
                        alert = alert,
                        cachedRegions = cachedRegions
                    )
                }
                val prev = mutable[point.id]
                val result = AlertDeduplicator.evaluateTransition(
                    point = point,
                    activeAlerts = matchingAlerts,
                    previousState = prev,
                    settings = updatedSettings,
                    isQuietHours = isQuietHours,
                    nowEpochMs = nowMs
                )
                mutable[point.id] = result.updatedState.copy(
                    isThreatInRadius = prev?.isThreatInRadius ?: false
                )
                if (result.decision != NotificationDecision.None) {
                    decisions += result.decision
                }
            }
            mutable
        }

        if (dispatchNotifications) {
            for (decision in decisions) {
                when (decision) {
                    is NotificationDecision.AlertStarted -> {
                        val hromadaLabel = formatHromadaDisplayName(decision.point)
                        val notifId = baseNotificationId(decision.point.id)
                        notifier.showAlertNotification(
                            notificationId = notifId,
                            title = getString(Res.string.notif_alert_started_title, decision.point.name),
                            body = getString(Res.string.notif_alert_started_body, hromadaLabel),
                            silent = decision.silent,
                            softPulseOnly = decision.softPulseOnly,
                            preset = updatedSettings.vibrationPreset,
                            channelVersion = updatedSettings.notificationChannelVersion,
                            isAlertActive = true
                        )
                    }

                    is NotificationDecision.AllClear -> {
                        val hromadaLabel = formatHromadaDisplayName(decision.point)
                        // Distinct notification ID so Alert Off always appears as a fresh notification.
                        val notifId = baseNotificationId(decision.point.id) + 1
                        notifier.showAlertNotification(
                            notificationId = notifId,
                            title = getString(Res.string.notif_all_clear_title, decision.point.name),
                            body = getString(Res.string.notif_all_clear_body, hromadaLabel),
                            silent = decision.silent,
                            softPulseOnly = decision.softPulseOnly,
                            preset = updatedSettings.vibrationPreset,
                            channelVersion = updatedSettings.notificationChannelVersion,
                            isAlertActive = false
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

    /**
     * Background entry-point for Threat-in-Radius notifications (0..10 km).
     * Performs no network request if the setting is disabled or there are no tracked points.
     */
    suspend fun pollThreatRadiusIfEnabled(
        dispatchNotifications: Boolean = true,
        gpsDisplayName: String = "My Location"
    ) {
        val settings = prefsRepo.settingsFlow.first()
        if (!settings.threatRadiusNotificationEnabled) return
        val savedPoints = prefsRepo.savedPointsFlow.first()
        val trackedPoints = buildTrackedPoints(settings, savedPoints, gpsDisplayName)
        if (trackedPoints.none { it.notifyEnabled }) return

        val threats = radarRepo.fetchRawThreats().getOrNull() ?: return
        evaluateThreatRadiusThreats(
            threats = threats,
            dispatchNotifications = dispatchNotifications,
            gpsDisplayName = gpsDisplayName
        )
    }

    /**
     * Evaluates an already-fetched list of threats against the 0..10 km threat-in-radius setting
     * and dispatches an aggressive buzzer + 4-pulse vibration notification only when the first
     * threat enters a point's radius.
     */
    suspend fun evaluateThreatRadiusThreats(
        threats: List<AirThreat>,
        dispatchNotifications: Boolean = true,
        gpsDisplayName: String = "My Location"
    ) {
        threatRadiusMutex.withLock {
            val settings = prefsRepo.settingsFlow.first()
            val savedPoints = prefsRepo.savedPointsFlow.first()
            val trackedPoints = buildTrackedPoints(settings, savedPoints, gpsDisplayName)
            var pendingNotifications: List<com.onemanarmy.airalerts.domain.logic.ThreatRadiusNotification> = emptyList()

            prefsRepo.updateAlertStates { currentStates ->
                val result = ThreatRadiusEvaluator.evaluate(
                    trackedPoints = trackedPoints,
                    threats = threats,
                    previousStates = currentStates,
                    settings = settings
                )
                pendingNotifications = result.notifications
                result.updatedStates.mapValues { (pointId, state) ->
                    val existing = currentStates[pointId]
                    if (existing != null) {
                        existing.copy(isThreatInRadius = state.isThreatInRadius)
                    } else {
                        state
                    }
                }
            }

            if (dispatchNotifications && settings.threatRadiusNotificationEnabled) {
                for (item in pendingNotifications) {
                    val threatLabel = formatThreatTypeLabel(item.threat.type)
                    val distanceRounded = item.distanceKm.roundToInt().coerceAtLeast(0)
                    val notifId = baseNotificationId(item.point.id) + 2
                    notifier.showThreatRadiusNotification(
                        notificationId = notifId,
                        title = getString(Res.string.notif_threat_radius_title, item.point.name),
                        body = getString(Res.string.notif_threat_radius_body, threatLabel, distanceRounded),
                        vibrationPattern = settings.threatVibrationPattern,
                        soundPattern = settings.threatSoundPattern,
                        soundVolumePercent = settings.threatSoundVolumePercent
                    )
                }
            }
        }
    }

    private fun baseNotificationId(pointId: String): Int {
        return 2000 + ((pointId.hashCode() and 0x7FFFF) * 4)
    }

    private fun enrichPointWithHromada(point: SavedPoint): SavedPoint {
        if (point.hromadaName.isNotBlank() && point.districtName.isNotBlank()) return point
        val hromada = boundaryRepo.findHromadaAt(point.latitude, point.longitude) ?: return point
        return point.copy(
            hromadaName = point.hromadaName.ifBlank { hromada.name },
            districtName = point.districtName.ifBlank { hromada.raionName },
            oblastName = point.oblastName.ifBlank { hromada.oblastName }
        )
    }

    private suspend fun formatHromadaDisplayName(point: SavedPoint): String {
        val raw = point.hromadaName.trim()
        if (raw.isBlank()) return point.regionName.ifBlank { point.name }
        val lower = raw.lowercase()
        return if (lower == "київ" || lower == "м. київ" || lower == "kyiv" ||
            lower == "севастополь" || lower == "sevastopol" ||
            lower.contains("громад") || lower.contains("hromada")
        ) {
            raw
        } else {
            getString(Res.string.notif_hromada_suffix, raw)
        }
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

    private suspend fun formatThreatTypeLabel(type: ThreatType): String = when (type) {
        ThreatType.UAV -> getString(Res.string.threat_type_uav)
        ThreatType.JET_SHAHED -> getString(Res.string.threat_type_jet_shahed)
        ThreatType.FPV -> getString(Res.string.threat_type_fpv)
        ThreatType.RECON -> getString(Res.string.threat_type_recon)
        ThreatType.CRUISE_MISSILE -> getString(Res.string.threat_type_cruise_missile)
        ThreatType.BALLISTIC -> getString(Res.string.threat_type_ballistic)
        ThreatType.KAB -> getString(Res.string.threat_type_kab)
        ThreatType.AVIATION -> getString(Res.string.threat_type_aviation)
        ThreatType.UNKNOWN -> getString(Res.string.threat_type_unknown)
    }
}
