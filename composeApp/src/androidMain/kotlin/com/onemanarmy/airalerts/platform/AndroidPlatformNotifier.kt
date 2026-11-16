package com.onemanarmy.airalerts.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.onemanarmy.airalerts.domain.model.NotificationSoundPreset
import com.onemanarmy.airalerts.domain.model.ThreatSoundPattern
import com.onemanarmy.airalerts.domain.model.ThreatVibrationPattern
import com.onemanarmy.airalerts.domain.model.VibrationPreset

class AndroidPlatformNotifier(
    private val context: Context
) : PlatformNotifier {

    companion object {
        const val FOREGROUND_CHANNEL_ID = "monitoring_status_channel"
        private const val ALERT_CHANNEL_PREFIX = "alert_channel_v"
        private const val SILENT_ALERT_CHANNEL_ID = "alert_channel_silent"
        private const val OLD_THREAT_RADIUS_CHANNEL_ID = "threat_radius_alert_v1"
        private const val THREAT_RADIUS_CHANNEL_ID = "threat_radius_alert_v2"
        private const val TEST_NOTIFICATION_ID = 9999
        private const val TEST_THREAT_NOTIFICATION_ID = 9998

        private val QUAD_HARD_TIMINGS = longArrayOf(0L, 140L, 80L, 140L, 80L, 140L, 80L, 180L)
        private val QUAD_HARD_AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255)

        private val RAPID_BURST_TIMINGS = longArrayOf(0L, 85L, 55L, 85L, 55L, 85L, 55L, 85L, 55L, 85L, 55L, 130L)
        private val RAPID_BURST_AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255)

        private val SOS_URGENT_TIMINGS = longArrayOf(0L, 260L, 90L, 110L, 90L, 260L, 90L, 110L)
        private val SOS_URGENT_AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255)

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

    private val audioLock = Any()
    private var activeMediaPlayer: MediaPlayer? = null

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

    override fun ensureThreatRadiusChannel() {
        // Remove v1 channel that hardcoded system sound & vibration so separate sound/vibro toggles & loudness work cleanly.
        runCatching { notificationManager.deleteNotificationChannel(OLD_THREAT_RADIUS_CHANNEL_ID) }
        if (notificationManager.getNotificationChannel(THREAT_RADIUS_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            THREAT_RADIUS_CHANNEL_ID,
            "Nearby Threat Warnings (0–10 km)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Urgent alert banner when an aerial threat enters your 0–10 km radius"
            enableVibration(false)
            setSound(null, null)
            enableLights(true)
            lightColor = Color.RED
        }
        notificationManager.createNotificationChannel(channel)
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
        val channelId = alertChannelId(version)
        runCatching { notificationManager.deleteNotificationChannel(channelId) }

        val importance = if (soundPreset == NotificationSoundPreset.SILENT && preset == VibrationPreset.NONE) {
            NotificationManager.IMPORTANCE_DEFAULT
        } else {
            NotificationManager.IMPORTANCE_HIGH
        }

        val channel = NotificationChannel(
            channelId,
            "Hromada Air Alerts (v$version)",
            importance
        ).apply {
            description = "Notifications when an air alert becomes active or turns off in your hromada"
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

    @SuppressLint("MissingPermission")
    override fun showAlertNotification(
        notificationId: Int,
        title: String,
        body: String,
        silent: Boolean,
        softPulseOnly: Boolean,
        preset: VibrationPreset,
        channelVersion: Int,
        isAlertActive: Boolean?
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
            .apply {
                when (isAlertActive) {
                    true -> color = 0xFFDC2626.toInt()
                    false -> color = 0xFF16A34A.toInt()
                    null -> Unit
                }
            }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())

        if (softPulseOnly) {
            previewVibration(VibrationPreset.ULTRA_SOFT)
        } else if (!silent && (preset == VibrationPreset.ULTRA_SOFT || preset == VibrationPreset.DELICATE_DOUBLE)) {
            previewVibration(preset)
        }
    }

    @SuppressLint("MissingPermission")
    override fun showThreatRadiusNotification(
        notificationId: Int,
        title: String,
        body: String,
        vibrationPattern: ThreatVibrationPattern,
        soundPattern: ThreatSoundPattern,
        soundVolumePercent: Int
    ) {
        if (!hasPostNotificationPermission()) return
        ensureThreatRadiusChannel()

        val builder = NotificationCompat.Builder(context, THREAT_RADIUS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setColor(0xFFEF4444.toInt())
            .setColorized(true)
            .setSound(null)
            .setAutoCancel(true)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        previewThreatVibration(vibrationPattern)
        previewThreatSound(soundPattern, soundVolumePercent)
    }

    @SuppressLint("MissingPermission")
    override fun previewThreatVibration(pattern: ThreatVibrationPattern) {
        if (pattern == ThreatVibrationPattern.OFF) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val (timings, amplitudes) = when (pattern) {
            ThreatVibrationPattern.OFF -> return
            ThreatVibrationPattern.QUAD_HARD -> QUAD_HARD_TIMINGS to QUAD_HARD_AMPLITUDES
            ThreatVibrationPattern.RAPID_BURST -> RAPID_BURST_TIMINGS to RAPID_BURST_AMPLITUDES
            ThreatVibrationPattern.SOS_URGENT -> SOS_URGENT_TIMINGS to SOS_URGENT_AMPLITUDES
        }

        val effect = if (vib.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            vib.vibrate(effect, attrs)
        } else {
            @Suppress("DEPRECATION")
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            @Suppress("DEPRECATION")
            vib.vibrate(effect, attrs)
        }
    }

    override fun previewThreatSound(pattern: ThreatSoundPattern, volumePercent: Int) {
        val rawName = when (pattern) {
            ThreatSoundPattern.OFF -> {
                stopActiveSound()
                return
            }
            ThreatSoundPattern.BUZZER -> "threat_buzz"
            ThreatSoundPattern.SIREN_PULSE -> "threat_siren"
            ThreatSoundPattern.ALARM_BEEP -> "threat_alarm"
        }

        val clamped = volumePercent.coerceIn(0, 100)
        if (clamped == 0) {
            stopActiveSound()
            return
        }

        val volumeScalar = clamped / 100f
        val soundUri = Uri.parse("android.resource://${context.packageName}/raw/$rawName")

        synchronized(audioLock) {
            runCatching {
                activeMediaPlayer?.let { prev ->
                    runCatching { if (prev.isPlaying) prev.stop() }
                    runCatching { prev.release() }
                }
                activeMediaPlayer = null

                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, soundUri)
                    prepare()
                    setVolume(volumeScalar, volumeScalar)
                    setOnCompletionListener { mp ->
                        synchronized(audioLock) {
                            if (activeMediaPlayer === mp) {
                                activeMediaPlayer = null
                            }
                        }
                        runCatching { mp.release() }
                    }
                    start()
                }
                activeMediaPlayer = player
            }
        }
    }

    private fun stopActiveSound() {
        synchronized(audioLock) {
            activeMediaPlayer?.let { prev ->
                runCatching { if (prev.isPlaying) prev.stop() }
                runCatching { prev.release() }
            }
            activeMediaPlayer = null
        }
    }

    override fun sendTestThreatRadiusNotification(
        title: String,
        body: String,
        vibrationPattern: ThreatVibrationPattern,
        soundPattern: ThreatSoundPattern,
        soundVolumePercent: Int
    ) {
        ensureThreatRadiusChannel()
        showThreatRadiusNotification(
            notificationId = TEST_THREAT_NOTIFICATION_ID,
            title = title,
            body = body,
            vibrationPattern = vibrationPattern,
            soundPattern = soundPattern,
            soundVolumePercent = soundVolumePercent
        )
    }

    @SuppressLint("MissingPermission")
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
            channelVersion = channelVersion,
            isAlertActive = true
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
