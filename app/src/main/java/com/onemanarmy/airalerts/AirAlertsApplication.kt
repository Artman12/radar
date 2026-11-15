package com.onemanarmy.airalerts

import android.app.Application
import com.onemanarmy.airalerts.di.commonModule
import com.onemanarmy.airalerts.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AirAlertsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AirAlertsApplication)
            modules(commonModule, platformModule())
        }
    }
}
