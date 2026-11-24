package com.onemanarmy.radar.di

import com.onemanarmy.radar.data.local.AppPreferencesRepository
import com.onemanarmy.radar.data.local.UkraineBoundaryRepository
import com.onemanarmy.radar.data.remote.DeepStateOccupiedApi
import com.onemanarmy.radar.data.remote.HttpClientFactory
import com.onemanarmy.radar.data.remote.MapaUaRadarApi
import com.onemanarmy.radar.data.remote.NeptunAlertsApi
import com.onemanarmy.radar.data.remote.NeptunRadarApi
import com.onemanarmy.radar.data.remote.NextAlertExplosionsApi
import com.onemanarmy.radar.data.remote.NominatimGeocoderApi
import com.onemanarmy.radar.data.remote.UkraineAlarmApi
import com.onemanarmy.radar.data.repository.AlertMonitoringRepository
import com.onemanarmy.radar.data.repository.NextAlertExplosionsRepository
import com.onemanarmy.radar.data.repository.OccupiedTerritoriesRepository
import com.onemanarmy.radar.data.repository.RadarRepository
import com.onemanarmy.radar.domain.logic.RegionThreatEvaluator
import com.onemanarmy.radar.platform.PlatformDataStoreFactory
import com.onemanarmy.radar.ui.map.MapViewModel
import com.onemanarmy.radar.ui.onboarding.OnboardingViewModel
import com.onemanarmy.radar.ui.points.PointsViewModel
import com.onemanarmy.radar.ui.settings.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

expect fun platformModule(): Module

val commonModule = module {
    single { HttpClientFactory.create() }
    single { UkraineAlarmApi(get()) }
    single { NeptunAlertsApi(get()) }
    single { NeptunRadarApi(get()) }
    single { MapaUaRadarApi(get()) }
    single { NextAlertExplosionsApi(get()) }
    single { DeepStateOccupiedApi(get()) }
    single { NominatimGeocoderApi(get()) }

    single { get<PlatformDataStoreFactory>().createPreferencesDataStore() }
    single { AppPreferencesRepository(get()) }
    single { UkraineBoundaryRepository() }
    single { RegionThreatEvaluator() }

    single { RadarRepository(get(), get()) }
    single { NextAlertExplosionsRepository(get()) }
    single { OccupiedTerritoriesRepository(get(), get()) }
    single {
        AlertMonitoringRepository(
            ukraineAlarmApi = get(),
            neptunAlertsApi = get(),
            radarRepo = get(),
            boundaryRepo = get(),
            prefsRepo = get(),
            locationClient = get(),
            geocoder = get(),
            notifier = get()
        )
    }

    viewModel { OnboardingViewModel(get(), get(), get(), get(), get()) }
    viewModel { MapViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { PointsViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get()) }
}
