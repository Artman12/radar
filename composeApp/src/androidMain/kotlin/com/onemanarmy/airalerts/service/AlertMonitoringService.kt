package com.onemanarmy.airalerts.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.onemanarmy.airalerts.data.repository.AlertMonitoringRepository
import com.onemanarmy.airalerts.domain.model.PointStatus
import com.onemanarmy.airalerts.platform.AndroidPlatformNotifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject

/**
 * Persistent ForegroundService polling only the lightweight UkraineAlarm status endpoint.
 * Handles connectivity loss gracefully with exponential backoff and immediate wakeup via NetworkCallback.
 */
class AlertMonitoringService : Service() {

    private val monitoringRepo: AlertMonitoringRepository by inject()
    private val notifier: AndroidPlatformNotifier by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wakeupSignal = CompletableDeferred<Unit>()

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val BASE_POLL_INTERVAL_MS = 15_000L
        private const val MAX_BACKOFF_MS = 120_000L

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        notifier.ensureForegroundChannel()
        startForegroundWithType(
            buildStatusNotification(getString(com.onemanarmy.airalerts.shared.R.string.fg_status_monitoring))
        )
        registerNetworkCallback()
        startPollingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (pollingJob?.isActive != true) {
            startPollingLoop()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _isRunning.value = false
        unregisterNetworkCallback()
        pollingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            runCatching { monitoringRepo.refreshRegionsIfNeeded() }
            var currentDelayMs = BASE_POLL_INTERVAL_MS

            while (isActive) {
                val gpsLabel = getString(com.onemanarmy.airalerts.shared.R.string.notif_my_location)
                val result = runCatching {
                    monitoringRepo.pollAlertsOnce(
                        forceFullFetch = false,
                        dispatchNotifications = true,
                        gpsDisplayName = gpsLabel
                    )
                }.getOrNull()

                runCatching {
                    monitoringRepo.pollThreatRadiusIfEnabled(
                        dispatchNotifications = true,
                        gpsDisplayName = gpsLabel
                    )
                }

                if (result != null && result.success) {
                    currentDelayMs = BASE_POLL_INTERVAL_MS
                    updateStatusNotification(formatCompactStatus(result.trackedStatuses))
                } else {
                    currentDelayMs = (currentDelayMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                    updateStatusNotification(getString(com.onemanarmy.airalerts.shared.R.string.fg_status_no_network))
                }

                wakeupSignal = CompletableDeferred()
                withTimeoutOrNull(currentDelayMs) {
                    wakeupSignal.await()
                }
            }
        }
    }

    private fun formatCompactStatus(statuses: List<PointStatus>): String {
        if (statuses.isEmpty()) return getString(com.onemanarmy.airalerts.shared.R.string.fg_status_monitoring)
        val activeAlertPoint = statuses.firstOrNull { it.isAlertActive }
        return if (activeAlertPoint != null) {
            getString(com.onemanarmy.airalerts.shared.R.string.fg_status_active_alert, activeAlertPoint.point.name)
        } else {
            val primary = statuses.first()
            getString(com.onemanarmy.airalerts.shared.R.string.fg_status_active_clear, primary.point.name)
        }
    }

    private fun updateStatusNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(FOREGROUND_NOTIFICATION_ID, buildStatusNotification(text))
    }

    private fun buildStatusNotification(contentText: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this,
                FOREGROUND_NOTIFICATION_ID,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            null
        }

        return NotificationCompat.Builder(this, AndroidPlatformNotifier.FOREGROUND_CHANNEL_ID)
            .setSmallIcon(com.onemanarmy.airalerts.shared.R.drawable.ic_notification_siren)
            .setContentTitle("AirAlerts")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (pendingIntent != null) setContentIntent(pendingIntent)
            }
            .build()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun registerNetworkCallback() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        connectivityManager = cm
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wakeupSignal.complete(Unit)
            }
        }
        networkCallback = callback
        runCatching { cm.registerNetworkCallback(request, callback) }
    }

    private fun unregisterNetworkCallback() {
        val cm = connectivityManager
        val cb = networkCallback
        if (cm != null && cb != null) {
            runCatching { cm.unregisterNetworkCallback(cb) }
        }
    }
}
