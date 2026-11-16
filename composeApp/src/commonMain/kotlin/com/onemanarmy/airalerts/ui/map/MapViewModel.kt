package com.onemanarmy.airalerts.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemanarmy.airalerts.data.local.AppPreferencesRepository
import com.onemanarmy.airalerts.data.local.UkraineBoundaryRepository
import com.onemanarmy.airalerts.data.repository.AlertMonitoringRepository
import com.onemanarmy.airalerts.data.repository.RadarRepository
import com.onemanarmy.airalerts.domain.logic.RegionThreatEvaluator
import com.onemanarmy.airalerts.domain.model.ActiveAlertInfo
import com.onemanarmy.airalerts.domain.model.AirThreat
import com.onemanarmy.airalerts.domain.model.AppSettings
import com.onemanarmy.airalerts.domain.model.PointStatus
import com.onemanarmy.airalerts.domain.model.RegionPolygon
import com.onemanarmy.airalerts.domain.model.RegionThreatInfo
import com.onemanarmy.airalerts.domain.model.RegionThreatSnapshot
import com.onemanarmy.airalerts.domain.model.SavedPoint
import com.onemanarmy.airalerts.domain.model.ThreatWithProximity
import com.onemanarmy.airalerts.domain.model.UkraineBounds
import com.onemanarmy.airalerts.domain.model.UkraineMapBoundaries
import com.onemanarmy.airalerts.platform.GeocodedAddress
import com.onemanarmy.airalerts.platform.PlatformLocationClient
import com.onemanarmy.airalerts.platform.SystemDiagnosticsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val activeAlerts: List<ActiveAlertInfo> = emptyList(),
    val boundaries: UkraineMapBoundaries? = null,
    val regionThreats: RegionThreatSnapshot? = null,
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
    val isAddLocationSheetOpen: Boolean = false,
    val isCrosshairMode: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<GeocodedAddress> = emptyList(),
    val isSearching: Boolean = false,
    val isRadarLoading: Boolean = false
)

private data class MapDomainData(
    val statuses: List<PointStatus>,
    val settings: AppSettings,
    val activeAlerts: List<ActiveAlertInfo>,
    val boundaries: UkraineMapBoundaries?,
    val regionThreats: RegionThreatSnapshot?
)

class MapViewModel(
    private val prefsRepo: AppPreferencesRepository,
    private val monitoringRepo: AlertMonitoringRepository,
    private val radarRepo: RadarRepository,
    private val locationClient: PlatformLocationClient,
    private val diagnosticsProvider: SystemDiagnosticsProvider,
    private val boundaryRepo: UkraineBoundaryRepository,
    private val regionThreatEvaluator: RegionThreatEvaluator
) : ViewModel() {

    private val _localState = MutableStateFlow(MapUiState())
    private val _rawThreats = MutableStateFlow<List<AirThreat>>(emptyList())
    private var radarPollingJob: Job? = null
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
            radarRepo.enrichProximity(rawThreats, points, settings.warningRadiusKm)
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
        regionThreatsFlow
    ) { statuses, settings, activeAlerts, boundaries, regionThreats ->
        MapDomainData(statuses, settings, activeAlerts, boundaries, regionThreats)
    }

    val uiState: StateFlow<MapUiState> = combine(
        _localState,
        enrichedThreatsFlow,
        mapDomainFlow
    ) { local, threats, domain ->
        val safeIndex = if (domain.statuses.isEmpty()) 0 else local.selectedPointIndex.coerceIn(0, domain.statuses.lastIndex)
        val nearby = if (domain.settings.radarLayerEnabled && domain.settings.radarNearbyAlertsEnabled) {
            threats
                .filter { it.isWithinWarningRadius }
                .minByOrNull { it.distanceKm ?: Double.MAX_VALUE }
        } else {
            null
        }
        val refreshedSelectedThreat = local.selectedThreatDetails?.let { selected ->
            threats.firstOrNull { it.threat.id == selected.threat.id } ?: selected
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
            settings = domain.settings,
            activeAlerts = domain.activeAlerts,
            boundaries = domain.boundaries,
            regionThreats = domain.regionThreats,
            selectedRegion = refreshedSelectedRegion,
            selectedThreatDetails = refreshedSelectedThreat,
            selectedPointIndex = safeIndex,
            nearbyThreat = nearby
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    init {
        viewModelScope.launch {
            boundaryRepo.loadIfNeeded()
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
    }

    /**
     * Starts NEPTUN radar polling strictly while the Map screen is visible in STARTED/RESUMED lifecycle.
     */
    fun startRadarPolling() {
        if (radarPollingJob?.isActive == true) return
        radarPollingJob = viewModelScope.launch {
            while (isActive) {
                val settings = prefsRepo.settingsFlow.first()
                if (settings.radarLayerEnabled) {
                    refreshRadarOnce()
                } else {
                    _rawThreats.value = emptyList()
                    _localState.update { it.copy(isRadarLoading = false) }
                }
                delay(20_000L)
            }
        }
    }

    fun stopRadarPolling() {
        radarPollingJob?.cancel()
        radarPollingJob = null
    }

    private suspend fun refreshRadarOnce() {
        _localState.update { it.copy(isRadarLoading = true) }
        radarRepo.fetchRawThreats()
            .onSuccess { threats ->
                _rawThreats.value = threats
                _localState.update { it.copy(isRadarLoading = false) }
            }
            .onFailure {
                // NEPTUN failure must never disrupt official alert monitoring.
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

    fun toggleRadarLayer() {
        viewModelScope.launch {
            val current = prefsRepo.settingsFlow.first().radarLayerEnabled
            val next = !current
            prefsRepo.updateSettings { it.copy(radarLayerEnabled = next) }
            if (next) {
                refreshRadarOnce()
            } else {
                _rawThreats.value = emptyList()
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
                selectedRegion = null
            )
        }
    }

    fun onThreatClicked(threat: ThreatWithProximity) {
        _localState.update {
            it.copy(
                selectedThreatDetails = threat,
                selectedPointDetails = null,
                selectedRegion = null
            )
        }
    }

    fun onRegionClicked(polygon: RegionPolygon, threatInfo: RegionThreatInfo?) {
        val info = threatInfo ?: RegionThreatInfo(
            threatLevel = com.onemanarmy.airalerts.domain.model.RegionThreatLevel.CLEAR
        )
        _localState.update {
            it.copy(
                selectedRegion = SelectedRegionDetails(polygon = polygon, threatInfo = info),
                selectedPointDetails = null,
                selectedThreatDetails = null
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
    }

    fun searchAddress() {
        val query = _localState.value.searchQuery.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
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
                oblastName = resolved.oblastName
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
                oblastName = instant.oblastName
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
                        oblastName = refined.oblastName
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
