package com.onemanarmy.radar.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.onemanarmy.radar.data.local.AppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Restarts background alert monitoring automatically after device reboot or app update.
 */
class BootCompletedReceiver : BroadcastReceiver(), KoinComponent {

    private val prefsRepo: AppPreferencesRepository by inject()

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = runCatching { prefsRepo.settingsFlow.first() }.getOrNull()
                if (settings?.isOnboardingCompleted == true) {
                    val serviceIntent = Intent(context, AlertMonitoringService::class.java)
                    runCatching {
                        ContextCompat.startForegroundService(context, serviceIntent)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
