package com.onemanarmy.airalerts.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemanarmy.airalerts.domain.logic.DurationFormatter
import com.onemanarmy.airalerts.domain.model.NightModeBehavior
import com.onemanarmy.airalerts.domain.model.NotificationSoundPreset
import com.onemanarmy.airalerts.domain.model.VibrationPreset
import com.onemanarmy.airalerts.resources.Res
import com.onemanarmy.airalerts.resources.about_disclaimer
import com.onemanarmy.airalerts.resources.about_neptun
import com.onemanarmy.airalerts.resources.about_ukraine_alarm
import com.onemanarmy.airalerts.resources.btn_cancel
import com.onemanarmy.airalerts.resources.btn_save
import com.onemanarmy.airalerts.resources.btn_test_notification
import com.onemanarmy.airalerts.resources.day_fri
import com.onemanarmy.airalerts.resources.day_mon
import com.onemanarmy.airalerts.resources.day_sat
import com.onemanarmy.airalerts.resources.day_sun
import com.onemanarmy.airalerts.resources.day_thu
import com.onemanarmy.airalerts.resources.day_tue
import com.onemanarmy.airalerts.resources.day_wed
import com.onemanarmy.airalerts.resources.diag_battery_ok
import com.onemanarmy.airalerts.resources.diag_battery_restricted
import com.onemanarmy.airalerts.resources.diag_battery_title
import com.onemanarmy.airalerts.resources.diag_bg_location_title
import com.onemanarmy.airalerts.resources.diag_fix
import com.onemanarmy.airalerts.resources.diag_location_title
import com.onemanarmy.airalerts.resources.diag_notifications_title
import com.onemanarmy.airalerts.resources.diag_service_running
import com.onemanarmy.airalerts.resources.diag_service_start
import com.onemanarmy.airalerts.resources.diag_service_stopped
import com.onemanarmy.airalerts.resources.diag_service_title
import com.onemanarmy.airalerts.resources.settings_about_section
import com.onemanarmy.airalerts.resources.settings_diagnostics_section
import com.onemanarmy.airalerts.resources.settings_kb_subtitle
import com.onemanarmy.airalerts.resources.settings_kb_title
import com.onemanarmy.airalerts.resources.settings_night_behavior
import com.onemanarmy.airalerts.resources.settings_night_days
import com.onemanarmy.airalerts.resources.settings_night_end
import com.onemanarmy.airalerts.resources.settings_night_interval
import com.onemanarmy.airalerts.resources.settings_night_mode_enable
import com.onemanarmy.airalerts.resources.settings_night_mode_section
import com.onemanarmy.airalerts.resources.settings_night_start
import com.onemanarmy.airalerts.resources.settings_notifications_section
import com.onemanarmy.airalerts.resources.settings_radar_nearby_alerts
import com.onemanarmy.airalerts.resources.settings_radar_section
import com.onemanarmy.airalerts.resources.settings_sound_preset
import com.onemanarmy.airalerts.resources.settings_vibration_preset
import com.onemanarmy.airalerts.resources.settings_warning_radius
import com.onemanarmy.airalerts.resources.status_granted
import com.onemanarmy.airalerts.resources.status_not_granted
import com.onemanarmy.airalerts.resources.test_notification_body
import com.onemanarmy.airalerts.resources.test_notification_title
import com.onemanarmy.airalerts.ui.common.nightBehaviorLabel
import com.onemanarmy.airalerts.ui.common.soundPresetLabel
import com.onemanarmy.airalerts.ui.common.vibrationPresetLabel
import com.onemanarmy.airalerts.ui.components.ThreatKnowledgeBaseSheet
import com.onemanarmy.airalerts.ui.theme.AlertColors
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsTabScreen(
    viewModel: SettingsViewModel = koinViewModel()
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnosticsState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    var editingStartTime by remember { mutableStateOf(false) }
    var editingEndTime by remember { mutableStateOf(false) }
    var isKnowledgeBaseOpen by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDiagnostics()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.refreshDiagnostics()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val testTitle = stringResource(Res.string.test_notification_title)
    val testBody = stringResource(Res.string.test_notification_body)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Notifications & Delicate Vibration
        item {
            SettingsSectionCard(
                icon = Icons.Default.Notifications,
                title = stringResource(Res.string.settings_notifications_section)
            ) {
                Text(
                    text = stringResource(Res.string.settings_vibration_preset),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                VibrationPreset.entries.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.selectVibrationPreset(preset) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.vibrationPreset == preset,
                            onClick = { viewModel.selectVibrationPreset(preset) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(vibrationPresetLabel(preset), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Text(
                    text = stringResource(Res.string.settings_sound_preset),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                NotificationSoundPreset.entries.forEach { sound ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.selectSoundPreset(sound) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.soundPreset == sound,
                            onClick = { viewModel.selectSoundPreset(sound) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(soundPresetLabel(sound), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = { viewModel.sendTestNotification(testTitle, testBody) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(Res.string.btn_test_notification))
                }
            }
        }

        // Section 2: Night Mode (Quiet Hours)
        item {
            SettingsSectionCard(
                icon = Icons.Default.NightlightRound,
                title = stringResource(Res.string.settings_night_mode_section)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.settings_night_mode_enable),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = settings.nightModeEnabled,
                        onCheckedChange = viewModel::setNightModeEnabled
                    )
                }

                if (settings.nightModeEnabled) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        text = stringResource(Res.string.settings_night_interval),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editingStartTime = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "${stringResource(Res.string.settings_night_start)}: " +
                                    DurationFormatter.formatTimeOfDay(settings.nightStartHour, settings.nightStartMinute)
                            )
                        }
                        OutlinedButton(
                            onClick = { editingEndTime = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "${stringResource(Res.string.settings_night_end)}: " +
                                    DurationFormatter.formatTimeOfDay(settings.nightEndHour, settings.nightEndMinute)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(Res.string.settings_night_days),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    val dayLabels = listOf(
                        1 to stringResource(Res.string.day_mon),
                        2 to stringResource(Res.string.day_tue),
                        3 to stringResource(Res.string.day_wed),
                        4 to stringResource(Res.string.day_thu),
                        5 to stringResource(Res.string.day_fri),
                        6 to stringResource(Res.string.day_sat),
                        7 to stringResource(Res.string.day_sun)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        dayLabels.forEach { (isoDay, label) ->
                            val isSelected = isoDay in settings.nightActiveDaysIso
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.toggleNightDay(isoDay) },
                                label = { Text(label) },
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        text = stringResource(Res.string.settings_night_behavior),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    NightModeBehavior.entries.forEach { behavior ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.selectNightBehavior(behavior) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.nightModeBehavior == behavior,
                                onClick = { viewModel.selectNightBehavior(behavior) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(nightBehaviorLabel(behavior), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Section 3: Radar & Proximity Distance
        item {
            SettingsSectionCard(
                icon = Icons.Default.Radar,
                title = stringResource(Res.string.settings_radar_section)
            ) {
                Text(
                    text = stringResource(Res.string.settings_warning_radius, settings.warningRadiusKm),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = settings.warningRadiusKm.toFloat(),
                    onValueChange = { viewModel.setWarningRadiusKm(it.roundToInt()) },
                    valueRange = 10f..100f,
                    steps = 8
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.settings_radar_nearby_alerts),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    Switch(
                        checked = settings.radarNearbyAlertsEnabled,
                        onCheckedChange = viewModel::setRadarNearbyAlertsEnabled
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isKnowledgeBaseOpen = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.settings_kb_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(Res.string.settings_kb_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section 4: Background Diagnostics Checklist
        item {
            SettingsSectionCard(
                icon = Icons.Default.HealthAndSafety,
                title = stringResource(Res.string.settings_diagnostics_section)
            ) {
                DiagnosticRow(
                    icon = Icons.Default.Sync,
                    title = stringResource(Res.string.diag_service_title),
                    statusText = stringResource(
                        if (diagnostics.isServiceRunning) Res.string.diag_service_running else Res.string.diag_service_stopped
                    ),
                    isHealthy = diagnostics.isServiceRunning,
                    actionLabel = stringResource(Res.string.diag_service_start),
                    onAction = viewModel::startMonitoringService
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DiagnosticRow(
                    icon = Icons.Default.BatteryChargingFull,
                    title = stringResource(Res.string.diag_battery_title),
                    statusText = stringResource(
                        if (diagnostics.isBatteryOptimizationIgnored) Res.string.diag_battery_ok else Res.string.diag_battery_restricted
                    ),
                    isHealthy = diagnostics.isBatteryOptimizationIgnored,
                    actionLabel = stringResource(Res.string.diag_fix),
                    onAction = viewModel::requestIgnoreBatteryOptimizations
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DiagnosticRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(Res.string.diag_notifications_title),
                    statusText = stringResource(
                        if (diagnostics.hasNotificationPermission) Res.string.status_granted else Res.string.status_not_granted
                    ),
                    isHealthy = diagnostics.hasNotificationPermission,
                    actionLabel = stringResource(Res.string.diag_fix),
                    onAction = viewModel::requestNotificationPermission
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DiagnosticRow(
                    icon = Icons.Default.LocationOn,
                    title = stringResource(Res.string.diag_location_title),
                    statusText = stringResource(
                        if (diagnostics.hasFineLocationPermission) Res.string.status_granted else Res.string.status_not_granted
                    ),
                    isHealthy = diagnostics.hasFineLocationPermission,
                    actionLabel = stringResource(Res.string.diag_fix),
                    onAction = { viewModel.requestLocationPermission(includeBackground = false) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DiagnosticRow(
                    icon = Icons.Default.LocationOn,
                    title = stringResource(Res.string.diag_bg_location_title),
                    statusText = stringResource(
                        if (diagnostics.hasBackgroundLocationPermission) Res.string.status_granted else Res.string.status_not_granted
                    ),
                    isHealthy = diagnostics.hasBackgroundLocationPermission,
                    actionLabel = stringResource(Res.string.diag_fix),
                    onAction = { viewModel.requestLocationPermission(includeBackground = true) }
                )
            }
        }

        // Section 5: Data Sources & Attribution
        item {
            SettingsSectionCard(
                icon = Icons.Default.Info,
                title = stringResource(Res.string.settings_about_section)
            ) {
                Text(stringResource(Res.string.about_ukraine_alarm), style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = viewModel::openNeptunSite)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.about_neptun),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = stringResource(Res.string.about_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (editingStartTime) {
        TimePickerDialogWrapper(
            initialHour = settings.nightStartHour,
            initialMinute = settings.nightStartMinute,
            onDismiss = { editingStartTime = false },
            onConfirm = { h, m ->
                viewModel.updateNightStart(h, m)
                editingStartTime = false
            }
        )
    }

    if (editingEndTime) {
        TimePickerDialogWrapper(
            initialHour = settings.nightEndHour,
            initialMinute = settings.nightEndMinute,
            onDismiss = { editingEndTime = false },
            onConfirm = { h, m ->
                viewModel.updateNightEnd(h, m)
                editingEndTime = false
            }
        )
    }

    if (isKnowledgeBaseOpen) {
        ThreatKnowledgeBaseSheet(
            onDismiss = { isKnowledgeBaseOpen = false }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}

@Composable
private fun DiagnosticRow(
    icon: ImageVector,
    title: String,
    statusText: String,
    isHealthy: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    val tint = if (isHealthy) AlertColors.SafeGreen else AlertColors.WarningAmber
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = tint
            )
        }
        if (isHealthy) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AlertColors.SafeGreen)
        } else {
            FilledTonalButton(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogWrapper(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(Res.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.btn_cancel))
            }
        },
        text = {
            TimePicker(state = state)
        }
    )
}
