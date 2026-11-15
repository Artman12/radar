package com.onemanarmy.airalerts.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * Bridges Compose Multiplatform UI requests in commonMain to Android runtime permission dialogs and Settings intents.
 */
class AndroidDiagnosticsAndActivityBridge(
    private val context: Context,
    private val serviceController: BackgroundServiceController
) : SystemDiagnosticsProvider {

    private var activityRef: WeakReference<ComponentActivity>? = null
    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null

    private val _diagnosticsState = MutableStateFlow(computeDiagnostics())
    override val diagnosticsState: StateFlow<SystemDiagnosticsState> = _diagnosticsState.asStateFlow()

    fun attachActivity(activity: ComponentActivity) {
        activityRef = WeakReference(activity)
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            refreshDiagnostics()
        }
        refreshDiagnostics()
    }

    fun detachActivity(activity: ComponentActivity) {
        if (activityRef?.get() === activity) {
            activityRef = null
            permissionLauncher = null
        }
    }

    override fun refreshDiagnostics() {
        _diagnosticsState.value = computeDiagnostics()
    }

    override fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val launcher = permissionLauncher
            if (launcher != null) {
                launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                return
            }
        }
        openAppNotificationSettings()
    }

    override fun requestLocationPermission(includeBackground: Boolean) {
        val launcher = permissionLauncher ?: return
        if (includeBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasFine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            if (!hasFine) {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            }
        } else {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("BatteryLife")
    override fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            val activity = activityRef?.get()
            if (activity != null) {
                activity.startActivity(intent.apply { flags = 0 })
            } else {
                context.startActivity(intent)
            }
        }.onFailure {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }

    override fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    override fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun computeDiagnostics(): SystemDiagnosticsState {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) == true

        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

        val hasFine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        val hasBg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            hasFine
        }

        return SystemDiagnosticsState(
            isServiceRunning = serviceController.isServiceRunning.value,
            isBatteryOptimizationIgnored = batteryIgnored,
            hasNotificationPermission = hasNotif,
            hasFineLocationPermission = hasFine,
            hasBackgroundLocationPermission = hasBg,
            hasAnyLocationPermission = hasFine || hasCoarse
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
