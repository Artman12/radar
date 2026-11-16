package com.onemanarmy.airalerts.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onemanarmy.airalerts.domain.model.ThreatType
import com.onemanarmy.airalerts.domain.model.ThreatWithProximity
import com.onemanarmy.airalerts.resources.Res
import com.onemanarmy.airalerts.resources.map_next_refresh_cd
import com.onemanarmy.airalerts.resources.threats_list_distance_from
import com.onemanarmy.airalerts.resources.threats_list_empty
import com.onemanarmy.airalerts.resources.threats_list_filter_all
import com.onemanarmy.airalerts.resources.threats_list_speed_kmh
import com.onemanarmy.airalerts.resources.threats_list_title
import com.onemanarmy.airalerts.resources.threats_list_total
import com.onemanarmy.airalerts.resources.threats_summary_none
import com.onemanarmy.airalerts.ui.common.threatTypeLabel
import com.onemanarmy.airalerts.ui.theme.AlertColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

internal val THREAT_DISPLAY_ORDER = listOf(
    ThreatType.BALLISTIC,
    ThreatType.CRUISE_MISSILE,
    ThreatType.KAB,
    ThreatType.JET_SHAHED,
    ThreatType.UAV,
    ThreatType.AVIATION,
    ThreatType.FPV,
    ThreatType.RECON,
    ThreatType.UNKNOWN
)

internal data class ThreatTypeCount(
    val type: ThreatType,
    val count: Int,
    val hasNearbyWarning: Boolean
)

internal fun summarizeThreatsByType(
    threats: List<ThreatWithProximity>,
    highlightNearbyThreats: Boolean
): List<ThreatTypeCount> {
    if (threats.isEmpty()) return emptyList()
    val grouped = threats.groupBy { it.threat.type }
    return THREAT_DISPLAY_ORDER.mapNotNull { type ->
        val items = grouped[type] ?: return@mapNotNull null
        val total = items.sumOf { it.threat.count.coerceAtLeast(1) }
        if (total <= 0) return@mapNotNull null
        ThreatTypeCount(
            type = type,
            count = total,
            hasNearbyWarning = highlightNearbyThreats && items.any { it.isWithinWarningRadius }
        )
    }
}

internal fun sortThreatsForList(
    threats: List<ThreatWithProximity>,
    highlightNearbyThreats: Boolean
): List<ThreatWithProximity> {
    return threats.sortedWith(
        compareByDescending<ThreatWithProximity> { highlightNearbyThreats && it.isWithinWarningRadius }
            .thenBy { it.distanceKm ?: Double.MAX_VALUE }
            .thenBy { typePriorityIndex(it.threat.type) }
            .thenByDescending { it.threat.count }
    )
}

private fun typePriorityIndex(type: ThreatType): Int {
    val idx = THREAT_DISPLAY_ORDER.indexOf(type)
    return if (idx >= 0) idx else THREAT_DISPLAY_ORDER.size
}

@Composable
fun ActiveThreatsSummaryPill(
    threats: List<ThreatWithProximity>,
    highlightNearbyThreats: Boolean,
    nextRefreshEpochMs: Long? = null,
    refreshIntervalMs: Long = 10_000L,
    isRefreshing: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val counts = remember(threats, highlightNearbyThreats) {
        summarizeThreatsByType(threats, highlightNearbyThreats)
    }
    val hasAnyNearby = counts.any { it.hasNearbyWarning }

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(
            width = if (hasAnyNearby) 1.dp else 0.5.dp,
            color = if (hasAnyNearby) {
                AlertColors.AlertRed.copy(alpha = 0.75f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        tonalElevation = 2.dp
    ) {
        if (counts.isEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRefreshing || nextRefreshEpochMs != null) {
                    RefreshCountdownBadge(
                        nextRefreshEpochMs = nextRefreshEpochMs,
                        intervalMs = refreshIntervalMs,
                        isRefreshing = isRefreshing
                    )
                }
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(Res.string.threats_summary_none),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRefreshing || nextRefreshEpochMs != null) {
                    RefreshCountdownBadge(
                        nextRefreshEpochMs = nextRefreshEpochMs,
                        intervalMs = refreshIntervalMs,
                        isRefreshing = isRefreshing
                    )
                }
                FlowRow(
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    counts.forEach { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            ThreatTypeIcon(
                                type = entry.type,
                                modifier = Modifier.size(19.dp),
                                isHighlighted = entry.hasNearbyWarning
                            )
                            Text(
                                text = entry.count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (entry.hasNearbyWarning) {
                                    AlertColors.AlertRed
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1
                            )
                        }
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = stringResource(Res.string.threats_list_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun RefreshCountdownBadge(
    nextRefreshEpochMs: Long?,
    intervalMs: Long,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    var nowMs by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }

    LaunchedEffect(nextRefreshEpochMs, isRefreshing) {
        if (!isRefreshing && nextRefreshEpochMs != null) {
            while (isActive) {
                nowMs = Clock.System.now().toEpochMilliseconds()
                delay(150.milliseconds)
            }
        }
    }

    if (isRefreshing) {
        CircularProgressIndicator(
            modifier = modifier.size(12.dp),
            strokeWidth = 1.8.dp,
            color = MaterialTheme.colorScheme.primary
        )
        return
    }

    val targetMs = nextRefreshEpochMs ?: return
    val safeInterval = intervalMs.coerceAtLeast(1_000L)
    val remainingMs = (targetMs - nowMs).coerceIn(0L, safeInterval)
    val remainingFraction = (remainingMs.toFloat() / safeInterval.toFloat()).coerceIn(0f, 1f)
    val secondsLeft = ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(0)
    val cdText = stringResource(Res.string.map_next_refresh_cd, secondsLeft)

    Box(
        modifier = modifier
            .size(12.dp)
            .semantics { contentDescription = cdText },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { remainingFraction },
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.8.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllThreatsBottomSheet(
    threats: List<ThreatWithProximity>,
    highlightNearbyThreats: Boolean,
    onThreatSelected: (ThreatWithProximity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val counts = remember(threats, highlightNearbyThreats) {
        summarizeThreatsByType(threats, highlightNearbyThreats)
    }
    val totalCount = remember(counts) { counts.sumOf { it.count } }

    var selectedFilter by remember { mutableStateOf<ThreatType?>(null) }
    val effectiveFilter = if (selectedFilter != null && counts.any { it.type == selectedFilter }) {
        selectedFilter
    } else {
        null
    }

    val displayedThreats = remember(threats, effectiveFilter, highlightNearbyThreats) {
        val filtered = if (effectiveFilter == null) {
            threats
        } else {
            threats.filter { it.threat.type == effectiveFilter }
        }
        sortThreatsForList(filtered, highlightNearbyThreats)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.threats_list_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(Res.string.threats_list_total, totalCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null
                    )
                }
            }

            if (counts.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    item {
                        val isSelected = effectiveFilter == null
                        Surface(
                            modifier = Modifier.clickable { selectedFilter = null },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                        ) {
                            Text(
                                text = "${stringResource(Res.string.threats_list_filter_all)} ($totalCount)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    items(counts, key = { it.type.name }) { entry ->
                        val isSelected = effectiveFilter == entry.type
                        Surface(
                            modifier = Modifier.clickable {
                                selectedFilter = if (isSelected) null else entry.type
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    entry.hasNearbyWarning -> AlertColors.AlertRed.copy(alpha = 0.65f)
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                ThreatTypeIcon(
                                    type = entry.type,
                                    modifier = Modifier.size(18.dp),
                                    isHighlighted = entry.hasNearbyWarning
                                )
                                Text(
                                    text = entry.count.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        entry.hasNearbyWarning -> AlertColors.AlertRed
                                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (displayedThreats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.threats_list_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    items(displayedThreats, key = { it.threat.id }) { item ->
                        ThreatListRowCard(
                            item = item,
                            highlightNearbyThreats = highlightNearbyThreats,
                            onClick = { onThreatSelected(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreatListRowCard(
    item: ThreatWithProximity,
    highlightNearbyThreats: Boolean,
    onClick: () -> Unit
) {
    val threat = item.threat
    val isWarningActive = highlightNearbyThreats && item.isWithinWarningRadius
    val locationText = listOf(threat.locality, threat.region)
        .filter { it.isNotBlank() }
        .joinToString(", ")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isWarningActive) {
                AlertColors.AlertRedContainerDark.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isWarningActive) {
                AlertColors.AlertRed.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(11.dp),
                color = Color(0xFF1A2535),
                border = BorderStroke(
                    width = if (isWarningActive) 1.5.dp else 1.dp,
                    color = if (isWarningActive) AlertColors.AlertRed else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ThreatTypeIcon(
                        type = threat.type,
                        modifier = Modifier.size(32.dp),
                        isHighlighted = isWarningActive
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = threat.title.ifBlank { threatTypeLabel(threat.type) },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (threat.count > 1) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AlertColors.WarningAmber.copy(alpha = 0.18f),
                            border = BorderStroke(0.5.dp, AlertColors.WarningAmber.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = "×${threat.count}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AlertColors.WarningAmber,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                if (locationText.isNotBlank()) {
                    Text(
                        text = locationText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val telemetryParts = buildList {
                    threat.speedKmh?.let { add(stringResource(Res.string.threats_list_speed_kmh, it.roundToInt())) }
                    threat.headingDeg?.let { add("${it.roundToInt()}°") }
                }
                val distanceText = if (item.distanceKm != null && item.nearestPointName != null) {
                    stringResource(
                        Res.string.threats_list_distance_from,
                        item.distanceKm.roundToInt(),
                        item.nearestPointName
                    )
                } else {
                    null
                }

                if (telemetryParts.isNotEmpty() || distanceText != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (telemetryParts.isNotEmpty()) {
                            Text(
                                text = telemetryParts.joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        if (distanceText != null) {
                            Text(
                                text = if (telemetryParts.isNotEmpty()) "• $distanceText" else distanceText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isWarningActive) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isWarningActive) AlertColors.AlertRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
