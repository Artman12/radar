package com.onemanarmy.radar.ui.points

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemanarmy.radar.data.local.AppPreferencesRepository
import com.onemanarmy.radar.data.repository.AlertMonitoringRepository
import com.onemanarmy.radar.domain.model.AppSettings
import com.onemanarmy.radar.domain.model.PointStatus
import com.onemanarmy.radar.domain.model.SavedPoint
import com.onemanarmy.radar.platform.GeocodedAddress
import com.onemanarmy.radar.platform.SystemDiagnosticsProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class PointsUiState(
    val settings: AppSettings = AppSettings(),
    val gpsStatus: PointStatus? = null,
    val savedStatuses: List<PointStatus> = emptyList(),
    val isDetectingGps: Boolean = false,
    val isAddSheetOpen: Boolean = false,
    val renamingPoint: SavedPoint? = null,
    val repositioningPoint: SavedPoint? = null,
    val editingSchedulePoint: SavedPoint? = null,
    val searchQuery: String = "",
    val searchResults: List<GeocodedAddress> = emptyList(),
    val isSearching: Boolean = false
)

class PointsViewModel(
    private val prefsRepo: AppPreferencesRepository,
    private val monitoringRepo: AlertMonitoringRepository,
    private val diagnosticsProvider: SystemDiagnosticsProvider
) : ViewModel() {

    private val _local = MutableStateFlow(PointsUiState())
    private var searchJob: Job? = null

    val uiState: StateFlow<PointsUiState> = combine(
        _local,
        prefsRepo.settingsFlow,
        monitoringRepo.trackedPointStatusesFlow
    ) { local, settings, statuses ->
        val gps = statuses.firstOrNull { it.point.isGpsAnchor }
        val saved = statuses.filterNot { it.point.isGpsAnchor }
        local.copy(
            settings = settings,
            gpsStatus = gps,
            savedStatuses = saved
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PointsUiState())

    init {
        viewModelScope.launch {
            diagnosticsProvider.diagnosticsState
                .map { it.hasAnyLocationPermission }
                .distinctUntilChanged()
                .collect { granted ->
                    if (granted) {
                        val settings = prefsRepo.settingsFlow.first()
                        if (settings.gpsTrackingEnabled) {
                            val needsInitialFix = settings.gpsLatitude == null || settings.gpsPointRegionName.isNullOrBlank()
                            if (needsInitialFix) {
                                _local.update { it.copy(isDetectingGps = true) }
                            }
                            monitoringRepo.refreshGpsLocation()
                            if (needsInitialFix) {
                                monitoringRepo.pollAlertsOnce(forceFullFetch = false, dispatchNotifications = false)
                            }
                            _local.update { it.copy(isDetectingGps = false) }
                        }
                    }
                }
        }
    }

    fun toggleGpsTracking(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                _local.update { it.copy(isDetectingGps = true) }
                prefsRepo.updateSettings { it.copy(gpsTrackingEnabled = true) }
                if (!diagnosticsProvider.diagnosticsState.value.hasAnyLocationPermission) {
                    diagnosticsProvider.requestLocationPermission(includeBackground = false)
                } else {
                    monitoringRepo.refreshGpsLocation()
                    monitoringRepo.pollAlertsOnce(forceFullFetch = false, dispatchNotifications = false)
                    _local.update { it.copy(isDetectingGps = false) }
                }
            } else {
                prefsRepo.updateSettings { it.copy(gpsTrackingEnabled = false) }
                _local.update { it.copy(isDetectingGps = false) }
            }
        }
    }

    fun togglePointNotification(point: SavedPoint, enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.saveOrUpdatePoint(point.copy(notifyEnabled = enabled))
        }
    }

    fun openScheduleEditor(point: SavedPoint) {
        _local.update { it.copy(editingSchedulePoint = point) }
    }

    fun dismissScheduleEditor() {
        _local.update { it.copy(editingSchedulePoint = null) }
    }

    fun savePointSchedule(
        point: SavedPoint,
        scheduleEnabled: Boolean,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        activeDaysIso: Set<Int>
    ) {
        viewModelScope.launch {
            val safeDays = activeDaysIso.ifEmpty { point.scheduleActiveDaysIso.ifEmpty { setOf(1, 2, 3, 4, 5) } }
            prefsRepo.saveOrUpdatePoint(
                point.copy(
                    scheduleEnabled = scheduleEnabled,
                    scheduleStartHour = startHour,
                    scheduleStartMinute = startMinute,
                    scheduleEndHour = endHour,
                    scheduleEndMinute = endMinute,
                    scheduleActiveDaysIso = safeDays
                )
            )
            _local.update { it.copy(editingSchedulePoint = null) }
        }
    }

    fun deletePoint(pointId: String) {
        viewModelScope.launch {
            prefsRepo.deletePoint(pointId)
        }
    }

    fun startRename(point: SavedPoint) {
        _local.update { it.copy(renamingPoint = point) }
    }

    fun dismissRename() {
        _local.update { it.copy(renamingPoint = null) }
    }

    fun confirmRename(point: SavedPoint, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            prefsRepo.saveOrUpdatePoint(point.copy(name = trimmed))
            _local.update { it.copy(renamingPoint = null) }
        }
    }

    fun startReposition(point: SavedPoint) {
        _local.update { it.copy(repositioningPoint = point) }
    }

    fun dismissReposition() {
        _local.update { it.copy(repositioningPoint = null) }
    }

    fun confirmReposition(point: SavedPoint, newLat: Double, newLon: Double) {
        viewModelScope.launch {
            val instant = monitoringRepo.resolveLocationInstantOffline(newLat, newLon, point.name)
            val updatedInstant = point.copy(
                latitude = instant.latitude,
                longitude = instant.longitude,
                regionId = instant.regionId,
                regionName = instant.regionName,
                districtName = instant.districtName,
                oblastName = instant.oblastName,
                hromadaName = instant.hromadaName
            )
            prefsRepo.saveOrUpdatePoint(updatedInstant)
            _local.update { it.copy(repositioningPoint = null) }

            val refined = monitoringRepo.resolveLocationForCoordinates(newLat, newLon, point.name)
            val stillExists = prefsRepo.savedPointsFlow.first().firstOrNull { it.id == point.id }
            if (stillExists != null) {
                prefsRepo.saveOrUpdatePoint(
                    stillExists.copy(
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

    fun openAddSheet() {
        _local.update { it.copy(isAddSheetOpen = true, searchQuery = "", searchResults = emptyList()) }
    }

    fun closeAddSheet() {
        _local.update { it.copy(isAddSheetOpen = false) }
    }

    fun updateSearchQuery(query: String) {
        _local.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _local.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            val local = monitoringRepo.searchLocalPlaces(trimmed)
            _local.update { it.copy(searchResults = local) }
            delay(350L)
            val combined = monitoringRepo.searchPlaces(trimmed)
            _local.update { it.copy(searchResults = combined, isSearching = false) }
        }
    }

    fun searchAddress() {
        val query = _local.value.searchQuery.trim()
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _local.update { it.copy(isSearching = true) }
            val results = monitoringRepo.searchPlaces(query)
            _local.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    fun addLocationFromPlace(name: String, place: GeocodedAddress) {
        viewModelScope.launch {
            val fallback = place.locality ?: place.displayName.substringBefore(",")
            val resolved = monitoringRepo.resolveLocationFromAddress(place, fallback)
            val pointName = name.trim().ifBlank { resolved.suggestedLabel }
            prefsRepo.saveOrUpdatePoint(
                SavedPoint(
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
            )
            _local.update { it.copy(isAddSheetOpen = false) }
            monitoringRepo.pollAlertsOnce(forceFullFetch = false, dispatchNotifications = false)
        }
    }

    fun addLocation(name: String, lat: Double, lon: Double, fallbackLabel: String) {
        viewModelScope.launch {
            val customName = name.trim()
            val instant = monitoringRepo.resolveLocationInstantOffline(lat, lon, fallbackLabel)
            val pointId = "point_${Clock.System.now().toEpochMilliseconds()}"
            prefsRepo.saveOrUpdatePoint(
                SavedPoint(
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
            )
            _local.update { it.copy(isAddSheetOpen = false) }

            val refined = monitoringRepo.resolveLocationForCoordinates(lat, lon, fallbackLabel)
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
}
