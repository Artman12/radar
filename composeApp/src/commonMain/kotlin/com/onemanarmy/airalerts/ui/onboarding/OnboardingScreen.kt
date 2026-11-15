package com.onemanarmy.airalerts.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.onemanarmy.airalerts.domain.model.VibrationPreset
import com.onemanarmy.airalerts.platform.SystemDiagnosticsState
import com.onemanarmy.airalerts.resources.Res
import com.onemanarmy.airalerts.resources.btn_back
import com.onemanarmy.airalerts.resources.btn_configure_later
import com.onemanarmy.airalerts.resources.btn_finish
import com.onemanarmy.airalerts.resources.btn_grant
import com.onemanarmy.airalerts.resources.btn_next
import com.onemanarmy.airalerts.resources.btn_test
import com.onemanarmy.airalerts.resources.default_home_name
import com.onemanarmy.airalerts.resources.onboarding_btn_start
import com.onemanarmy.airalerts.resources.onboarding_comfort_desc
import com.onemanarmy.airalerts.resources.onboarding_comfort_title
import com.onemanarmy.airalerts.resources.onboarding_disclaimer_card_body
import com.onemanarmy.airalerts.resources.onboarding_disclaimer_card_title
import com.onemanarmy.airalerts.resources.onboarding_location_desc
import com.onemanarmy.airalerts.resources.onboarding_location_selected
import com.onemanarmy.airalerts.resources.onboarding_location_title
import com.onemanarmy.airalerts.resources.onboarding_night_toggle
import com.onemanarmy.airalerts.resources.onboarding_option_gps_desc
import com.onemanarmy.airalerts.resources.onboarding_option_gps_title
import com.onemanarmy.airalerts.resources.onboarding_option_map_desc
import com.onemanarmy.airalerts.resources.onboarding_option_map_title
import com.onemanarmy.airalerts.resources.onboarding_permissions_desc
import com.onemanarmy.airalerts.resources.onboarding_permissions_title
import com.onemanarmy.airalerts.resources.onboarding_pick_point_confirm
import com.onemanarmy.airalerts.resources.onboarding_step_indicator
import com.onemanarmy.airalerts.resources.onboarding_vibration_label
import com.onemanarmy.airalerts.resources.onboarding_welcome_desc
import com.onemanarmy.airalerts.resources.onboarding_welcome_title
import com.onemanarmy.airalerts.resources.perm_battery_desc
import com.onemanarmy.airalerts.resources.perm_battery_title
import com.onemanarmy.airalerts.resources.perm_notifications_desc
import com.onemanarmy.airalerts.resources.perm_notifications_title
import com.onemanarmy.airalerts.resources.points_gps_subtitle_detecting
import com.onemanarmy.airalerts.resources.status_granted
import com.onemanarmy.airalerts.resources.status_not_granted
import com.onemanarmy.airalerts.ui.common.vibrationPresetLabel
import com.onemanarmy.airalerts.ui.components.CrosshairMapPickerOverlay
import com.onemanarmy.airalerts.ui.theme.AlertColors
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnosticsState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh permission and battery states whenever returning from system dialogs.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDiagnostics()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (uiState.isShowingMapPicker) {
        OnboardingMapPickerOverlay(
            isLoading = uiState.isResolvingLocation,
            defaultHomeName = stringResource(Res.string.default_home_name),
            onCancel = viewModel::closeMapPicker,
            onConfirm = { lat, lon, homeName ->
                viewModel.confirmMapPin(lat, lon, homeName)
            }
        )
        return
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(Res.string.onboarding_step_indicator, uiState.stepIndex + 1),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (uiState.stepIndex + 1) / 4f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.height(20.dp))

            AnimatedContent(
                targetState = uiState.stepIndex,
                modifier = Modifier.weight(1f)
            ) { step ->
                when (step) {
                    0 -> WelcomeStep(onStart = viewModel::startSetup)
                    1 -> PermissionsStep(
                        diagnostics = diagnostics,
                        onRequestNotifications = viewModel::requestNotificationPermission,
                        onRequestBattery = viewModel::requestBatteryExemption,
                        onNext = viewModel::nextStep,
                        onBack = viewModel::previousStep
                    )
                    2 -> LocationStep(
                        uiState = uiState,
                        onSelectGps = viewModel::selectGpsLocation,
                        onOpenMapPicker = viewModel::openMapPicker,
                        onNext = viewModel::nextStep,
                        onBack = viewModel::previousStep
                    )
                    else -> ComfortStep(
                        uiState = uiState,
                        onToggleNightMode = viewModel::setNightModeEnabled,
                        onSelectPreset = viewModel::selectVibrationPreset,
                        onTestVibration = viewModel::previewVibration,
                        onBack = viewModel::previousStep,
                        onFinish = viewModel::finishOnboarding
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }

            Text(
                text = stringResource(Res.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(Res.string.onboarding_welcome_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(Res.string.onboarding_disclaimer_card_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(Res.string.onboarding_disclaimer_card_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(52.dp)
        ) {
            Text(text = stringResource(Res.string.onboarding_btn_start))
        }
    }
}

@Composable
private fun PermissionsStep(
    diagnostics: SystemDiagnosticsState,
    onRequestNotifications: () -> Unit,
    onRequestBattery: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.onboarding_permissions_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(Res.string.onboarding_permissions_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PermissionStatusCard(
                icon = Icons.Default.Notifications,
                title = stringResource(Res.string.perm_notifications_title),
                description = stringResource(Res.string.perm_notifications_desc),
                isGranted = diagnostics.hasNotificationPermission,
                onGrant = onRequestNotifications
            )

            PermissionStatusCard(
                icon = Icons.Default.BatteryChargingFull,
                title = stringResource(Res.string.perm_battery_title),
                description = stringResource(Res.string.perm_battery_desc),
                isGranted = diagnostics.isBatteryOptimizationIgnored,
                onGrant = onRequestBattery
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(stringResource(Res.string.btn_back))
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(stringResource(Res.string.btn_next))
                }
            }
            TextButton(
                onClick = onNext,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(Res.string.btn_configure_later))
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            1.dp,
            if (isGranted) AlertColors.SafeGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        (if (isGranted) AlertColors.SafeGreen else MaterialTheme.colorScheme.primary).copy(alpha = 0.14f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) AlertColors.SafeGreen else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isGranted) stringResource(Res.string.status_granted) else stringResource(Res.string.status_not_granted),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isGranted) AlertColors.SafeGreen else AlertColors.WarningAmber,
                    fontWeight = FontWeight.Medium
                )
            }
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AlertColors.SafeGreen
                )
            } else {
                FilledTonalButton(onClick = onGrant, shape = RoundedCornerShape(10.dp)) {
                    Text(stringResource(Res.string.btn_grant))
                }
            }
        }
    }
}

@Composable
private fun LocationStep(
    uiState: OnboardingUiState,
    onSelectGps: () -> Unit,
    onOpenMapPicker: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(Res.string.onboarding_location_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(Res.string.onboarding_location_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LocationOptionCard(
                icon = Icons.Default.MyLocation,
                title = stringResource(Res.string.onboarding_option_gps_title),
                description = stringResource(Res.string.onboarding_option_gps_desc),
                selected = uiState.gpsEnabled,
                onClick = onSelectGps
            )

            LocationOptionCard(
                icon = Icons.Default.Map,
                title = stringResource(Res.string.onboarding_option_map_title),
                description = stringResource(Res.string.onboarding_option_map_desc),
                selected = uiState.pendingPinLocation != null,
                onClick = onOpenMapPicker
            )

            if (uiState.isResolvingLocation) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(Res.string.points_gps_subtitle_detecting),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (!uiState.selectedModeLabel.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(Res.string.onboarding_location_selected, uiState.selectedModeLabel),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text(stringResource(Res.string.btn_back))
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text(stringResource(Res.string.btn_next))
            }
        }
    }
}

@Composable
private fun LocationOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ComfortStep(
    uiState: OnboardingUiState,
    onToggleNightMode: (Boolean) -> Unit,
    onSelectPreset: (VibrationPreset) -> Unit,
    onTestVibration: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.onboarding_comfort_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(Res.string.onboarding_comfort_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.NightlightRound, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(Res.string.onboarding_night_toggle),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Switch(
                        checked = uiState.nightModeEnabled,
                        onCheckedChange = onToggleNightMode
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = stringResource(Res.string.onboarding_vibration_label),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        FilledTonalButton(onClick = onTestVibration, shape = RoundedCornerShape(10.dp)) {
                            Text(stringResource(Res.string.btn_test))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    VibrationPreset.entries.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectPreset(preset) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.selectedVibrationPreset == preset,
                                onClick = { onSelectPreset(preset) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = vibrationPresetLabel(preset),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text(stringResource(Res.string.btn_back))
            }
            Button(
                onClick = onFinish,
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text(stringResource(Res.string.btn_finish))
            }
        }
    }
}

@Composable
private fun OnboardingMapPickerOverlay(
    isLoading: Boolean,
    defaultHomeName: String,
    onCancel: () -> Unit,
    onConfirm: (Double, Double, String) -> Unit
) {
    CrosshairMapPickerOverlay(
        initialLat = 48.5132,
        initialLon = 32.2597,
        initialZoom = 6.3,
        confirmLabel = stringResource(Res.string.onboarding_pick_point_confirm),
        isLoading = isLoading,
        applySafeDrawingInsets = true,
        onCancel = onCancel,
        onConfirm = { lat, lon -> onConfirm(lat, lon, defaultHomeName) }
    )
}
