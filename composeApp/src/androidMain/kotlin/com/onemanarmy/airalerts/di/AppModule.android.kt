package com.onemanarmy.airalerts.di

import com.onemanarmy.airalerts.platform.AndroidDataStoreFactory
import com.onemanarmy.airalerts.platform.AndroidDiagnosticsAndActivityBridge
import com.onemanarmy.airalerts.platform.AndroidGeocoder
import com.onemanarmy.airalerts.platform.AndroidLocationClient
import com.onemanarmy.airalerts.platform.AndroidPlatformNotifier
import com.onemanarmy.airalerts.platform.BackgroundServiceController
import com.onemanarmy.airalerts.platform.PlatformDataStoreFactory
import com.onemanarmy.airalerts.platform.PlatformGeocoder
import com.onemanarmy.airalerts.platform.PlatformLocationClient
import com.onemanarmy.airalerts.platform.PlatformNotifier
import com.onemanarmy.airalerts.platform.SystemDiagnosticsProvider
import com.onemanarmy.airalerts.service.AndroidBackgroundServiceController
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
}
