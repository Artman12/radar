package com.onemanarmy.airalerts.di

import com.onemanarmy.airalerts.data.local.AppPreferencesRepository
import com.onemanarmy.airalerts.data.local.UkraineBoundaryRepository
import com.onemanarmy.airalerts.data.remote.DeepStateOccupiedApi
import com.onemanarmy.airalerts.data.remote.HttpClientFactory
import com.onemanarmy.airalerts.data.remote.NeptunAlertsApi
import com.onemanarmy.airalerts.data.remote.NeptunRadarApi
import com.onemanarmy.airalerts.data.remote.NominatimGeocoderApi
import com.onemanarmy.airalerts.data.remote.UkraineAlarmApi
import com.onemanarmy.airalerts.data.repository.AlertMonitoringRepository
import com.onemanarmy.airalerts.data.repository.OccupiedTerritoriesRepository
import com.onemanarmy.airalerts.data.repository.RadarRepository
import com.onemanarmy.airalerts.domain.logic.RegionThreatEvaluator
import com.onemanarmy.airalerts.platform.PlatformDataStoreFactory
import com.onemanarmy.airalerts.ui.map.MapViewModel
import com.onemanarmy.airalerts.ui.onboarding.OnboardingViewModel
import com.onemanarmy.airalerts.ui.points.PointsViewModel
import com.onemanarmy.airalerts.ui.settings.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

expect fun platformModule(): Module

val commonModule = module {
    single { HttpClientFactory.create() }
    single { UkraineAlarmApi(get()) }
    single { NeptunAlertsApi(get()) }
    single { NeptunRadarApi(get()) }
    single { DeepStateOccupiedApi(get()) }
    single { NominatimGeocoderApi(get()) }

    single { get<PlatformDataStoreFactory>().createPreferencesDataStore() }
    single { AppPreferencesRepository(get()) }
    single { UkraineBoundaryRepository() }
    single { RegionThreatEvaluator() }

    single { RadarRepository(get()) }
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
    viewModel { MapViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { PointsViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
}
