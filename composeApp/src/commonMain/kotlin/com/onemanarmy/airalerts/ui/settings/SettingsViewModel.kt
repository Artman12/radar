package com.onemanarmy.airalerts.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemanarmy.airalerts.data.local.AppPreferencesRepository
import com.onemanarmy.airalerts.data.remote.NeptunRadarApi
import com.onemanarmy.airalerts.domain.model.AppSettings
import com.onemanarmy.airalerts.domain.model.NightModeBehavior
import com.onemanarmy.airalerts.domain.model.NotificationSoundPreset
import com.onemanarmy.airalerts.domain.model.VibrationPreset
import com.onemanarmy.airalerts.platform.BackgroundServiceController
import com.onemanarmy.airalerts.platform.PlatformNotifier
import com.onemanarmy.airalerts.platform.SystemDiagnosticsProvider
import com.onemanarmy.airalerts.platform.SystemDiagnosticsState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefsRepo: AppPreferencesRepository,
    private val notifier: PlatformNotifier,
    private val serviceController: BackgroundServiceController,
    private val diagnosticsProvider: SystemDiagnosticsProvider
) : ViewModel() {

    val settingsState: StateFlow<AppSettings> = prefsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val diagnosticsState: StateFlow<SystemDiagnosticsState> = diagnosticsProvider.diagnosticsState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SystemDiagnosticsState())

    fun refreshDiagnostics() {
        diagnosticsProvider.refreshDiagnostics()
    }

    fun selectVibrationPreset(preset: VibrationPreset) {
        viewModelScope.launch {
            val current = prefsRepo.settingsFlow.first()
            val nextVersion = current.notificationChannelVersion + 1
            prefsRepo.updateSettings {
                it.copy(
                    vibrationPreset = preset,
                    notificationChannelVersion = nextVersion
                )
            }
            notifier.recreateAlertChannel(
                version = nextVersion,
                preset = preset,
                soundPreset = current.soundPreset
            )
            notifier.previewVibration(preset)
        }
    }

    fun selectSoundPreset(soundPreset: NotificationSoundPreset) {
        viewModelScope.launch {
            val current = prefsRepo.settingsFlow.first()
            val nextVersion = current.notificationChannelVersion + 1
            prefsRepo.updateSettings {
                it.copy(
                    soundPreset = soundPreset,
                    notificationChannelVersion = nextVersion
                )
            }
            notifier.recreateAlertChannel(
                version = nextVersion,
                preset = current.vibrationPreset,
                soundPreset = soundPreset
            )
        }
    }

    fun sendTestNotification(title: String, body: String) {
        viewModelScope.launch {
            val current = prefsRepo.settingsFlow.first()
            notifier.sendTestNotification(
                title = title,
                body = body,
                preset = current.vibrationPreset,
                soundPreset = current.soundPreset,
                channelVersion = current.notificationChannelVersion
            )
        }
    }

    fun setNightModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateSettings { it.copy(nightModeEnabled = enabled) }
        }
    }

    fun updateNightStart(hour: Int, minute: Int) {
        viewModelScope.launch {
            prefsRepo.updateSettings {
                it.copy(nightStartHour = hour, nightStartMinute = minute)
            }
        }
    }

    fun updateNightEnd(hour: Int, minute: Int) {
        viewModelScope.launch {
            prefsRepo.updateSettings {
                it.copy(nightEndHour = hour, nightEndMinute = minute)
            }
        }
    }

    fun toggleNightDay(isoDay: Int) {
        viewModelScope.launch {
            prefsRepo.updateSettings { current ->
                val updated = if (isoDay in current.nightActiveDaysIso) {
                    current.nightActiveDaysIso - isoDay
                } else {
                    current.nightActiveDaysIso + isoDay
                }
                current.copy(nightActiveDaysIso = updated)
            }
        }
    }

    fun selectNightBehavior(behavior: NightModeBehavior) {
        viewModelScope.launch {
            prefsRepo.updateSettings { it.copy(nightModeBehavior = behavior) }
        }
    }

    fun setWarningRadiusKm(radiusKm: Int) {
        viewModelScope.launch {
            prefsRepo.updateSettings { it.copy(warningRadiusKm = radiusKm.coerceIn(10, 100)) }
        }
    }

    fun setRadarNearbyAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateSettings { it.copy(radarNearbyAlertsEnabled = enabled) }
        }
    }

    fun startMonitoringService() {
        serviceController.startMonitoringService()
        diagnosticsProvider.refreshDiagnostics()
    }

    fun requestIgnoreBatteryOptimizations() {
        diagnosticsProvider.requestIgnoreBatteryOptimizations()
    }

    fun requestNotificationPermission() {
        diagnosticsProvider.requestNotificationPermission()
    }

    fun requestLocationPermission(includeBackground: Boolean) {
        diagnosticsProvider.requestLocationPermission(includeBackground)
    }

    fun openNeptunSite() {
        diagnosticsProvider.openUrl(NeptunRadarApi.NEPTUN_BASE_URL)
    }
}
