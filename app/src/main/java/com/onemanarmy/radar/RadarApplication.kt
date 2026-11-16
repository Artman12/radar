package com.onemanarmy.radar

import android.app.Application
import com.onemanarmy.radar.data.local.UkraineBoundaryRepository
import com.onemanarmy.radar.di.commonModule
import com.onemanarmy.radar.di.platformModule
import com.onemanarmy.radar.ui.map.UkraineMapPreloader
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class RadarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@RadarApplication)
            modules(commonModule, platformModule())
        }
        UkraineMapPreloader.startPreload(this, get<UkraineBoundaryRepository>())
    }
}
