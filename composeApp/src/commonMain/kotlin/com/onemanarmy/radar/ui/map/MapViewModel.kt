package com.onemanarmy.radar.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemanarmy.radar.data.local.AppPreferencesRepository
import com.onemanarmy.radar.data.local.UkraineBoundaryRepository
import com.onemanarmy.radar.data.repository.AlertMonitoringRepository
import com.onemanarmy.radar.data.repository.NextAlertExplosionsRepository
import com.onemanarmy.radar.data.repository.OccupiedTerritoriesRepository
import com.onemanarmy.radar.data.repository.RadarRepository
import com.onemanarmy.radar.domain.logic.RegionThreatEvaluator
import com.onemanarmy.radar.domain.logic.ThreatRadiusEvaluator
import com.onemanarmy.radar.domain.model.ActiveAlertInfo
import com.onemanarmy.radar.domain.model.AirThreat
import com.onemanarmy.radar.domain.model.AppSettings
import com.onemanarmy.radar.domain.model.ExplosionEvent
import com.onemanarmy.radar.domain.model.ExplosionWithProximity
import com.onemanarmy.radar.domain.model.OccupiedTerritoriesSnapshot
import com.onemanarmy.radar.domain.model.PointStatus
import com.onemanarmy.radar.domain.model.RegionPolygon
import com.onemanarmy.radar.domain.model.RegionThreatInfo
import com.onemanarmy.radar.domain.model.RegionThreatSnapshot
import com.onemanarmy.radar.domain.model.SavedPoint
import com.onemanarmy.radar.domain.model.ThreatDataSource
import com.onemanarmy.radar.domain.model.ThreatWithProximity
import com.onemanarmy.radar.domain.model.UkraineBounds
import com.onemanarmy.radar.domain.model.UkraineMapBoundaries
import com.onemanarmy.radar.platform.GeocodedAddress
import com.onemanarmy.radar.platform.PlatformLocationClient
import com.onemanarmy.radar.platform.SystemDiagnosticsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class SelectedRegionDetails(
    val polygon: RegionPolygon,
    val threatInfo: RegionThreatInfo
)

data class MapUiState(
    val pointStatuses: List<PointStatus> = emptyList(),
    val threats: List<ThreatWithProximity> = emptyList(),
    val explosions: List<ExplosionWithProximity> = emptyList(),
    val activeAlerts: List<ActiveAlertInfo> = emptyList(),
    val boundaries: UkraineMapBoundaries? = null,
    val regionThreats: RegionThreatSnapshot? = null,
    val occupiedTerritories: OccupiedTerritoriesSnapshot? = null,
    val selectedRegion: SelectedRegionDetails? = null,
    val nearbyThreat: ThreatWithProximity? = null,
    val settings: AppSettings = AppSettings(),
    val selectedPointIndex: Int = 0,
    val mapCenterLat: Double = UkraineBounds.CENTER_LAT,
    val mapCenterLon: Double = UkraineBounds.CENTER_LON,
    val mapZoom: Double = UkraineBounds.COUNTRY_ZOOM,
    val focusVersion: Int = 0,
    val selectedPointDetails: PointStatus? = null,
    val selectedThreatDetails: ThreatWithProximity? = null,
    val selectedExplosionDetails: ExplosionWithProximity? = null,
    val isAddLocationSheetOpen: Boolean = false,
    val isCrosshairMode: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<GeocodedAddress> = emptyList(),
    val isSearching: Boolean = false,
    val isRadarLoading: Boolean = false,
    val nextRadarRefreshEpochMs: Long? = null,
    val radarPollIntervalMs: Long = MapViewModel.RADAR_POLL_INTERVAL_MS,
    val alertPollIntervalMs: Long = MapViewModel.ALERT_POLL_INTERVAL_MS
)

private data class MapDomainData(
    val statuses: List<PointStatus>,
    val settings: AppSettings,
    val activeAlerts: List<ActiveAlertInfo>,
    val boundaries: UkraineMapBoundaries?,
    val regionThreats: RegionThreatSnapshot?,
    val occupiedTerritories: OccupiedTerritoriesSnapshot?
)

class MapViewModel(
    private val prefsRepo: AppPreferencesRepository,
    private val monitoringRepo: AlertMonitoringRepository,
    private val radarRepo: RadarRepository,
    private val locationClient: PlatformLocationClient,
    private val diagnosticsProvider: SystemDiagnosticsProvider,
    private val boundaryRepo: UkraineBoundaryRepository,
    private val regionThreatEvaluator: RegionThreatEvaluator,
    private val occupiedRepo: OccupiedTerritoriesRepository,
    private val explosionsRepo: NextAlertExplosionsRepository
) : ViewModel() {

    private val _localState = MutableStateFlow(MapUiState())
    private val _rawThreats = MutableStateFlow<List<AirThreat>>(emptyList())
    private val _liveExplosions = MutableStateFlow<List<ExplosionEvent>>(emptyList())
    private var radarPollingJob: Job? = null
    private var explosionsPollingJob: Job? = null
    private var searchJob: Job? = null
    private var latestMapCenterLat = UkraineBounds.CENTER_LAT
    private var latestMapCenterLon = UkraineBounds.CENTER_LON
    private var hasUserInteractedWithMap = false
    private var hasDoneInitialAutoFocus = false

    private val enrichedThreatsFlow = combine(
        _rawThreats,
        monitoringRepo.trackedPointStatusesFlow,
        prefsRepo.settingsFlow
    ) { rawThreats, statuses, settings ->
        if (!settings.radarLayerEnabled || rawThreats.isEmpty()) {
            emptyList()
        } else {
            val points = buildProximityPoints(statuses, settings)
            radarRepo.enrichProximity(
                rawThreats,
                points,
                ThreatRadiusEvaluator.effectiveRadiusKm(settings.threatNotificationRadiusKm)
            )
        }
    }.flowOn(Dispatchers.Default)

    private val enrichedExplosionsFlow = combine(
        _liveExplosions,
        monitoringRepo.trackedPointStatusesFlow,
        prefsRepo.settingsFlow
    ) { liveExplosions, statuses, settings ->
        if (!settings.explosionsLayerEnabled || liveExplosions.isEmpty()) {
            emptyList()
        } else {
            val points = buildProximityPoints(statuses, settings)
            explosionsRepo.enrichProximity(liveExplosions, points)
        }
    }.flowOn(Dispatchers.Default)

    private val regionThreatsFlow = combine(
        boundaryRepo.boundariesFlow,
        monitoringRepo.activeAlertsFlow,
        enrichedThreatsFlow
    ) { boundaries, alerts, threats ->
        if (boundaries == null) {
            null
        } else {
            regionThreatEvaluator.evaluate(boundaries, alerts, threats)
        }
    }.flowOn(Dispatchers.Default)

    private val mapDomainFlow = combine(
        monitoringRepo.trackedPointStatusesFlow,
        prefsRepo.settingsFlow,
        monitoringRepo.activeAlertsFlow,
        boundaryRepo.boundariesFlow,
        regionThreatsFlow,
        occupiedRepo.occupiedTerritoriesFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        MapDomainData(
            statuses = values[0] as List<PointStatus>,
            settings = values[1] as AppSettings,
            activeAlerts = values[2] as List<ActiveAlertInfo>,
            boundaries = values[3] as UkraineMapBoundaries?,
            regionThreats = values[4] as RegionThreatSnapshot?,
            occupiedTerritories = values[5] as OccupiedTerritoriesSnapshot?
        )
    }

    val uiState: StateFlow<MapUiState> = combine(
        _localState,
        enrichedThreatsFlow,
        enrichedExplosionsFlow,
        mapDomainFlow
    ) { local, threats, explosions, domain ->
        val safeIndex = if (domain.statuses.isEmpty()) 0 else local.selectedPointIndex.coerceIn(0, domain.statuses.lastIndex)
        val nearby = if (domain.settings.radarLayerEnabled && domain.settings.threatRadiusNotificationEnabled) {
            threats
                .filter { it.isWithinWarningRadius }
                .minByOrNull { it.distanceKm ?: Double.MAX_VALUE }
        } else {
            null
        }
        val refreshedSelectedThreat = local.selectedThreatDetails?.let { selected ->
            threats.firstOrNull { it.threat.id == selected.threat.id } ?: selected
        }
        val refreshedSelectedExplosion = local.selectedExplosionDetails?.let { selected ->
            explosions.firstOrNull { it.explosion.id == selected.explosion.id } ?: selected
        }
        val refreshedSelectedRegion = local.selectedRegion?.let { sel ->
            val regionThreats = domain.regionThreats
            val id = sel.polygon.id
            val updatedInfo = when {
                id.startsWith("oblast_") -> id.removePrefix("oblast_").toIntOrNull()?.let {
                    regionThreats?.oblastInfos?.getOrNull(it)
                }
                id.startsWith("raion_") -> id.removePrefix("raion_").toIntOrNull()?.let {
                    regionThreats?.raionInfos?.getOrNull(it)
                }
                else -> id.removePrefix("hromada_").toIntOrNull()?.let {
                    regionThreats?.hromadaInfos?.getOrNull(it)
                }
            }
            if (updatedInfo != null) sel.copy(threatInfo = updatedInfo) else sel
        }
        local.copy(
            pointStatuses = domain.statuses,
            threats = threats,
            explosions = explosions,
            settings = domain.settings,
            activeAlerts = domain.activeAlerts,
            boundaries = domain.boundaries,
            regionThreats = domain.regionThreats,
            occupiedTerritories = domain.occupiedTerritories,
            selectedRegion = refreshedSelectedRegion,
            selectedThreatDetails = refreshedSelectedThreat,
            selectedExplosionDetails = refreshedSelectedExplosion,
            selectedPointIndex = safeIndex,
            nearbyThreat = nearby
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    init {
        viewModelScope.launch {
            boundaryRepo.loadIfNeeded()
        }

        viewModelScope.launch {
            prefsRepo.settingsFlow
                .map { it.occupiedLayerEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) {
                        occupiedRepo.refreshIfNeeded()
                    }
                }
        }

        // Apply initial focus immediately from cached points before waiting on network.
        viewModelScope.launch {
            val cachedStatuses = monitoringRepo.trackedPointStatusesFlow.first()
            if (!hasUserInteractedWithMap && !hasDoneInitialAutoFocus) {
                cachedStatuses.firstOrNull()?.let { first ->
                    focusMapOn(first.point.latitude, first.point.longitude, 9.5)
                }
            }
        }

        viewModelScope.launch {
            monitoringRepo.refreshRegionsIfNeeded()
            monitoringRepo.pollAlertsOnce(forceFullFetch = false, dispatchNotifications = false)
            if (!hasUserInteractedWithMap && !hasDoneInitialAutoFocus) {
                val statuses = monitoringRepo.trackedPointStatusesFlow.first()
                statuses.firstOrNull()?.let { first ->
                    focusMapOn(first.point.latitude, first.point.longitude, 9.5)
                }
            }
        }

        // Automatically detect GPS as soon as location permission is granted while GPS tracking is enabled.
        viewModelScope.launch {
            diagnosticsProvider.diagnosticsState
                .map { it.hasAnyLocationPermission }
                .distinctUntilChanged()
                .collect { granted ->
                    if (granted) {
                        val settings = prefsRepo.settingsFlow.first()
                        if (settings.gpsTrackingEnabled) {
                            val hadCoords = settings.gpsLatitude != null
                            val resolved = monitoringRepo.refreshGpsLocation()
                            if (!hadCoords && resolved != null && !hasUserInteractedWithMap && !hasDoneInitialAutoFocus) {
                                focusMapOn(resolved.latitude, resolved.longitude, 10.0)
                            }
                        }
                    }
                }
        }

        // Refresh radar immediately whenever the user toggles the radar layer or switches source.
        viewModelScope.launch {
            prefsRepo.settingsFlow
                .map { it.radarLayerEnabled to it.threatDataSource }
                .distinctUntilChanged()
                .drop(1)
                .collect { (enabled, _) ->
                    _rawThreats.value = emptyList()
                    _localState.update {
                        it.copy(
                            selectedThreatDetails = null,
                            isRadarLoading = if (enabled) it.isRadarLoading else false,
                            nextRadarRefreshEpochMs = if (enabled) it.nextRadarRefreshEpochMs else null
                        )
                    }
                    if (radarPollingJob?.isActive == true) {
                        radarPollingJob?.cancel()
                        radarPollingJob = null
                        startRadarOnlyPolling()
                    }
                }
        }
    }

    /**
     * Starts air-threat radar polling strictly while the Map screen is visible in STARTED/RESUMED lifecycle.
     */
    fun startRadarPolling() {
        startRadarOnlyPolling()
        startExplosionsOnlyPolling()
    }

    private fun startRadarOnlyPolling() {
        if (radarPollingJob?.isActive != true) {
            radarPollingJob = viewModelScope.launch {
                while (isActive) {
                    val settings = prefsRepo.settingsFlow.first()
                    if (settings.radarLayerEnabled) {
                        refreshRadarOnce()
                        val nextAt = Clock.System.now().toEpochMilliseconds() + RADAR_POLL_INTERVAL_MS
                        _localState.update { it.copy(nextRadarRefreshEpochMs = nextAt) }
                    } else {
                        _rawThreats.value = emptyList()
                        _localState.update {
                            it.copy(
                                isRadarLoading = false,
                                nextRadarRefreshEpochMs = null
                            )
                        }
                    }
                    delay(RADAR_POLL_INTERVAL_MS)
                }
            }
        }
    }

    private fun startExplosionsOnlyPolling() {
        if (explosionsPollingJob?.isActive != true) {
            explosionsPollingJob = viewModelScope.launch {
                while (isActive) {
                    val settings = prefsRepo.settingsFlow.first()
                    if (settings.explosionsLayerEnabled) {
                        refreshExplosionsOnce()
                    } else {
                        _liveExplosions.value = emptyList()
                    }
                    delay(EXPLOSIONS_POLL_INTERVAL_MS)
                }
            }
        }
    }

    companion object {
        const val RADAR_POLL_INTERVAL_MS = 10_000L
        const val ALERT_POLL_INTERVAL_MS = 15_000L
        const val EXPLOSIONS_POLL_INTERVAL_MS = 30_000L
    }

    fun stopRadarPolling() {
        radarPollingJob?.cancel()
        radarPollingJob = null
        explosionsPollingJob?.cancel()
        explosionsPollingJob = null
        _localState.update {
            it.copy(
                isRadarLoading = false,
                nextRadarRefreshEpochMs = null
            )
        }
    }

    private suspend fun refreshExplosionsOnce() {
        val boundaries = boundaryRepo.boundariesFlow.value ?: boundaryRepo.loadIfNeeded()
        val alerts = monitoringRepo.activeAlertsFlow.first()
        val statuses = monitoringRepo.trackedPointStatusesFlow.first()
        val settings = prefsRepo.settingsFlow.first()
        val points = buildProximityPoints(statuses, settings)
        explosionsRepo.fetchLiveExplosions(
            activeAlerts = alerts,
            trackedPoints = points,
            boundaries = boundaries
        ).onSuccess { events ->
            _liveExplosions.value = events
        }
    }

    private suspend fun refreshRadarOnce() {
        _localState.update { it.copy(isRadarLoading = true) }
        val source = prefsRepo.settingsFlow.first().threatDataSource
        radarRepo.fetchRawThreats(source)
            .onSuccess { threats ->
                _rawThreats.value = threats
                _localState.update { it.copy(isRadarLoading = false) }
                monitoringRepo.evaluateThreatRadiusThreats(
                    threats = threats,
                    dispatchNotifications = true
                )
            }
            .onFailure {
                // Radar failure must never disrupt official alert monitoring.
                _localState.update { it.copy(isRadarLoading = false) }
            }
    }

    private fun buildProximityPoints(
        statuses: List<PointStatus>,
        settings: AppSettings
    ): List<SavedPoint> {
        val points = statuses.map { it.point }
        val hasGpsAnchor = points.any { it.isGpsAnchor }
        if (!hasGpsAnchor &&
            settings.gpsTrackingEnabled &&
            settings.gpsLatitude != null &&
            settings.gpsLongitude != null
        ) {
            return points + SavedPoint(
                id = AlertMonitoringRepository.GPS_POINT_ID,
                name = settings.gpsPointRegionName ?: "My Location",
                latitude = settings.gpsLatitude,
                longitude = settings.gpsLongitude,
                regionId = settings.gpsPointRegionId.orEmpty(),
                regionName = settings.gpsPointRegionName.orEmpty(),
                isGpsAnchor = true
            )
        }
        return points
    }

    fun selectPointPage(index: Int) {
        val statuses = uiState.value.pointStatuses
        val target = statuses.getOrNull(index) ?: return
        _localState.update {
            it.copy(
                selectedPointIndex = index,
                mapCenterLat = target.point.latitude,
                mapCenterLon = target.point.longitude,
                mapZoom = 10.0,
                focusVersion = it.focusVersion + 1
            )
        }
    }

    fun zoomToUkraine() {
        focusMapOn(UkraineBounds.CENTER_LAT, UkraineBounds.CENTER_LON, UkraineBounds.COUNTRY_ZOOM)
    }

    /**
     * Cycles the map radar FAB through: Off -> NEPTUN -> MAPA.UA -> Off.
     */
    fun cycleThreatRadarMode() {
        viewModelScope.launch {
            prefsRepo.updateSettings { settings ->
                when {
                    !settings.radarLayerEnabled -> settings.copy(
                        radarLayerEnabled = true,
                        threatDataSource = ThreatDataSource.NEPTUN
                    )
                    settings.threatDataSource == ThreatDataSource.NEPTUN -> settings.copy(
                        radarLayerEnabled = true,
                        threatDataSource = ThreatDataSource.MAPA_UA
                    )
                    else -> settings.copy(
                        radarLayerEnabled = false
                    )
                }
            }
        }
    }

    fun centerOnMyLocation() {
        viewModelScope.launch {
            if (!diagnosticsProvider.diagnosticsState.value.hasAnyLocationPermission) {
                prefsRepo.updateSettings { it.copy(gpsTrackingEnabled = true) }
                diagnosticsProvider.requestLocationPermission(includeBackground = false)
                return@launch
            }
            val coords = locationClient.getCurrentCoordinates()
            if (coords != null) {
                focusMapOn(coords.latitude, coords.longitude, 11.0)
                monitoringRepo.refreshGpsLocation()
            } else {
                diagnosticsProvider.requestLocationPermission(includeBackground = false)
            }
        }
    }

    fun focusMapOn(lat: Double, lon: Double, zoom: Double = 10.0) {
        hasDoneInitialAutoFocus = true
        _localState.update {
            it.copy(
                mapCenterLat = lat,
                mapCenterLon = lon,
                mapZoom = zoom,
                focusVersion = it.focusVersion + 1
            )
        }
    }

    fun onUserMapInteraction() {
        hasUserInteractedWithMap = true
    }

    fun onMapViewportChanged(lat: Double, lon: Double, zoom: Double) {
        latestMapCenterLat = lat
        latestMapCenterLon = lon
        _localState.update {
            it.copy(
                mapCenterLat = lat,
                mapCenterLon = lon,
                mapZoom = zoom
            )
        }
    }

    fun onPointClicked(status: PointStatus) {
        _localState.update {
            it.copy(
                selectedPointDetails = status,
                selectedThreatDetails = null,
                selectedExplosionDetails = null,
                selectedRegion = null
            )
        }
    }

    fun onThreatClicked(threat: ThreatWithProximity) {
        _localState.update {
            it.copy(
                selectedThreatDetails = threat,
                selectedPointDetails = null,
                selectedExplosionDetails = null,
                selectedRegion = null
            )
        }
    }

    fun onExplosionClicked(explosion: ExplosionWithProximity) {
        _localState.update {
            it.copy(
                selectedExplosionDetails = explosion,
                selectedPointDetails = null,
                selectedThreatDetails = null,
                selectedRegion = null
            )
        }
    }

    fun onRegionClicked(polygon: RegionPolygon, threatInfo: RegionThreatInfo?) {
        val info = threatInfo ?: RegionThreatInfo(
            threatLevel = com.onemanarmy.radar.domain.model.RegionThreatLevel.CLEAR
        )
        _localState.update {
            it.copy(
                selectedRegion = SelectedRegionDetails(polygon = polygon, threatInfo = info),
                selectedPointDetails = null,
                selectedThreatDetails = null,
                selectedExplosionDetails = null
            )
        }
    }

    fun dismissSelectedRegion() {
        _localState.update { it.copy(selectedRegion = null) }
    }

    fun dismissDetailsSheets() {
        _localState.update {
            it.copy(
                selectedPointDetails = null,
                selectedThreatDetails = null,
                selectedExplosionDetails = null,
                selectedRegion = null
            )
        }
    }

    fun openAddLocationSheet() {
        _localState.update {
            it.copy(
                isAddLocationSheetOpen = true,
                searchQuery = "",
                searchResults = emptyList(),
                selectedRegion = null
            )
        }
    }

    fun closeAddLocationSheet() {
        _localState.update { it.copy(isAddLocationSheetOpen = false) }
    }

    fun enterCrosshairMode() {
        _localState.update {
            it.copy(
                isAddLocationSheetOpen = false,
                isCrosshairMode = true,
                selectedRegion = null
            )
        }
    }

    fun cancelCrosshairMode() {
        _localState.update { it.copy(isCrosshairMode = false) }
    }

    fun onMapCenterChanged(lat: Double, lon: Double) {
        latestMapCenterLat = lat
        latestMapCenterLon = lon
    }

    fun saveCrosshairLocation(defaultLabel: String) {
        saveLocationFromCoordinates(
            name = "",
            latitude = latestMapCenterLat,
            longitude = latestMapCenterLon,
            defaultLabel = defaultLabel
        )
    }

    fun updateSearchQuery(query: String) {
        _localState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _localState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            val local = monitoringRepo.searchLocalPlaces(trimmed)
            _localState.update { it.copy(searchResults = local) }
            delay(350L)
            val combined = monitoringRepo.searchPlaces(trimmed)
            _localState.update { it.copy(searchResults = combined, isSearching = false) }
        }
    }

    fun searchAddress() {
        val query = _localState.value.searchQuery.trim()
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _localState.update { it.copy(isSearching = true) }
            val results = monitoringRepo.searchPlaces(query)
            _localState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    fun saveLocationFromPlace(name: String, place: GeocodedAddress) {
        viewModelScope.launch {
            val fallback = place.locality ?: place.displayName.substringBefore(",")
            val resolved = monitoringRepo.resolveLocationFromAddress(place, fallback)
            val pointName = name.trim().ifBlank { resolved.suggestedLabel }
            val newPoint = SavedPoint(
                id = "point_${Clock.System.now().toEpochMilliseconds()}",
                name = pointName,
                latitude = resolved.latitude,
                longitude = resolved.longitude,
                regionId = resolved.regionId,
                regionName = resolved.regionName,
                notifyEnabled = true,
                districtName = resolved.districtName,
                oblastName = resolved.oblastName,
                hromadaName = resolved.hromadaName
            )
            prefsRepo.saveOrUpdatePoint(newPoint)
            _localState.update {
                it.copy(
                    isAddLocationSheetOpen = false,
                    isCrosshairMode = false,
                    mapCenterLat = resolved.latitude,
                    mapCenterLon = resolved.longitude,
                    mapZoom = 10.5,
                    focusVersion = it.focusVersion + 1
                )
            }
            monitoringRepo.pollAlertsOnce(forceFullFetch = false, dispatchNotifications = false)
        }
    }

    fun saveLocationFromCoordinates(name: String, latitude: Double, longitude: Double, defaultLabel: String) {
        viewModelScope.launch {
            val customName = name.trim()
            val instant = monitoringRepo.resolveLocationInstantOffline(latitude, longitude, defaultLabel)
            val pointId = "point_${Clock.System.now().toEpochMilliseconds()}"
            val initialPoint = SavedPoint(
                id = pointId,
                name = customName.ifBlank { instant.suggestedLabel },
                latitude = instant.latitude,
                longitude = instant.longitude,
                regionId = instant.regionId,
                regionName = instant.regionName,
                notifyEnabled = true,
                districtName = instant.districtName,
                oblastName = instant.oblastName,
                hromadaName = instant.hromadaName
            )
            prefsRepo.saveOrUpdatePoint(initialPoint)
            _localState.update {
                it.copy(
                    isAddLocationSheetOpen = false,
                    isCrosshairMode = false,
                    mapCenterLat = instant.latitude,
                    mapCenterLon = instant.longitude,
                    mapZoom = 10.5,
                    focusVersion = it.focusVersion + 1
                )
            }

            // Enrich exact district/locality name in the background without blocking UI.
            val refined = monitoringRepo.resolveLocationForCoordinates(latitude, longitude, defaultLabel)
            val stillExists = prefsRepo.savedPointsFlow.first().firstOrNull { it.id == pointId }
            if (stillExists != null) {
                prefsRepo.saveOrUpdatePoint(
                    stillExists.copy(
                        name = customName.ifBlank { refined.suggestedLabel },
                        regionId = refined.regionId,
                        regionName = refined.regionName,
                        districtName = refined.districtName,
                        oblastName = refined.oblastName,
                        hromadaName = refined.hromadaName
                    )
                )
            }
            monitoringRepo.pollAlertsOnce(forceFullFetch = false, dispatchNotifications = false)
        }
    }

    fun openExternalUrl(url: String) {
        diagnosticsProvider.openUrl(url)
    }

    override fun onCleared() {
        super.onCleared()
        stopRadarPolling()
    }
}
