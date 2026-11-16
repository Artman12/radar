package com.onemanarmy.airalerts.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.onemanarmy.airalerts.domain.model.ActiveAlertInfo
import com.onemanarmy.airalerts.domain.model.PointStatus
import com.onemanarmy.airalerts.domain.model.RegionPolygon
import com.onemanarmy.airalerts.domain.model.RegionThreatInfo
import com.onemanarmy.airalerts.domain.model.RegionThreatSnapshot
import com.onemanarmy.airalerts.domain.model.ThreatWithProximity
import com.onemanarmy.airalerts.domain.model.UkraineBounds
import com.onemanarmy.airalerts.domain.model.UkraineMapBoundaries

/**
 * Platform-specific interactive map view (implemented with osmdroid on Android).
 */
@Composable
expect fun AlertMapView(
    modifier: Modifier = Modifier,
    centerLat: Double,
    centerLon: Double,
    zoomLevel: Double = UkraineBounds.COUNTRY_ZOOM,
    focusVersion: Int = 0,
    zoomInVersion: Int = 0,
    zoomOutVersion: Int = 0,
    trackedPoints: List<PointStatus>,
    threats: List<ThreatWithProximity>,
    activeAlerts: List<ActiveAlertInfo> = emptyList(),
    boundaries: UkraineMapBoundaries? = null,
    regionThreats: RegionThreatSnapshot? = null,
    selectedRegionId: String? = null,
    showThreatsLayer: Boolean,
    warningRadiusKm: Int,
    highlightNearbyThreats: Boolean = true,
    gpsCoordinates: Pair<Double, Double>?,
    isCrosshairPickerMode: Boolean = false,
    onPointClicked: (PointStatus) -> Unit = {},
    onThreatClicked: (ThreatWithProximity) -> Unit = {},
    onRegionClicked: (RegionPolygon, RegionThreatInfo?) -> Unit = { _, _ -> },
    onMapCenterChanged: (Double, Double) -> Unit = { _, _ -> },
    onMapViewportChanged: (Double, Double, Double) -> Unit = { _, _, _ -> },
    onUserMapInteraction: () -> Unit = {}
)
