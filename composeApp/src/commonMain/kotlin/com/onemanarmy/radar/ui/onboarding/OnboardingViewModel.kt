package com.onemanarmy.radar.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemanarmy.radar.data.local.AppPreferencesRepository
import com.onemanarmy.radar.data.repository.AlertMonitoringRepository
import com.onemanarmy.radar.domain.model.ResolvedLocation
import com.onemanarmy.radar.domain.model.SavedPoint
import com.onemanarmy.radar.domain.model.VibrationPreset
import com.onemanarmy.radar.platform.BackgroundServiceController
import com.onemanarmy.radar.platform.PlatformNotifier
import com.onemanarmy.radar.platform.SystemDiagnosticsProvider
import com.onemanarmy.radar.platform.SystemDiagnosticsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class OnboardingUiState(
    val stepIndex: Int = 0,
    val isPreloadingRegions: Boolean = false,
    val isResolvingLocation: Boolean = false,
    val isShowingMapPicker: Boolean = false,
    val selectedModeLabel: String? = null,
    val pendingPinLocation: ResolvedLocation? = null,
    val gpsEnabled: Boolean = false,
    val nightModeEnabled: Boolean = true,
    val selectedVibrationPreset: VibrationPreset = VibrationPreset.DELICATE_DOUBLE
)

class OnboardingViewModel(
    private val prefsRepo: AppPreferencesRepository,
    private val monitoringRepo: AlertMonitoringRepository,
    private val diagnosticsProvider: SystemDiagnosticsProvider,
    private val notifier: PlatformNotifier,
    private val serviceController: BackgroundServiceController
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    val diagnosticsState: StateFlow<SystemDiagnosticsState> = diagnosticsProvider.diagnosticsState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SystemDiagnosticsState())

    init {
        viewModelScope.launch {
            diagnosticsProvider.diagnosticsState
                .map { it.hasAnyLocationPermission }
                .distinctUntilChanged()
                .collect { granted ->
                    if (granted && _uiState.value.gpsEnabled) {
                        _uiState.update { it.copy(isResolvingLocation = true) }
                        val resolved = monitoringRepo.refreshGpsLocation()
                        _uiState.update {
                            it.copy(
                                isResolvingLocation = false,
                                selectedModeLabel = resolved?.regionName ?: it.selectedModeLabel ?: "GPS"
                            )
                        }
                    }
                }
        }
    }

    fun startSetup() {
        _uiState.update { it.copy(stepIndex = 1, isPreloadingRegions = true) }
        viewModelScope.launch {
            monitoringRepo.refreshRegionsIfNeeded()
            _uiState.update { it.copy(isPreloadingRegions = false) }
        }
        diagnosticsProvider.refreshDiagnostics()
    }

    fun nextStep() {
        _uiState.update { it.copy(stepIndex = (it.stepIndex + 1).coerceAtMost(3)) }
    }

    fun previousStep() {
        _uiState.update { it.copy(stepIndex = (it.stepIndex - 1).coerceAtLeast(0), isShowingMapPicker = false) }
    }

    fun refreshDiagnostics() {
        diagnosticsProvider.refreshDiagnostics()
    }

    fun requestNotificationPermission() {
        diagnosticsProvider.requestNotificationPermission()
    }

    fun requestBatteryExemption() {
        diagnosticsProvider.requestIgnoreBatteryOptimizations()
    }

    fun selectGpsLocation() {
        _uiState.update { it.copy(isResolvingLocation = true, gpsEnabled = true) }
        viewModelScope.launch {
            prefsRepo.updateSettings { it.copy(gpsTrackingEnabled = true) }
            if (!diagnosticsProvider.diagnosticsState.value.hasAnyLocationPermission) {
                diagnosticsProvider.requestLocationPermission(includeBackground = false)
            } else {
                val resolved = monitoringRepo.refreshGpsLocation()
                _uiState.update {
                    it.copy(
                        isResolvingLocation = false,
                        selectedModeLabel = resolved?.regionName ?: "GPS"
                    )
                }
            }
        }
    }

    fun openMapPicker() {
        _uiState.update { it.copy(isShowingMapPicker = true) }
    }

    fun closeMapPicker() {
        _uiState.update { it.copy(isShowingMapPicker = false) }
    }

    fun confirmMapPin(latitude: Double, longitude: Double, defaultName: String) {
        _uiState.update { it.copy(isResolvingLocation = true) }
        viewModelScope.launch {
            val resolved = monitoringRepo.resolveLocationForCoordinates(latitude, longitude, defaultName)
            val point = SavedPoint(
                id = "point_${Clock.System.now().toEpochMilliseconds()}",
                name = defaultName,
                latitude = resolved.latitude,
                longitude = resolved.longitude,
                regionId = resolved.regionId,
                regionName = resolved.regionName,
                notifyEnabled = true,
                districtName = resolved.districtName,
                oblastName = resolved.oblastName,
                hromadaName = resolved.hromadaName
            )
            prefsRepo.saveOrUpdatePoint(point)
            _uiState.update {
                it.copy(
                    isResolvingLocation = false,
                    isShowingMapPicker = false,
                    pendingPinLocation = resolved,
                    selectedModeLabel = "${point.name} (${resolved.regionName})"
                )
            }
        }
    }

    fun setNightModeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(nightModeEnabled = enabled) }
    }

    fun selectVibrationPreset(preset: VibrationPreset) {
        _uiState.update { it.copy(selectedVibrationPreset = preset) }
    }

    fun previewVibration() {
        notifier.previewVibration(_uiState.value.selectedVibrationPreset)
    }

    fun finishOnboarding() {
        viewModelScope.launch {
            val state = _uiState.value
            val currentSettings = prefsRepo.settingsFlow.first()
            val newVersion = currentSettings.notificationChannelVersion + 1

            prefsRepo.updateSettings {
                it.copy(
                    isOnboardingCompleted = true,
                    nightModeEnabled = state.nightModeEnabled,
                    vibrationPreset = state.selectedVibrationPreset,
                    notificationChannelVersion = newVersion,
                    gpsTrackingEnabled = state.gpsEnabled || it.gpsTrackingEnabled
                )
            }

            notifier.recreateAlertChannel(
                version = newVersion,
                preset = state.selectedVibrationPreset,
                soundPreset = currentSettings.soundPreset
            )

            serviceController.startMonitoringService()
            monitoringRepo.pollAlertsOnce(forceFullFetch = true, dispatchNotifications = false)
        }
    }
}
