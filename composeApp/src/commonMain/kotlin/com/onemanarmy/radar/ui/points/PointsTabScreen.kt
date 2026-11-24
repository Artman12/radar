package com.onemanarmy.radar.ui.points

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemanarmy.radar.domain.logic.DurationFormatter
import com.onemanarmy.radar.domain.logic.PointScheduleEvaluator
import com.onemanarmy.radar.domain.model.PointStatus
import com.onemanarmy.radar.domain.model.SavedPoint
import com.onemanarmy.radar.resources.Res
import com.onemanarmy.radar.resources.btn_save
import com.onemanarmy.radar.resources.day_fri
import com.onemanarmy.radar.resources.day_mon
import com.onemanarmy.radar.resources.day_sat
import com.onemanarmy.radar.resources.day_sun
import com.onemanarmy.radar.resources.day_thu
import com.onemanarmy.radar.resources.day_tue
import com.onemanarmy.radar.resources.day_wed
import com.onemanarmy.radar.resources.default_home_name
import com.onemanarmy.radar.resources.points_action_delete
import com.onemanarmy.radar.resources.points_action_rename
import com.onemanarmy.radar.resources.points_action_reposition
import com.onemanarmy.radar.resources.points_add_button
import com.onemanarmy.radar.resources.points_empty_hint
import com.onemanarmy.radar.resources.points_gps_ew_warning
import com.onemanarmy.radar.resources.points_gps_subtitle_detecting
import com.onemanarmy.radar.resources.points_gps_subtitle_disabled
import com.onemanarmy.radar.resources.points_gps_subtitle_enabled
import com.onemanarmy.radar.resources.points_gps_title
import com.onemanarmy.radar.resources.points_notify_switch
import com.onemanarmy.radar.resources.points_saved_header
import com.onemanarmy.radar.resources.points_schedule_24_7
import com.onemanarmy.radar.resources.points_schedule_active
import com.onemanarmy.radar.resources.points_schedule_configure
import com.onemanarmy.radar.resources.points_schedule_days_daily
import com.onemanarmy.radar.resources.points_schedule_days_weekdays
import com.onemanarmy.radar.resources.points_schedule_days_weekends
import com.onemanarmy.radar.resources.points_schedule_outside_badge
import com.onemanarmy.radar.resources.points_schedule_paused
import com.onemanarmy.radar.resources.points_schedule_title
import com.onemanarmy.radar.resources.points_status_alert
import com.onemanarmy.radar.resources.points_status_clear
import com.onemanarmy.radar.ui.components.AddLocationBottomSheet
import com.onemanarmy.radar.ui.components.CrosshairMapPickerOverlay
import com.onemanarmy.radar.ui.components.PointScheduleBottomSheet
import com.onemanarmy.radar.ui.components.RenameLocationDialog
import com.onemanarmy.radar.ui.theme.AlertColors
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PointsTabScreen(
    viewModel: PointsViewModel = koinViewModel(),
    listState: LazyListState = rememberLazyListState()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isAddCrosshairOpen by remember { mutableStateOf(false) }

    // Crosshair map overlay when adding or repositioning a point.
    val repositioning = uiState.repositioningPoint
    if (repositioning != null || isAddCrosshairOpen) {
        val initLat = repositioning?.latitude ?: 49.02
        val initLon = repositioning?.longitude ?: 31.48
        val defaultPointName = stringResource(Res.string.default_home_name)

        CrosshairMapPickerOverlay(
            initialLat = initLat,
            initialLon = initLon,
            initialZoom = if (repositioning != null) 11.0 else 6.3,
            confirmLabel = stringResource(Res.string.btn_save),
            onCancel = {
                viewModel.dismissReposition()
                isAddCrosshairOpen = false
            },
            onConfirm = { lat, lon ->
                if (repositioning != null) {
                    viewModel.confirmReposition(repositioning, lat, lon)
                } else {
                    viewModel.addLocation("", lat, lon, defaultPointName)
                    isAddCrosshairOpen = false
                }
            }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openAddSheet,
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    Text(
                        text = stringResource(Res.string.points_add_button),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GpsTrackingCard(
                    enabled = uiState.settings.gpsTrackingEnabled,
                    isDetecting = uiState.isDetectingGps,
                    regionName = uiState.settings.gpsPointRegionName,
                    gpsStatus = uiState.gpsStatus,
                    onToggle = viewModel::toggleGpsTracking
                )
            }

            item {
                Text(
                    text = stringResource(Res.string.points_saved_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
            }

            if (uiState.savedStatuses.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = stringResource(Res.string.points_empty_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(uiState.savedStatuses, key = { it.point.id }) { status ->
                    SavedPointCard(
                        status = status,
                        onToggleNotify = { enabled ->
                            viewModel.togglePointNotification(status.point, enabled)
                        },
                        onConfigureSchedule = {
                            viewModel.openScheduleEditor(status.point)
                        },
                        onRename = { viewModel.startRename(status.point) },
                        onReposition = { viewModel.startReposition(status.point) },
                        onDelete = { viewModel.deletePoint(status.point.id) }
                    )
                }
            }
        }
    }

    if (uiState.isAddSheetOpen) {
        AddLocationBottomSheet(
            searchQuery = uiState.searchQuery,
            searchResults = uiState.searchResults,
            isSearching = uiState.isSearching,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onSearchSubmit = viewModel::searchAddress,
            onPickOnMap = {
                viewModel.closeAddSheet()
                isAddCrosshairOpen = true
            },
            onSelectResult = { name, place ->
                viewModel.addLocationFromPlace(name, place)
            },
            onDismiss = viewModel::closeAddSheet
        )
    }

    uiState.renamingPoint?.let { point ->
        RenameLocationDialog(
            point = point,
            onDismiss = viewModel::dismissRename,
            onConfirm = { newName -> viewModel.confirmRename(point, newName) }
        )
    }

    uiState.editingSchedulePoint?.let { point ->
        PointScheduleBottomSheet(
            point = point,
            onDismiss = viewModel::dismissScheduleEditor,
            onSave = { scheduleEnabled, startH, startM, endH, endM, days ->
                viewModel.savePointSchedule(
                    point = point,
                    scheduleEnabled = scheduleEnabled,
                    startHour = startH,
                    startMinute = startM,
                    endHour = endH,
                    endMinute = endM,
                    activeDaysIso = days
                )
            }
        )
    }
}

@Composable
private fun GpsTrackingCard(
    enabled: Boolean,
    isDetecting: Boolean,
    regionName: String?,
    gpsStatus: PointStatus?,
    onToggle: (Boolean) -> Unit
) {
    val borderColor = if (enabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        border = BorderStroke(1.dp, borderColor)
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
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.points_gps_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (enabled && gpsStatus != null) {
                            StatusBadge(isAlertActive = gpsStatus.isAlertActive)
                        }
                    }
                    val subtitle = when {
                        !enabled -> stringResource(Res.string.points_gps_subtitle_disabled)
                        isDetecting || regionName.isNullOrBlank() -> stringResource(Res.string.points_gps_subtitle_detecting)
                        else -> stringResource(Res.string.points_gps_subtitle_enabled, regionName)
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            }

            if (enabled) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AlertColors.WarningAmberContainerDark.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, AlertColors.WarningAmber.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AlertColors.WarningAmber,
                            modifier = Modifier.size(16.dp).padding(top = 1.dp)
                        )
                        Text(
                            text = stringResource(Res.string.points_gps_ew_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = AlertColors.WarningOnContainerDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedPointCard(
    status: PointStatus,
    onToggleNotify: (Boolean) -> Unit,
    onConfigureSchedule: () -> Unit,
    onRename: () -> Unit,
    onReposition: () -> Unit,
    onDelete: () -> Unit
) {
    val point: SavedPoint = status.point
    val isScheduleActiveNow = remember(point) {
        PointScheduleEvaluator.isMonitoringActive(point)
    }
    val statusTint = if (status.isAlertActive) AlertColors.AlertRed else AlertColors.SafeGreen
    val borderColor = if (status.isAlertActive) {
        AlertColors.AlertRed.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, borderColor)
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(statusTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (status.isAlertActive) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = statusTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = point.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = point.regionName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusBadge(isAlertActive = status.isAlertActive)
                    if (point.notifyEnabled && point.scheduleEnabled && !isScheduleActiveNow) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = stringResource(Res.string.points_schedule_outside_badge),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(Res.string.points_notify_switch),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = point.notifyEnabled,
                    onCheckedChange = onToggleNotify
                )
            }

            if (point.notifyEnabled) {
                val scheduleSummary = if (!point.scheduleEnabled) {
                    stringResource(Res.string.points_schedule_24_7)
                } else {
                    val start = DurationFormatter.formatTimeOfDay(
                        point.scheduleStartHour,
                        point.scheduleStartMinute
                    )
                    val end = DurationFormatter.formatTimeOfDay(
                        point.scheduleEndHour,
                        point.scheduleEndMinute
                    )
                    val daysText = formatScheduleDaysSummary(point.scheduleActiveDaysIso)
                    "$start – $end • $daysText"
                }

                Surface(
                    onClick = onConfigureSchedule,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        if (point.scheduleEnabled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (point.scheduleEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(Res.string.points_schedule_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (point.scheduleEnabled) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isScheduleActiveNow) {
                                            AlertColors.SafeGreenContainerDark
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (isScheduleActiveNow) {
                                                AlertColors.SafeGreen.copy(alpha = 0.45f)
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            }
                                        )
                                    ) {
                                        Text(
                                            text = stringResource(
                                                if (isScheduleActiveNow) {
                                                    Res.string.points_schedule_active
                                                } else {
                                                    Res.string.points_schedule_paused
                                                }
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isScheduleActiveNow) {
                                                AlertColors.SafeOnContainerDark
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = scheduleSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(Res.string.points_schedule_configure),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onRename,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(Res.string.points_action_rename),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Surface(
                    onClick = onReposition,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.OpenWith,
                            contentDescription = stringResource(Res.string.points_action_reposition),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Surface(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = AlertColors.AlertRedContainerDark.copy(alpha = 0.7f),
                    contentColor = AlertColors.AlertRed,
                    border = BorderStroke(1.dp, AlertColors.AlertRed.copy(alpha = 0.50f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.points_action_delete),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun formatScheduleDaysSummary(daysIso: Set<Int>): String {
    return when {
        daysIso.size == 7 -> stringResource(Res.string.points_schedule_days_daily)
        daysIso == setOf(1, 2, 3, 4, 5) -> stringResource(Res.string.points_schedule_days_weekdays)
        daysIso == setOf(6, 7) -> stringResource(Res.string.points_schedule_days_weekends)
        else -> {
            val labels = mapOf(
                1 to stringResource(Res.string.day_mon),
                2 to stringResource(Res.string.day_tue),
                3 to stringResource(Res.string.day_wed),
                4 to stringResource(Res.string.day_thu),
                5 to stringResource(Res.string.day_fri),
                6 to stringResource(Res.string.day_sat),
                7 to stringResource(Res.string.day_sun)
            )
            daysIso.sorted().mapNotNull { labels[it] }.joinToString(", ")
        }
    }
}

@Composable
private fun StatusBadge(isAlertActive: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isAlertActive) AlertColors.AlertRedContainerDark else AlertColors.SafeGreenContainerDark,
        border = BorderStroke(
            1.dp,
            if (isAlertActive) AlertColors.AlertRed.copy(alpha = 0.48f) else AlertColors.SafeGreen.copy(alpha = 0.45f)
        )
    ) {
        Text(
            text = stringResource(
                if (isAlertActive) Res.string.points_status_alert else Res.string.points_status_clear
            ),
            color = if (isAlertActive) AlertColors.AlertOnContainerDark else AlertColors.SafeOnContainerDark,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
