package com.onemanarmy.radar.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemanarmy.radar.domain.model.VibrationPreset
import com.onemanarmy.radar.platform.SystemDiagnosticsState
import com.onemanarmy.radar.resources.Res
import com.onemanarmy.radar.resources.btn_back
import com.onemanarmy.radar.resources.btn_configure_later
import com.onemanarmy.radar.resources.btn_finish
import com.onemanarmy.radar.resources.btn_grant
import com.onemanarmy.radar.resources.btn_next
import com.onemanarmy.radar.resources.btn_test
import com.onemanarmy.radar.resources.default_home_name
import com.onemanarmy.radar.resources.onboarding_btn_start
import com.onemanarmy.radar.resources.onboarding_comfort_desc
import com.onemanarmy.radar.resources.onboarding_comfort_title
import com.onemanarmy.radar.resources.onboarding_disclaimer_card_body
import com.onemanarmy.radar.resources.onboarding_disclaimer_card_title
import com.onemanarmy.radar.resources.onboarding_feature_alerts_desc
import com.onemanarmy.radar.resources.onboarding_feature_alerts_title
import com.onemanarmy.radar.resources.onboarding_feature_privacy_desc
import com.onemanarmy.radar.resources.onboarding_feature_privacy_title
import com.onemanarmy.radar.resources.onboarding_feature_radar_desc
import com.onemanarmy.radar.resources.onboarding_feature_radar_title
import com.onemanarmy.radar.resources.onboarding_location_desc
import com.onemanarmy.radar.resources.onboarding_location_selected
import com.onemanarmy.radar.resources.onboarding_location_title
import com.onemanarmy.radar.resources.onboarding_night_subtitle
import com.onemanarmy.radar.resources.onboarding_night_title
import com.onemanarmy.radar.resources.onboarding_option_gps_desc
import com.onemanarmy.radar.resources.onboarding_option_gps_title
import com.onemanarmy.radar.resources.onboarding_option_map_desc
import com.onemanarmy.radar.resources.onboarding_option_map_title
import com.onemanarmy.radar.resources.onboarding_permissions_desc
import com.onemanarmy.radar.resources.onboarding_permissions_title
import com.onemanarmy.radar.resources.onboarding_pick_point_confirm
import com.onemanarmy.radar.resources.onboarding_step_indicator
import com.onemanarmy.radar.resources.onboarding_tag_manual
import com.onemanarmy.radar.resources.onboarding_tag_recommended
import com.onemanarmy.radar.resources.onboarding_vibration_label
import com.onemanarmy.radar.resources.onboarding_vibration_subtitle
import com.onemanarmy.radar.resources.onboarding_welcome_desc
import com.onemanarmy.radar.resources.onboarding_welcome_title
import com.onemanarmy.radar.resources.perm_battery_desc
import com.onemanarmy.radar.resources.perm_battery_title
import com.onemanarmy.radar.resources.perm_notifications_desc
import com.onemanarmy.radar.resources.perm_notifications_title
import com.onemanarmy.radar.resources.points_gps_ew_warning
import com.onemanarmy.radar.resources.points_gps_subtitle_detecting
import com.onemanarmy.radar.resources.status_granted
import com.onemanarmy.radar.resources.status_not_granted
import com.onemanarmy.radar.ui.common.vibrationPresetLabel
import com.onemanarmy.radar.ui.components.CrosshairMapPickerOverlay
import com.onemanarmy.radar.ui.theme.AlertColors
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val TOTAL_STEPS = 4

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
            OnboardingStepperBar(currentStep = uiState.stepIndex)

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedContent(
                targetState = uiState.stepIndex,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState >= initialState
                    val enter = slideInHorizontally(
                        animationSpec = tween(260)
                    ) { width -> if (forward) width / 3 else -width / 3 } + fadeIn(tween(240))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(220)
                    ) { width -> if (forward) -width / 3 else width / 3 } + fadeOut(tween(180))
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "onboarding_step_transition"
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
private fun OnboardingStepperBar(currentStep: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
            ) {
                Text(
                    text = stringResource(Res.string.onboarding_step_indicator, currentStep + 1),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(TOTAL_STEPS) { index ->
                    val isCurrent = index == currentStep
                    val isDone = index < currentStep
                    val dotColor by animateColorAsState(
                        targetValue = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isDone -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = if (isCurrent) 18.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(TOTAL_STEPS) { index ->
                val isCompletedOrCurrent = index <= currentStep
                val segmentColor by animateColorAsState(
                    targetValue = if (isCompletedOrCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(segmentColor)
                )
            }
        }
    }
}

@Composable
private fun OnboardingStepHeader(
    icon: ImageVector,
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OnboardingBottomNav(
    backLabel: String,
    primaryLabel: String,
    primaryIcon: ImageVector,
    onBack: () -> Unit,
    onPrimary: () -> Unit,
    secondaryAction: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (secondaryAction != null) {
            Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                secondaryAction()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f))
            ) {
                Text(backLabel)
            }
            Button(
                onClick = onPrimary,
                modifier = Modifier.weight(1.35f).height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(primaryLabel)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = primaryIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            RadarShieldHero()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = stringResource(Res.string.onboarding_welcome_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(Res.string.onboarding_welcome_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    WelcomeFeatureRow(
                        icon = Icons.Default.Notifications,
                        accent = MaterialTheme.colorScheme.primary,
                        title = stringResource(Res.string.onboarding_feature_alerts_title),
                        subtitle = stringResource(Res.string.onboarding_feature_alerts_desc)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    WelcomeFeatureRow(
                        icon = Icons.Default.Radar,
                        accent = AlertColors.AlertRed,
                        title = stringResource(Res.string.onboarding_feature_radar_title),
                        subtitle = stringResource(Res.string.onboarding_feature_radar_desc)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    WelcomeFeatureRow(
                        icon = Icons.Default.Lock,
                        accent = AlertColors.SafeGreen,
                        title = stringResource(Res.string.onboarding_feature_privacy_title),
                        subtitle = stringResource(Res.string.onboarding_feature_privacy_desc)
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = AlertColors.WarningAmber.copy(alpha = 0.08f)
                ),
                border = BorderStroke(1.dp, AlertColors.WarningAmber.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(AlertColors.WarningAmber.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AlertColors.WarningAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(Res.string.onboarding_disclaimer_card_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AlertColors.WarningOnContainerDark
                        )
                        Text(
                            text = stringResource(Res.string.onboarding_disclaimer_card_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = stringResource(Res.string.onboarding_btn_start),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RadarShieldHero() {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val ringColor = Color(0xFF4B9AF4)

    Box(
        modifier = Modifier.size(132.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f
            val outerRingRadius = maxRadius * 0.92f

            // Soft radial glow that blends naturally into the onboarding background
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to primary.copy(alpha = 0.28f),
                    0.55f to primary.copy(alpha = 0.10f),
                    1.0f to Color.Transparent,
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )

            // Balanced radar sweep wedge (-55 deg to +15 deg)
            val startAngleRad = ((-55.0 * PI) / 180.0).toFloat()
            val endAngleRad = ((15.0 * PI) / 180.0).toFloat()
            val rayTip = Offset(
                center.x + outerRingRadius * cos(startAngleRad),
                center.y + outerRingRadius * sin(startAngleRad)
            )
            val trailTip = Offset(
                center.x + outerRingRadius * cos(endAngleRad),
                center.y + outerRingRadius * sin(endAngleRad)
            )

            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.34f),
                        primary.copy(alpha = 0.06f)
                    ),
                    start = rayTip,
                    end = trailTip
                ),
                startAngle = -55f,
                sweepAngle = 70f,
                useCenter = true,
                topLeft = Offset(
                    center.x - outerRingRadius,
                    center.y - outerRingRadius
                ),
                size = Size(outerRingRadius * 2f, outerRingRadius * 2f)
            )

            // Subtle leading-edge sweep ray
            drawLine(
                color = Color(0xFF7CB8FA).copy(alpha = 0.52f),
                start = center,
                end = rayTip,
                strokeWidth = 1.4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Medium-contrast concentric radar rings
            listOf(
                0.92f to 0.30f,
                0.70f to 0.42f,
                0.50f to 0.52f
            ).forEach { (factor, alpha) ->
                drawCircle(
                    color = ringColor.copy(alpha = alpha),
                    radius = maxRadius * factor,
                    center = center,
                    style = Stroke(width = 1.4.dp.toPx())
                )
            }

            // Blip accents with soft halos
            val redCenter = Offset(center.x + maxRadius * 0.54f, center.y - maxRadius * 0.42f)
            drawCircle(
                color = AlertColors.AlertRed.copy(alpha = 0.22f),
                radius = 5.4.dp.toPx(),
                center = redCenter
            )
            drawCircle(
                color = AlertColors.AlertRed,
                radius = 3.5.dp.toPx(),
                center = redCenter
            )

            val greenCenter = Offset(center.x - maxRadius * 0.62f, center.y + maxRadius * 0.34f)
            drawCircle(
                color = AlertColors.SafeGreen.copy(alpha = 0.20f),
                radius = 4.8.dp.toPx(),
                center = greenCenter
            )
            drawCircle(
                color = AlertColors.SafeGreen,
                radius = 3.0.dp.toPx(),
                center = greenCenter
            )
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(surfaceColor)
                .border(1.6.dp, primary.copy(alpha = 0.72f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = Color(0xFF479AF2),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun WelcomeFeatureRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    val allGranted = diagnostics.hasNotificationPermission && diagnostics.isBatteryOptimizationIgnored

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OnboardingStepHeader(
                icon = Icons.Default.Notifications,
                title = stringResource(Res.string.onboarding_permissions_title),
                description = stringResource(Res.string.onboarding_permissions_desc)
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

        OnboardingBottomNav(
            backLabel = stringResource(Res.string.btn_back),
            primaryLabel = stringResource(Res.string.btn_next),
            primaryIcon = Icons.AutoMirrored.Filled.ArrowForward,
            onBack = onBack,
            onPrimary = onNext,
            secondaryAction = if (!allGranted) {
                {
                    TextButton(onClick = onNext) {
                        Text(
                            text = stringResource(Res.string.btn_configure_later),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                null
            }
        )
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
    val accent = if (isGranted) AlertColors.SafeGreen else MaterialTheme.colorScheme.primary
    val statusColor = if (isGranted) AlertColors.SafeGreen else AlertColors.WarningAmber
    val borderColor by animateColorAsState(
        targetValue = if (isGranted) {
            AlertColors.SafeGreen.copy(alpha = 0.48f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    val containerColor by animateColorAsState(
        targetValue = if (isGranted) {
            AlertColors.SafeGreen.copy(alpha = 0.06f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = if (!isGranted) {
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onGrant)
        } else {
            Modifier.fillMaxWidth()
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = stringResource(
                                if (isGranted) Res.string.status_granted else Res.string.status_not_granted
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AlertColors.SafeGreen,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                FilledTonalButton(
                    onClick = onGrant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
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
            OnboardingStepHeader(
                icon = Icons.Default.LocationOn,
                title = stringResource(Res.string.onboarding_location_title),
                description = stringResource(Res.string.onboarding_location_desc)
            )

            Spacer(modifier = Modifier.height(2.dp))

            LocationOptionCard(
                icon = Icons.Default.MyLocation,
                title = stringResource(Res.string.onboarding_option_gps_title),
                description = stringResource(Res.string.onboarding_option_gps_desc),
                tag = stringResource(Res.string.onboarding_tag_recommended),
                tagAccent = MaterialTheme.colorScheme.primary,
                selected = uiState.gpsEnabled,
                onClick = onSelectGps
            )

            if (uiState.gpsEnabled) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = AlertColors.WarningAmberContainerDark.copy(alpha = 0.72f)
                    ),
                    border = BorderStroke(1.dp, AlertColors.WarningAmber.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AlertColors.WarningAmber,
                            modifier = Modifier.size(18.dp).padding(top = 1.dp)
                        )
                        Text(
                            text = stringResource(Res.string.points_gps_ew_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = AlertColors.WarningOnContainerDark
                        )
                    }
                }
            }

            LocationOptionCard(
                icon = Icons.Default.Map,
                title = stringResource(Res.string.onboarding_option_map_title),
                description = stringResource(Res.string.onboarding_option_map_desc),
                tag = stringResource(Res.string.onboarding_tag_manual),
                tagAccent = MaterialTheme.colorScheme.secondary,
                selected = uiState.pendingPinLocation != null,
                onClick = onOpenMapPicker
            )

            if (uiState.isResolvingLocation) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(14.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(Res.string.points_gps_subtitle_detecting),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else if (!uiState.selectedModeLabel.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = AlertColors.SafeGreen.copy(alpha = 0.10f)
                    ),
                    border = BorderStroke(1.dp, AlertColors.SafeGreen.copy(alpha = 0.42f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AlertColors.SafeGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(Res.string.onboarding_location_selected, uiState.selectedModeLabel),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = AlertColors.SafeOnContainerDark
                        )
                    }
                }
            }
        }

        OnboardingBottomNav(
            backLabel = stringResource(Res.string.btn_back),
            primaryLabel = stringResource(Res.string.btn_next),
            primaryIcon = Icons.AutoMirrored.Filled.ArrowForward,
            onBack = onBack,
            onPrimary = onNext
        )
    }
}

@Composable
private fun LocationOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    tag: String,
    tagAccent: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = tagAccent.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = tagAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SelectionCircleIndicator(selected = selected)
        }
    }
}

@Composable
private fun SelectionCircleIndicator(selected: Boolean) {
    val fill by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    val ringColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, ringColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp)
            )
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
    val nightBorderColor by animateColorAsState(
        targetValue = if (uiState.nightModeEnabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OnboardingStepHeader(
                icon = Icons.Default.Tune,
                title = stringResource(Res.string.onboarding_comfort_title),
                description = stringResource(Res.string.onboarding_comfort_desc)
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, nightBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onToggleNightMode(!uiState.nightModeEnabled) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NightlightRound,
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
                            text = stringResource(Res.string.onboarding_night_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(Res.string.onboarding_night_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.nightModeEnabled,
                        onCheckedChange = onToggleNightMode
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
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
                                text = stringResource(Res.string.onboarding_vibration_label),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(Res.string.onboarding_vibration_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = onTestVibration,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(Res.string.btn_test))
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VibrationPreset.entries.forEach { preset ->
                            VibrationPresetOptionTile(
                                preset = preset,
                                label = vibrationPresetLabel(preset),
                                selected = uiState.selectedVibrationPreset == preset,
                                onClick = { onSelectPreset(preset) }
                            )
                        }
                    }
                }
            }
        }

        OnboardingBottomNav(
            backLabel = stringResource(Res.string.btn_back),
            primaryLabel = stringResource(Res.string.btn_finish),
            primaryIcon = Icons.Default.Check,
            onBack = onBack,
            onPrimary = onFinish
        )
    }
}

@Composable
private fun VibrationPresetOptionTile(
    preset: VibrationPreset,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    val fillColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    val strengthLevel = when (preset) {
        VibrationPreset.NONE -> 0
        VibrationPreset.ULTRA_SOFT -> 1
        VibrationPreset.DELICATE_DOUBLE -> 2
        VibrationPreset.STANDARD -> 3
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = fillColor,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SelectionCircleIndicator(selected = selected)

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                repeat(3) { index ->
                    val active = index < strengthLevel
                    val barHeight = when (index) {
                        0 -> 8.dp
                        1 -> 12.dp
                        else -> 16.dp
                    }
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (active) {
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                }
                            )
                    )
                }
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
