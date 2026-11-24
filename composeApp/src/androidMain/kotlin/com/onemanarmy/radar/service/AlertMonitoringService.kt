package com.onemanarmy.radar.service

import android.app.ForegroundServiceStartNotAllowedException
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
import androidx.core.app.ServiceCompat
import com.onemanarmy.radar.data.repository.AlertMonitoringRepository
import com.onemanarmy.radar.domain.model.PointStatus
import com.onemanarmy.radar.platform.AndroidLocaleHelper
import com.onemanarmy.radar.platform.AndroidPlatformNotifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
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
    @Volatile
    private var wakeupSignal = CompletableDeferred<Unit>()
    private var isForegroundStarted = false

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val BASE_POLL_INTERVAL_MS = 15_000L
        private const val MAX_BACKOFF_MS = 120_000L

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    @Volatile
    private var latestStatuses: List<PointStatus> = emptyList()

    override fun attachBaseContext(newBase: Context) {
        val language = AndroidLocaleHelper.getSavedLanguage(newBase)
        AndroidLocaleHelper.applyDefaultLocale(newBase, language)
        super.attachBaseContext(AndroidLocaleHelper.wrapContext(newBase, language))
    }

    private fun localizedContext(): Context = AndroidLocaleHelper.wrapContext(this)

    override fun onCreate() {
        super.onCreate()
        notifier.ensureForegroundChannel()
        val promoted = promoteToForeground(
            localizedContext().getString(com.onemanarmy.radar.shared.R.string.fg_status_monitoring)
        )
        if (!promoted) {
            _isRunning.value = false
            stopSelf()
            return
        }
        _isRunning.value = true
        registerNetworkCallback()
        observeLanguageChanges()
        startPollingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isForegroundStarted) {
            notifier.ensureForegroundChannel()
            val promoted = promoteToForeground(
                localizedContext().getString(com.onemanarmy.radar.shared.R.string.fg_status_monitoring)
            )
            if (!promoted) {
                _isRunning.value = false
                stopSelf(startId)
                return START_NOT_STICKY
            }
            _isRunning.value = true
            registerNetworkCallback()
        }
        if (pollingJob?.isActive != true) {
            startPollingLoop()
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int) {
        handleServiceTimeout(startId)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        handleServiceTimeout(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isForegroundStarted = false
        _isRunning.value = false
        unregisterNetworkCallback()
        pollingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleServiceTimeout(startId: Int) {
        isForegroundStarted = false
        _isRunning.value = false
        pollingJob?.cancel()
        unregisterNetworkCallback()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun promoteToForeground(contentText: String): Boolean {
        return try {
            val notification = buildStatusNotification(contentText)
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                FOREGROUND_NOTIFICATION_ID,
                notification,
                foregroundServiceType
            )
            isForegroundStarted = true
            true
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                isForegroundStarted = false
                false
            } else if (e is SecurityException || e is IllegalStateException) {
                isForegroundStarted = false
                false
            } else {
                throw e
            }
        }
    }

    private fun observeLanguageChanges() {
        serviceScope.launch {
            monitoringRepo.settingsFlow
                .map { it.appLanguage }
                .distinctUntilChanged()
                .drop(1)
                .collect { language ->
                    AndroidLocaleHelper.saveLanguage(this@AlertMonitoringService, language)
                    AndroidLocaleHelper.applyDefaultLocale(this@AlertMonitoringService, language)
                    updateStatusNotification(formatCompactStatus(latestStatuses))
                }
        }
    }

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            runCatching { monitoringRepo.refreshRegionsIfNeeded() }
            var currentDelayMs = BASE_POLL_INTERVAL_MS

            while (isActive) {
                val gpsLabel = localizedContext().getString(com.onemanarmy.radar.shared.R.string.notif_my_location)
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
                    latestStatuses = result.trackedStatuses
                    updateStatusNotification(formatCompactStatus(result.trackedStatuses))
                } else {
                    currentDelayMs = (currentDelayMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                    updateStatusNotification(
                        localizedContext().getString(com.onemanarmy.radar.shared.R.string.fg_status_no_network)
                    )
                }

                val signal = CompletableDeferred<Unit>()
                wakeupSignal = signal
                withTimeoutOrNull(currentDelayMs) {
                    signal.await()
                }
            }
        }
    }

    private fun formatCompactStatus(statuses: List<PointStatus>): String {
        val ctx = localizedContext()
        if (statuses.isEmpty()) return ctx.getString(com.onemanarmy.radar.shared.R.string.fg_status_monitoring)
        val activeAlertPoint = statuses.firstOrNull { it.isAlertActive }
        return if (activeAlertPoint != null) {
            ctx.getString(com.onemanarmy.radar.shared.R.string.fg_status_active_alert, activeAlertPoint.point.name)
        } else {
            val primary = statuses.first()
            ctx.getString(com.onemanarmy.radar.shared.R.string.fg_status_active_clear, primary.point.name)
        }
    }

    private fun updateStatusNotification(text: String) {
        if (!isForegroundStarted) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching {
            manager.notify(FOREGROUND_NOTIFICATION_ID, buildStatusNotification(text))
        }
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
            .setSmallIcon(com.onemanarmy.radar.shared.R.drawable.ic_notification_siren)
            .setContentTitle(applicationInfo.loadLabel(packageManager))
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
        if (networkCallback != null) return
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
            .onFailure { networkCallback = null }
    }

    private fun unregisterNetworkCallback() {
        val cm = connectivityManager
        val cb = networkCallback
        networkCallback = null
        if (cm != null && cb != null) {
            runCatching { cm.unregisterNetworkCallback(cb) }
        }
    }
}
