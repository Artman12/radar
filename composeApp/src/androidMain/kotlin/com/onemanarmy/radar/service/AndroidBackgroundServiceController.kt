package com.onemanarmy.radar.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.onemanarmy.radar.platform.BackgroundServiceController
import kotlinx.coroutines.flow.StateFlow

class AndroidBackgroundServiceController(
    private val context: Context
) : BackgroundServiceController {

    override val isServiceRunning: StateFlow<Boolean>
        get() = AlertMonitoringService.isRunning

    override fun startMonitoringService() {
        if (AlertMonitoringService.isRunning.value) return
        val intent = Intent(context, AlertMonitoringService::class.java)
        runCatching {
            ContextCompat.startForegroundService(context, intent)
        }
    }

    override fun stopMonitoringService() {
        val intent = Intent(context, AlertMonitoringService::class.java)
        runCatching {
            context.stopService(intent)
        }
    }
}
