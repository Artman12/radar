package com.onemanarmy.radar.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.onemanarmy.radar.domain.model.ActiveAlertInfo
import com.onemanarmy.radar.domain.model.ExplosionWithProximity
import com.onemanarmy.radar.domain.model.OccupiedTerritoriesSnapshot
import com.onemanarmy.radar.domain.model.PointStatus
import com.onemanarmy.radar.domain.model.RegionPolygon
import com.onemanarmy.radar.domain.model.RegionThreatInfo
import com.onemanarmy.radar.domain.model.RegionThreatSnapshot
import com.onemanarmy.radar.domain.model.ThreatWithProximity
import com.onemanarmy.radar.domain.model.UkraineBounds
import com.onemanarmy.radar.domain.model.UkraineMapBoundaries

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
    explosions: List<ExplosionWithProximity> = emptyList(),
    activeAlerts: List<ActiveAlertInfo> = emptyList(),
    boundaries: UkraineMapBoundaries? = null,
    regionThreats: RegionThreatSnapshot? = null,
    occupiedTerritories: OccupiedTerritoriesSnapshot? = null,
    selectedRegionId: String? = null,
    showThreatsLayer: Boolean,
    showExplosionsLayer: Boolean = false,
    showOccupiedLayer: Boolean = false,
    warningRadiusKm: Int,
    highlightNearbyThreats: Boolean = true,
    gpsCoordinates: Pair<Double, Double>?,
    isCrosshairPickerMode: Boolean = false,
    onPointClicked: (PointStatus) -> Unit = {},
    onThreatClicked: (ThreatWithProximity) -> Unit = {},
    onExplosionClicked: (ExplosionWithProximity) -> Unit = {},
    onRegionClicked: (RegionPolygon, RegionThreatInfo?) -> Unit = { _, _ -> },
    onMapCenterChanged: (Double, Double) -> Unit = { _, _ -> },
    onMapViewportChanged: (Double, Double, Double) -> Unit = { _, _, _ -> },
    onUserMapInteraction: () -> Unit = {}
)
