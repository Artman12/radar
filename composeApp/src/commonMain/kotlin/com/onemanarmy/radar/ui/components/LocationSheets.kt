package com.onemanarmy.radar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onemanarmy.radar.domain.logic.DurationFormatter
import com.onemanarmy.radar.domain.model.ExplosionWithProximity
import com.onemanarmy.radar.domain.model.PointStatus
import com.onemanarmy.radar.domain.model.SavedPoint
import com.onemanarmy.radar.domain.model.ThreatWithProximity
import com.onemanarmy.radar.platform.GeocodedAddress
import com.onemanarmy.radar.resources.Res
import com.onemanarmy.radar.resources.btn_cancel
import com.onemanarmy.radar.resources.btn_pick_on_map
import com.onemanarmy.radar.resources.btn_save
import com.onemanarmy.radar.resources.btn_search
import com.onemanarmy.radar.resources.explosion_details_age_just_now
import com.onemanarmy.radar.resources.explosion_details_age_minutes
import com.onemanarmy.radar.resources.explosion_details_approx_note
import com.onemanarmy.radar.resources.explosion_details_distance
import com.onemanarmy.radar.resources.explosion_details_district
import com.onemanarmy.radar.resources.explosion_details_source
import com.onemanarmy.radar.resources.explosion_details_source_nextalert
import com.onemanarmy.radar.resources.explosion_details_time
import com.onemanarmy.radar.resources.explosion_details_title_default
import com.onemanarmy.radar.resources.field_location_name
import com.onemanarmy.radar.resources.field_search_address
import com.onemanarmy.radar.resources.just_now
import com.onemanarmy.radar.resources.point_details_alert_types
import com.onemanarmy.radar.resources.point_details_duration
import com.onemanarmy.radar.resources.point_details_last_check
import com.onemanarmy.radar.resources.point_details_no_active
import com.onemanarmy.radar.resources.point_details_region
import com.onemanarmy.radar.resources.point_details_status
import com.onemanarmy.radar.resources.points_status_alert
import com.onemanarmy.radar.resources.points_status_clear
import com.onemanarmy.radar.resources.search_no_results
import com.onemanarmy.radar.resources.sheet_add_location_title
import com.onemanarmy.radar.resources.sheet_rename_location_title
import com.onemanarmy.radar.resources.threat_details_age_hours_minutes
import com.onemanarmy.radar.resources.threat_details_approx_note
import com.onemanarmy.radar.resources.threat_details_distance
import com.onemanarmy.radar.resources.threat_details_distance_value
import com.onemanarmy.radar.resources.threat_details_heading
import com.onemanarmy.radar.resources.threat_details_heading_unknown
import com.onemanarmy.radar.resources.threat_details_inside_radius
import com.onemanarmy.radar.resources.threat_details_inside_radius_zero
import com.onemanarmy.radar.resources.threat_details_open_kb
import com.onemanarmy.radar.resources.threat_details_source
import com.onemanarmy.radar.resources.threat_details_speed
import com.onemanarmy.radar.resources.threat_details_type
import com.onemanarmy.radar.resources.threat_details_uncertainty
import com.onemanarmy.radar.resources.threat_details_updated
import com.onemanarmy.radar.ui.common.alertTypeLabel
import com.onemanarmy.radar.ui.common.threatDataSourceFullLabel
import com.onemanarmy.radar.ui.common.threatTypeLabel
import com.onemanarmy.radar.ui.theme.AlertColors
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLocationBottomSheet(
    searchQuery: String,
    searchResults: List<GeocodedAddress>,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onPickOnMap: (() -> Unit)?,
    onSelectResult: (name: String, place: GeocodedAddress) -> Unit,
    onDismiss: () -> Unit
) {
    var customName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.sheet_add_location_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                label = { Text(stringResource(Res.string.field_location_name)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text(stringResource(Res.string.field_search_address)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) onSearchSubmit()
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onSearchSubmit,
                    enabled = !isSearching && searchQuery.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .height(56.dp)
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.btn_search))
                    }
                }
            }

            if (onPickOnMap != null) {
                OutlinedButton(
                    onClick = onPickOnMap,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(Res.string.btn_pick_on_map), fontWeight = FontWeight.SemiBold)
                }
            }

            if (searchResults.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(240.dp)
                    ) {
                        items(searchResults) { place ->
                            ListItem(
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                headlineContent = {
                                    Text(place.locality ?: place.displayName, maxLines = 1, fontWeight = FontWeight.Medium)
                                },
                                supportingContent = {
                                    Text(place.displayName, maxLines = 2)
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier.clickable {
                                    onSelectResult(customName, place)
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            } else if (!isSearching && searchQuery.trim().length >= 2) {
                Text(
                    text = stringResource(Res.string.search_no_results),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointDetailsBottomSheet(
    status: PointStatus,
    onDismiss: () -> Unit
) {
    var nowInstant by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(status.point.id, status.isAlertActive) {
        while (status.isAlertActive) {
            nowInstant = Clock.System.now()
            delay(1_000L)
        }
    }

    val statusTint = if (status.isAlertActive) AlertColors.AlertRed else AlertColors.SafeGreen

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(statusTint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = statusTint
                    )
                }
                Text(
                    text = status.point.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailRow(
                        label = stringResource(Res.string.point_details_region),
                        value = status.point.regionName
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow(
                        label = stringResource(Res.string.point_details_status),
                        value = if (status.isAlertActive) {
                            stringResource(Res.string.points_status_alert)
                        } else {
                            stringResource(Res.string.points_status_clear)
                        },
                        valueColor = statusTint
                    )

                    if (status.isAlertActive && !status.earliestStartedAtIso.isNullOrBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailRow(
                            label = stringResource(Res.string.point_details_duration),
                            value = DurationFormatter.formatDurationSince(status.earliestStartedAtIso, nowInstant),
                            valueColor = AlertColors.AlertRed
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val lastCheckedLabel = if (status.lastCheckedEpochMs > 0L) {
                        val dt = Instant.fromEpochMilliseconds(status.lastCheckedEpochMs)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                        DurationFormatter.formatTimeOfDay(dt.hour, dt.minute)
                    } else {
                        stringResource(Res.string.just_now)
                    }
                    DetailRow(
                        label = stringResource(Res.string.point_details_last_check),
                        value = lastCheckedLabel
                    )
                }
            }

            Text(
                text = stringResource(Res.string.point_details_alert_types),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            if (status.activeAlerts.isEmpty()) {
                Text(
                    text = stringResource(Res.string.point_details_no_active),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                status.activeAlerts.forEach { alert ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, AlertColors.AlertRed.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${alertTypeLabel(alert.type)} • ${alert.regionName}",
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreatDetailsBottomSheet(
    item: ThreatWithProximity,
    warningRadiusKm: Int = 30,
    highlightNearbyThreats: Boolean = true,
    onOpenKnowledgeBase: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val threat = item.threat
    val isWarningActive = highlightNearbyThreats && item.isWithinWarningRadius
    val threatTint = if (isWarningActive) AlertColors.AlertRed else AlertColors.WarningAmber

    val updatedInstant = remember(threat.updatedAtIso) {
        DurationFormatter.parseInstantOrNull(threat.updatedAtIso)
    }
    var nowInstant by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(threat.id, threat.updatedAtIso) {
        nowInstant = Clock.System.now()
        while (updatedInstant != null) {
            delay(15_000L)
            nowInstant = Clock.System.now()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = androidx.compose.ui.graphics.Color(0xFF1A2535),
                    border = BorderStroke(
                        width = if (isWarningActive) 2.dp else 1.dp,
                        color = threatTint
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ThreatTypeIcon(
                            type = threat.type,
                            modifier = Modifier.size(40.dp),
                            isHighlighted = isWarningActive
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = threat.title.ifBlank { threatTypeLabel(threat.type) },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (threat.locality.isNotBlank() || threat.region.isNotBlank()) {
                        Text(
                            text = listOf(threat.locality, threat.region).filter { it.isNotBlank() }.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isWarningActive) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AlertColors.AlertRedContainerDark,
                            border = BorderStroke(1.dp, AlertColors.AlertRed.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = AlertColors.AlertRed,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (warningRadiusKm <= 0) {
                                        stringResource(Res.string.threat_details_inside_radius_zero)
                                    } else {
                                        stringResource(Res.string.threat_details_inside_radius, warningRadiusKm)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AlertColors.AlertOnContainerDark
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailRow(
                        label = stringResource(Res.string.threat_details_type),
                        value = threatTypeLabel(threat.type)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow(
                        label = stringResource(Res.string.threat_details_heading),
                        value = threat.headingDeg?.let { "${it.roundToInt()}°" }
                            ?: stringResource(Res.string.threat_details_heading_unknown)
                    )

                    threat.speedKmh?.let { speed ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailRow(
                            label = stringResource(Res.string.threat_details_speed),
                            value = "${speed.roundToInt()} km/h"
                        )
                    }

                    if (item.nearestPointName != null && item.distanceKm != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailRow(
                            label = stringResource(Res.string.threat_details_distance, item.nearestPointName),
                            value = stringResource(Res.string.threat_details_distance_value, item.distanceKm.roundToInt()),
                            valueColor = if (isWarningActive) AlertColors.AlertRed else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    updatedInstant?.let { instant ->
                        val dt = remember(instant) {
                            instant.toLocalDateTime(TimeZone.currentSystemDefault())
                        }
                        val timeLabel = remember(dt) {
                            DurationFormatter.formatTimeOfDay(dt.hour, dt.minute)
                        }
                        val ageMinutes = DurationFormatter.ageMinutesSince(instant, nowInstant)
                        val ageLabel = when {
                            ageMinutes < 1 -> stringResource(Res.string.explosion_details_age_just_now)
                            ageMinutes < 60 -> stringResource(Res.string.explosion_details_age_minutes, ageMinutes)
                            else -> stringResource(
                                Res.string.threat_details_age_hours_minutes,
                                ageMinutes / 60,
                                ageMinutes % 60
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailRow(
                            label = stringResource(Res.string.threat_details_updated),
                            value = "$timeLabel ($ageLabel)"
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow(
                        label = stringResource(Res.string.threat_details_source),
                        value = threatDataSourceFullLabel(threat.source)
                    )
                }
            }

            threat.uncertaintyKm?.let { unc ->
                Text(
                    text = stringResource(Res.string.threat_details_uncertainty, unc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (threat.explanation.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = threat.explanation,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (onOpenKnowledgeBase != null) {
                OutlinedButton(
                    onClick = onOpenKnowledgeBase,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(Res.string.threat_details_open_kb),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = stringResource(Res.string.threat_details_approx_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplosionDetailsBottomSheet(
    item: ExplosionWithProximity,
    onDismiss: () -> Unit
) {
    val explosion = item.explosion
    val accentTint = if (explosion.isRecent) AlertColors.AlertRed else AlertColors.WarningAmber

    val nowMs = Clock.System.now().toEpochMilliseconds()
    val ageMinutes = ((nowMs - explosion.timestampEpochMs).coerceAtLeast(0L) / 60_000L).toInt()
    val ageLabel = if (ageMinutes < 1) {
        stringResource(Res.string.explosion_details_age_just_now)
    } else {
        stringResource(Res.string.explosion_details_age_minutes, ageMinutes)
    }
    val timeLabel = remember(explosion.timestampEpochMs) {
        val dt = Instant.fromEpochMilliseconds(explosion.timestampEpochMs)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        DurationFormatter.formatTimeOfDay(dt.hour, dt.minute)
    }
    val districtText = listOf(explosion.raionName, explosion.oblastName)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(", ")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = androidx.compose.ui.graphics.Color(0xFF1A2535),
                    border = BorderStroke(1.5.dp, accentTint)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ExplosionIcon(
                            modifier = Modifier.size(40.dp),
                            isRecent = explosion.isRecent
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = explosion.title.ifBlank {
                            stringResource(Res.string.explosion_details_title_default)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (explosion.localityName.isNotBlank()) {
                        Text(
                            text = explosion.localityName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailRow(
                        label = stringResource(Res.string.explosion_details_time),
                        value = "$timeLabel ($ageLabel)",
                        valueColor = accentTint
                    )
                    if (districtText.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailRow(
                            label = stringResource(Res.string.explosion_details_district),
                            value = districtText
                        )
                    }
                    if (item.nearestPointName != null && item.distanceKm != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailRow(
                            label = stringResource(Res.string.explosion_details_distance, item.nearestPointName),
                            value = stringResource(Res.string.threat_details_distance_value, item.distanceKm.roundToInt())
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow(
                        label = stringResource(Res.string.explosion_details_source),
                        value = stringResource(Res.string.explosion_details_source_nextalert)
                    )
                }
            }

            Text(
                text = stringResource(Res.string.explosion_details_approx_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RenameLocationDialog(
    point: SavedPoint,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(point.id) { mutableStateOf(point.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sheet_rename_location_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(Res.string.field_location_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(Res.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
