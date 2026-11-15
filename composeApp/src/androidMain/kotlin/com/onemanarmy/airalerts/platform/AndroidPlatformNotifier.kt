package com.onemanarmy.airalerts.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.onemanarmy.airalerts.domain.model.NotificationSoundPreset
import com.onemanarmy.airalerts.domain.model.VibrationPreset

class AndroidPlatformNotifier(
    private val context: Context
) : PlatformNotifier {

    companion object {
        const val FOREGROUND_CHANNEL_ID = "monitoring_status_channel"
        private const val ALERT_CHANNEL_PREFIX = "alert_channel_v"
        private const val SILENT_ALERT_CHANNEL_ID = "alert_channel_silent"
        private const val TEST_NOTIFICATION_ID = 9999

        fun alertChannelId(version: Int): String = "$ALERT_CHANNEL_PREFIX$version"
    }

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun ensureForegroundChannel() {
        val existing = notificationManager.getNotificationChannel(FOREGROUND_CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Background Alert Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent status notification while AirAlerts monitors official feeds"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun ensureSilentAlertChannel() {
        if (notificationManager.getNotificationChannel(SILENT_ALERT_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                SILENT_ALERT_CHANNEL_ID,
                "Silent Night Alerts",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Silent alert notifications during Quiet Hours"
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun recreateAlertChannel(
        version: Int,
        preset: VibrationPreset,
        soundPreset: NotificationSoundPreset
    ) {
        // Delete older versioned alert channels because Android freezes channel vibration once created.
        for (oldVersion in 1 until version) {
            runCatching { notificationManager.deleteNotificationChannel(alertChannelId(oldVersion)) }
        }
        // Also delete target channel if it already existed before recreating.
        val channelId = alertChannelId(version)
        runCatching { notificationManager.deleteNotificationChannel(channelId) }

        val importance = if (soundPreset == NotificationSoundPreset.SILENT && preset == VibrationPreset.NONE) {
            NotificationManager.IMPORTANCE_DEFAULT
        } else {
            NotificationManager.IMPORTANCE_HIGH
        }

        val channel = NotificationChannel(
            channelId,
            "Air Raid Alerts (v$version)",
            importance
        ).apply {
            description = "Notifications for air alerts and all-clears"
            when (preset) {
                VibrationPreset.NONE -> {
                    enableVibration(false)
                }
                VibrationPreset.ULTRA_SOFT -> {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0L, 90L)
                }
                VibrationPreset.DELICATE_DOUBLE -> {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0L, 80L, 120L, 80L)
                }
                VibrationPreset.STANDARD -> {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0L, 250L, 150L, 250L)
                }
            }

            val soundUri = when (soundPreset) {
                NotificationSoundPreset.DEFAULT -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                NotificationSoundPreset.SOFT_CHIME -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                NotificationSoundPreset.SILENT -> null
            }
            if (soundUri != null) {
                setSound(
                    soundUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            } else {
                setSound(null, null)
            }
        }

        notificationManager.createNotificationChannel(channel)
    }

    override fun showAlertNotification(
        notificationId: Int,
        title: String,
        body: String,
        silent: Boolean,
        softPulseOnly: Boolean,
        preset: VibrationPreset,
        channelVersion: Int
    ) {
        if (!hasPostNotificationPermission()) return

        val channelId = if (silent || softPulseOnly) {
            ensureSilentAlertChannel()
            SILENT_ALERT_CHANNEL_ID
        } else {
            val id = alertChannelId(channelVersion)
            if (notificationManager.getNotificationChannel(id) == null) {
                recreateAlertChannel(channelVersion, preset, NotificationSoundPreset.DEFAULT)
            }
            id
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(if (silent || softPulseOnly) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setSilent(silent || softPulseOnly)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                notificationId,
                launchIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())

        if (softPulseOnly) {
            previewVibration(VibrationPreset.ULTRA_SOFT)
        } else if (!silent && (preset == VibrationPreset.ULTRA_SOFT || preset == VibrationPreset.DELICATE_DOUBLE)) {
            // Reinforce delicate amplitude-controlled waveform on devices that support amplitude control.
            previewVibration(preset)
        }
    }

    override fun previewVibration(preset: VibrationPreset) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator() || preset == VibrationPreset.NONE) return

        val (timings, amplitudes) = when (preset) {
            VibrationPreset.NONE -> return
            VibrationPreset.ULTRA_SOFT -> {
                longArrayOf(0L, 90L) to intArrayOf(0, 65)
            }
            VibrationPreset.DELICATE_DOUBLE -> {
                longArrayOf(0L, 80L, 120L, 80L) to intArrayOf(0, 95, 0, 95)
            }
            VibrationPreset.STANDARD -> {
                longArrayOf(0L, 250L, 150L, 250L) to intArrayOf(0, 255, 0, 255)
            }
        }

        val effect = if (vib.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        vib.vibrate(effect)
    }

    override fun sendTestNotification(
        title: String,
        body: String,
        preset: VibrationPreset,
        soundPreset: NotificationSoundPreset,
        channelVersion: Int
    ) {
        recreateAlertChannel(channelVersion, preset, soundPreset)
        showAlertNotification(
            notificationId = TEST_NOTIFICATION_ID,
            title = title,
            body = body,
            silent = false,
            softPulseOnly = false,
            preset = preset,
            channelVersion = channelVersion
        )
    }

    private fun hasPostNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
