package com.onemanarmy.airalerts.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemanarmy.airalerts.data.remote.NeptunRadarApi
import com.onemanarmy.airalerts.domain.logic.DurationFormatter
import com.onemanarmy.airalerts.domain.model.AdminLevel
import com.onemanarmy.airalerts.domain.model.AlertLevel
import com.onemanarmy.airalerts.domain.model.PointStatus
import com.onemanarmy.airalerts.domain.model.RegionThreatLevel
import com.onemanarmy.airalerts.resources.Res
import com.onemanarmy.airalerts.resources.btn_save
import com.onemanarmy.airalerts.resources.default_home_name
import com.onemanarmy.airalerts.resources.kb_map_legend_btn
import com.onemanarmy.airalerts.resources.legend_clear
import com.onemanarmy.airalerts.resources.legend_critical
import com.onemanarmy.airalerts.resources.legend_elevated
import com.onemanarmy.airalerts.resources.legend_high
import com.onemanarmy.airalerts.resources.map_add_point
import com.onemanarmy.airalerts.resources.map_center_gps
import com.onemanarmy.airalerts.resources.map_disclaimer_link
import com.onemanarmy.airalerts.resources.map_disclaimer_prefix
import com.onemanarmy.airalerts.resources.map_nearby_threat_banner
import com.onemanarmy.airalerts.resources.map_nearby_threat_banner_zero
import com.onemanarmy.airalerts.resources.map_toggle_radar_off
import com.onemanarmy.airalerts.resources.map_toggle_radar_on
import com.onemanarmy.airalerts.resources.region_nearby_targets_count
import com.onemanarmy.airalerts.resources.region_threat_clear
import com.onemanarmy.airalerts.resources.region_threat_critical
import com.onemanarmy.airalerts.resources.region_threat_elevated
import com.onemanarmy.airalerts.resources.region_threat_high
import com.onemanarmy.airalerts.resources.status_alert_active
import com.onemanarmy.airalerts.resources.status_no_alert
import com.onemanarmy.airalerts.resources.status_no_points_subtitle
import com.onemanarmy.airalerts.resources.status_no_points_title
import com.onemanarmy.airalerts.resources.status_point_subtitle
import com.onemanarmy.airalerts.resources.status_yellow_warning
import com.onemanarmy.airalerts.ui.components.AddLocationBottomSheet
import com.onemanarmy.airalerts.ui.components.CrosshairMapPickerOverlay
import com.onemanarmy.airalerts.ui.components.MapZoomControlsPill
import com.onemanarmy.airalerts.ui.components.PointDetailsBottomSheet
import com.onemanarmy.airalerts.ui.components.ThreatDetailsBottomSheet
import com.onemanarmy.airalerts.ui.components.ThreatKnowledgeBaseSheet
import com.onemanarmy.airalerts.ui.theme.AlertColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun MapTabScreen(
    viewModel: MapViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Strict lifecycle binding: NEPTUN radar polling only runs while Map tab is visible and STARTED/RESUMED.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startRadarPolling()
                Lifecycle.Event.ON_STOP -> viewModel.stopRadarPolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.startRadarPolling()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopRadarPolling()
        }
    }

    val gpsPair = if (uiState.settings.gpsTrackingEnabled &&
        uiState.settings.gpsLatitude != null &&
        uiState.settings.gpsLongitude != null
    ) {
        uiState.settings.gpsLatitude!! to uiState.settings.gpsLongitude!!
    } else {
        null
    }

    var zoomInVersion by remember { mutableIntStateOf(0) }
    var zoomOutVersion by remember { mutableIntStateOf(0) }
    var isKnowledgeBaseOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isCrosshairMode) {
            val defaultLabel = stringResource(Res.string.default_home_name)
            CrosshairMapPickerOverlay(
                initialLat = uiState.mapCenterLat,
                initialLon = uiState.mapCenterLon,
                initialZoom = uiState.mapZoom,
                confirmLabel = stringResource(Res.string.btn_save),
                onCancel = viewModel::cancelCrosshairMode,
                onConfirm = { lat, lon ->
                    viewModel.saveLocationFromCoordinates("", lat, lon, defaultLabel)
                }
            )
        } else {
            AlertMapView(
                modifier = Modifier.fillMaxSize(),
                centerLat = uiState.mapCenterLat,
                centerLon = uiState.mapCenterLon,
                zoomLevel = uiState.mapZoom,
                focusVersion = uiState.focusVersion,
                zoomInVersion = zoomInVersion,
                zoomOutVersion = zoomOutVersion,
                trackedPoints = uiState.pointStatuses,
                threats = uiState.threats,
                activeAlerts = uiState.activeAlerts,
                boundaries = uiState.boundaries,
                regionThreats = uiState.regionThreats,
                selectedRegionId = uiState.selectedRegion?.polygon?.id,
                showThreatsLayer = uiState.settings.radarLayerEnabled,
                warningRadiusKm = uiState.settings.threatNotificationRadiusKm,
                highlightNearbyThreats = uiState.settings.threatRadiusNotificationEnabled,
                gpsCoordinates = gpsPair,
                isCrosshairPickerMode = false,
                onPointClicked = viewModel::onPointClicked,
                onThreatClicked = viewModel::onThreatClicked,
                onRegionClicked = viewModel::onRegionClicked,
                onMapCenterChanged = viewModel::onMapCenterChanged,
                onMapViewportChanged = viewModel::onMapViewportChanged,
                onUserMapInteraction = viewModel::onUserMapInteraction
            )

            // Top sticky swipeable status card + optional proximity banner.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StickyStatusPager(
                    statuses = uiState.pointStatuses,
                    selectedIndex = uiState.selectedPointIndex,
                    onPageSelected = viewModel::selectPointPage,
                    onCardClick = viewModel::onPointClicked
                )

                uiState.nearbyThreat?.let { nearby ->
                    val distKm = nearby.distanceKm?.roundToInt() ?: 0
                    val pointName = nearby.nearestPointName.orEmpty()
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { viewModel.onThreatClicked(nearby) },
                        shape = RoundedCornerShape(12.dp),
                        color = AlertColors.WarningAmberContainerDark.copy(alpha = 0.96f),
                        border = BorderStroke(1.dp, AlertColors.WarningAmber.copy(alpha = 0.55f)),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AlertColors.WarningAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (uiState.settings.threatNotificationRadiusKm <= 0) {
                                    stringResource(
                                        Res.string.map_nearby_threat_banner_zero,
                                        pointName,
                                        distKm
                                    )
                                } else {
                                    stringResource(
                                        Res.string.map_nearby_threat_banner,
                                        uiState.settings.threatNotificationRadiusKm,
                                        pointName,
                                        distKm
                                    )
                                },
                                color = AlertColors.WarningOnContainerDark,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Bottom-left selected region card + threat legend pill.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 84.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                uiState.selectedRegion?.let { selected ->
                    SelectedRegionInfoCard(
                        selected = selected,
                        onDismiss = viewModel::dismissSelectedRegion
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RegionThreatLegendPill()

                    Surface(
                        modifier = Modifier.clickable { isKnowledgeBaseOpen = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = stringResource(Res.string.kb_map_legend_btn),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(Res.string.kb_map_legend_btn),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Right-side map zoom pill + Floating Action Buttons.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 52.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                MapZoomControlsPill(
                    onZoomIn = { zoomInVersion++ },
                    onZoomOut = { zoomOutVersion++ }
                )

                SmallFloatingActionButton(
                    onClick = viewModel::zoomToUkraine,
                    shape = RoundedCornerShape(14.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                    modifier = Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(14.dp)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOutMap,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }

                SmallFloatingActionButton(
                    onClick = viewModel::toggleRadarLayer,
                    shape = RoundedCornerShape(14.dp),
                    containerColor = if (uiState.settings.radarLayerEnabled) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
                    },
                    contentColor = if (uiState.settings.radarLayerEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                    modifier = Modifier.border(
                        1.dp,
                        if (uiState.settings.radarLayerEnabled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        RoundedCornerShape(14.dp)
                    )
                ) {
                    Icon(
                        imageVector = if (uiState.settings.radarLayerEnabled) {
                            Icons.Default.Radar
                        } else {
                            Icons.Default.VisibilityOff
                        },
                        contentDescription = stringResource(
                            if (uiState.settings.radarLayerEnabled) {
                                Res.string.map_toggle_radar_on
                            } else {
                                Res.string.map_toggle_radar_off
                            }
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                }

                SmallFloatingActionButton(
                    onClick = viewModel::centerOnMyLocation,
                    shape = RoundedCornerShape(14.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                    modifier = Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(14.dp)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = stringResource(Res.string.map_center_gps),
                        modifier = Modifier.size(20.dp)
                    )
                }

                FloatingActionButton(
                    onClick = viewModel::openAddLocationSheet,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(Res.string.map_add_point)
                    )
                }
            }

            // Bottom semi-transparent NEPTUN attribution & approximate disclaimer bar.
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.map_disclaimer_prefix),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(Res.string.map_disclaimer_link),
                        style = MaterialTheme.typography.labelSmall.copy(
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            viewModel.openExternalUrl(NeptunRadarApi.NEPTUN_BASE_URL)
                        }
                    )
                }
            }
        }
    }

    // Bottom Sheets
    uiState.selectedPointDetails?.let { details ->
        PointDetailsBottomSheet(
            status = details,
            onDismiss = viewModel::dismissDetailsSheets
        )
    }

    uiState.selectedThreatDetails?.let { threat ->
        ThreatDetailsBottomSheet(
            item = threat,
            warningRadiusKm = uiState.settings.threatNotificationRadiusKm,
            highlightNearbyThreats = uiState.settings.threatRadiusNotificationEnabled,
            onOpenKnowledgeBase = {
                viewModel.dismissDetailsSheets()
                isKnowledgeBaseOpen = true
            },
            onDismiss = viewModel::dismissDetailsSheets
        )
    }

    if (isKnowledgeBaseOpen) {
        ThreatKnowledgeBaseSheet(
            onDismiss = { isKnowledgeBaseOpen = false }
        )
    }

    if (uiState.isAddLocationSheetOpen) {
        AddLocationBottomSheet(
            searchQuery = uiState.searchQuery,
            searchResults = uiState.searchResults,
            isSearching = uiState.isSearching,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onSearchSubmit = viewModel::searchAddress,
            onPickOnMap = viewModel::enterCrosshairMode,
            onSelectResult = { name, place ->
                viewModel.saveLocationFromPlace(name, place)
            },
            onDismiss = viewModel::closeAddLocationSheet
        )
    }
}

@Composable
private fun StickyStatusPager(
    statuses: List<PointStatus>,
    selectedIndex: Int,
    onPageSelected: (Int) -> Unit,
    onCardClick: (PointStatus) -> Unit
) {
    if (statuses.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(Res.string.status_no_points_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(Res.string.status_no_points_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val initialPage = selectedIndex.coerceIn(0, statuses.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { statuses.size }
    )
    var lastHandledPage by remember { mutableIntStateOf(initialPage) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page != lastHandledPage) {
                    lastHandledPage = page
                    onPageSelected(page)
                }
            }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 10.dp
        ) { page ->
            val status = statuses[page]
            StatusStickyCard(
                status = status,
                onClick = { onCardClick(status) }
            )
        }

        if (statuses.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(statuses.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .width(if (selected) 18.dp else 6.dp)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusStickyCard(
    status: PointStatus,
    onClick: () -> Unit
) {
    var nowInstant by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(status.isAlertActive, status.earliestStartedAtIso) {
        while (status.isAlertActive) {
            nowInstant = Clock.System.now()
            delay(1_000L)
        }
    }

    val containerColor = when {
        status.isAlertActive && status.highestLevel == AlertLevel.YELLOW -> AlertColors.WarningAmberContainerDark
        status.isAlertActive -> AlertColors.AlertRedContainerDark
        else -> AlertColors.SafeGreenContainerDark
    }

    val borderColor = when {
        status.isAlertActive && status.highestLevel == AlertLevel.YELLOW -> AlertColors.WarningAmber.copy(alpha = 0.55f)
        status.isAlertActive -> AlertColors.AlertRed.copy(alpha = 0.6f)
        else -> AlertColors.SafeGreen.copy(alpha = 0.45f)
    }

    val contentColor = when {
        status.isAlertActive && status.highestLevel == AlertLevel.YELLOW -> AlertColors.WarningOnContainerDark
        status.isAlertActive -> AlertColors.AlertOnContainerDark
        else -> AlertColors.SafeOnContainerDark
    }

    val iconTint = when {
        status.isAlertActive && status.highestLevel == AlertLevel.YELLOW -> AlertColors.WarningAmber
        status.isAlertActive -> AlertColors.AlertRed
        else -> AlertColors.SafeGreen
    }

    val durationText = DurationFormatter.formatDurationSince(status.earliestStartedAtIso, nowInstant)
    val headlineText = when {
        status.isAlertActive && status.highestLevel == AlertLevel.YELLOW -> {
            stringResource(Res.string.status_yellow_warning, durationText)
        }
        status.isAlertActive -> {
            stringResource(Res.string.status_alert_active, durationText)
        }
        else -> {
            stringResource(Res.string.status_no_alert, status.point.name)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (status.isAlertActive) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = headlineText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        Res.string.status_point_subtitle,
                        status.point.regionName,
                        status.point.name
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SelectedRegionInfoCard(
    selected: SelectedRegionDetails,
    onDismiss: () -> Unit
) {
    val threatColor = when (selected.threatInfo.threatLevel) {
        RegionThreatLevel.CLEAR -> AlertColors.SafeGreen
        RegionThreatLevel.ELEVATED -> AlertColors.WarningAmber
        RegionThreatLevel.HIGH -> AlertColors.AlertRed
        RegionThreatLevel.CRITICAL -> AlertColors.CriticalRed
    }
    val statusText = when (selected.threatInfo.threatLevel) {
        RegionThreatLevel.CLEAR -> stringResource(Res.string.region_threat_clear)
        RegionThreatLevel.ELEVATED -> stringResource(Res.string.region_threat_elevated)
        RegionThreatLevel.HIGH -> stringResource(Res.string.region_threat_high)
        RegionThreatLevel.CRITICAL -> stringResource(Res.string.region_threat_critical)
    }
    val subtitle = when (selected.polygon.level) {
        AdminLevel.OBLAST -> ""
        AdminLevel.RAION -> selected.polygon.oblastName
        AdminLevel.HROMADA -> if (selected.polygon.raionName.isBlank() ||
            selected.polygon.raionName == selected.polygon.oblastName
        ) {
            selected.polygon.oblastName
        } else {
            "${selected.polygon.raionName} • ${selected.polygon.oblastName}"
        }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(threatColor)
            )
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = selected.polygon.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = threatColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (selected.threatInfo.nearbyThreatCount > 0) {
                    Text(
                        text = stringResource(
                            Res.string.region_nearby_targets_count,
                            selected.threatInfo.nearbyThreatCount
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun RegionThreatLegendPill() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(color = AlertColors.SafeGreen, label = stringResource(Res.string.legend_clear))
            LegendItem(color = AlertColors.WarningAmber, label = stringResource(Res.string.legend_elevated))
            LegendItem(color = AlertColors.AlertRed, label = stringResource(Res.string.legend_high))
            LegendItem(color = AlertColors.CriticalRed, label = stringResource(Res.string.legend_critical))
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

