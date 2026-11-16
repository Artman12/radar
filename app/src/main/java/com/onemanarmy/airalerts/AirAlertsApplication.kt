package com.onemanarmy.airalerts

import android.app.Application
import com.onemanarmy.airalerts.data.local.UkraineBoundaryRepository
import com.onemanarmy.airalerts.di.commonModule
import com.onemanarmy.airalerts.di.platformModule
import com.onemanarmy.airalerts.ui.map.UkraineMapPreloader
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AirAlertsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AirAlertsApplication)
            modules(commonModule, platformModule())
        }
        UkraineMapPreloader.startPreload(this, get<UkraineBoundaryRepository>())
    }
}
