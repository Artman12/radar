package com.onemanarmy.radar.di

import com.onemanarmy.radar.platform.AndroidDataStoreFactory
import com.onemanarmy.radar.platform.AndroidDiagnosticsAndActivityBridge
import com.onemanarmy.radar.platform.AndroidGeocoder
import com.onemanarmy.radar.platform.AndroidLocationClient
import com.onemanarmy.radar.platform.AndroidPlatformLocaleController
import com.onemanarmy.radar.platform.AndroidPlatformNotifier
import com.onemanarmy.radar.platform.BackgroundServiceController
import com.onemanarmy.radar.platform.PlatformDataStoreFactory
import com.onemanarmy.radar.platform.PlatformGeocoder
import com.onemanarmy.radar.platform.PlatformLocaleController
import com.onemanarmy.radar.platform.PlatformLocationClient
import com.onemanarmy.radar.platform.PlatformNotifier
import com.onemanarmy.radar.platform.SystemDiagnosticsProvider
import com.onemanarmy.radar.service.AndroidBackgroundServiceController
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<PlatformDataStoreFactory> { AndroidDataStoreFactory(androidContext()) }
    single<PlatformLocationClient> { AndroidLocationClient(androidContext()) }
    single<PlatformGeocoder> { AndroidGeocoder(androidContext(), get()) }
    single { AndroidPlatformNotifier(androidContext()) }
    single<PlatformNotifier> { get<AndroidPlatformNotifier>() }

    single { AndroidBackgroundServiceController(androidContext()) }
    single<BackgroundServiceController> { get<AndroidBackgroundServiceController>() }

    single { AndroidDiagnosticsAndActivityBridge(androidContext(), get()) }
    single<SystemDiagnosticsProvider> { get<AndroidDiagnosticsAndActivityBridge>() }

    single<PlatformLocaleController> { AndroidPlatformLocaleController(androidContext()) }
}
