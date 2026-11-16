package com.onemanarmy.airalerts.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemanarmy.airalerts.data.local.UkraineBoundaryRepository
import com.onemanarmy.airalerts.domain.logic.GeoUtils
import com.onemanarmy.airalerts.domain.logic.ThreatRadiusEvaluator
import com.onemanarmy.airalerts.domain.model.ActiveAlertInfo
import com.onemanarmy.airalerts.domain.model.AlertLevel
import com.onemanarmy.airalerts.domain.model.PointStatus
import com.onemanarmy.airalerts.domain.model.RegionPolygon
import com.onemanarmy.airalerts.domain.model.RegionThreatInfo
import com.onemanarmy.airalerts.domain.model.RegionThreatLevel
import com.onemanarmy.airalerts.domain.model.RegionThreatSnapshot
import com.onemanarmy.airalerts.domain.model.ThreatType
import com.onemanarmy.airalerts.domain.model.ThreatWithProximity
import com.onemanarmy.airalerts.domain.model.UkraineBounds
import com.onemanarmy.airalerts.domain.model.UkraineMapBoundaries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.util.SimpleInvalidationHandler
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

@Composable
actual fun AlertMapView(
    modifier: Modifier,
    centerLat: Double,
    centerLon: Double,
    zoomLevel: Double,
    focusVersion: Int,
    zoomInVersion: Int,
    zoomOutVersion: Int,
    trackedPoints: List<PointStatus>,
    threats: List<ThreatWithProximity>,
    activeAlerts: List<ActiveAlertInfo>,
    boundaries: UkraineMapBoundaries?,
    regionThreats: RegionThreatSnapshot?,
    selectedRegionId: String?,
    showThreatsLayer: Boolean,
    warningRadiusKm: Int,
    highlightNearbyThreats: Boolean,
    gpsCoordinates: Pair<Double, Double>?,
    isCrosshairPickerMode: Boolean,
    onPointClicked: (PointStatus) -> Unit,
    onThreatClicked: (ThreatWithProximity) -> Unit,
    onRegionClicked: (RegionPolygon, RegionThreatInfo?) -> Unit,
    onMapCenterChanged: (Double, Double) -> Unit,
    onMapViewportChanged: (Double, Double, Double) -> Unit,
    onUserMapInteraction: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDark = true

    // Ensure detailed Ukraine boundaries are loaded even on picker screens that pass boundaries = null.
    val boundaryRepo: UkraineBoundaryRepository = koinInject()
    val repoBoundaries by boundaryRepo.boundariesFlow.collectAsStateWithLifecycle()
    val effectiveBoundaries = boundaries ?: repoBoundaries

    LaunchedEffect(boundaries == null) {
        if (boundaries == null) {
            boundaryRepo.loadIfNeeded()
        }
    }

    // Reuse cached Mercator spatial vector & overview bitmap atlas across tab switches and startup preload.
    val prebuiltStatic = remember(effectiveBoundaries, isDark) {
        UkraineAtlasCache.peekStatic(effectiveBoundaries, isDark)
    }
    val staticAtlas by produceState<UkraineStaticMercatorAtlas?>(
        initialValue = prebuiltStatic,
        effectiveBoundaries,
        isDark
    ) {
        val existing = UkraineAtlasCache.peekStatic(effectiveBoundaries, isDark)
        if (existing != null) {
            value = existing
            return@produceState
        }
        val data = effectiveBoundaries ?: return@produceState
        value = withContext(Dispatchers.Default) {
            UkraineAtlasCache.getOrBuildStatic(data, isDark)
        }
    }

    val prebuiltThreat = remember(staticAtlas, regionThreats) {
        UkraineAtlasCache.peekThreat(staticAtlas, regionThreats)
    }
    val threatAtlas by produceState<UkraineThreatAtlas?>(
        initialValue = prebuiltThreat,
        staticAtlas,
        regionThreats
    ) {
        val atlas = staticAtlas
        val snapshot = regionThreats
        if (atlas == null || snapshot == null) {
            value = null
            return@produceState
        }
        val existing = UkraineAtlasCache.peekThreat(atlas, snapshot)
        if (existing != null) {
            value = existing
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            UkraineAtlasCache.getOrBuildThreat(atlas, snapshot)
        }
    }

    val selectedRegionPath = remember(staticAtlas, selectedRegionId) {
        staticAtlas?.getSelectedRegionPath(selectedRegionId)
    }

    val currentOnPointClicked by rememberUpdatedState(onPointClicked)
    val currentOnThreatClicked by rememberUpdatedState(onThreatClicked)
    val currentOnRegionClicked by rememberUpdatedState(onRegionClicked)
    val currentOnMapCenterChanged by rememberUpdatedState(onMapCenterChanged)
    val currentOnMapViewportChanged by rememberUpdatedState(onMapViewportChanged)
    val currentOnUserMapInteraction by rememberUpdatedState(onUserMapInteraction)

    val density = LocalDensity.current.density
    val regionsOverlay = remember(density) { UkraineRegionsOverlay(density) }
    val maskOverlay = remember(density) { UkraineMaskOverlay(density) }
    val darkTileFilter = remember { createDarkMapColorFilter() }
    val labelsTileFilter = remember { createLabelsColorFilter() }

    // Configure osmdroid user agent, larger tile caches, disk cache & Ukraine-only viewport bounds.
    val mapView = remember(context) {
        UkraineMapPreloader.ensureOsmDroidConfigured(context)
        val tileProvider = ResilientMapTileProvider(
            context.applicationContext,
            DarkMapTileSources.EsriDarkGrayBase
        ).apply {
            ensureCapacity(64)
        }
        MapView(context, tileProvider).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            minZoomLevel = 5.4
            maxZoomLevel = 16.0
            setScrollableAreaLimitDouble(
                BoundingBox(
                    UkraineBounds.VIEWPORT_NORTH_LAT,
                    UkraineBounds.VIEWPORT_EAST_LON,
                    UkraineBounds.VIEWPORT_SOUTH_LAT,
                    UkraineBounds.VIEWPORT_WEST_LON
                )
            )
            controller.setZoom(zoomLevel.coerceAtLeast(5.4))
            controller.setCenter(GeoPoint(centerLat, centerLon))
        }
    }

    // Separate transparent reference-labels layer rendered ABOVE colored regions so city names stay readable.
    val labelsInvalidationHandler = remember(mapView) { SimpleInvalidationHandler(mapView) }
    val labelsOverlay = remember(context, mapView, labelsInvalidationHandler) {
        val refProvider = ResilientMapTileProvider(
            context.applicationContext,
            DarkMapTileSources.EsriDarkGrayReference
        ).apply {
            ensureCapacity(64)
            tileRequestCompleteHandlers.add(labelsInvalidationHandler)
        }
        TilesOverlay(refProvider, context.applicationContext, false, false).apply {
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor = Color.TRANSPARENT
            setColorFilter(labelsTileFilter)
        }
    }

    val lastAppliedFocus = remember { intArrayOf(focusVersion) }
    val lastAnimateStartMs = remember { longArrayOf(0L) }
    val lastZoomIn = remember { intArrayOf(zoomInVersion) }
    val lastZoomOut = remember { intArrayOf(zoomOutVersion) }
    val lastDynamicOverlayKey = remember { arrayOf<Any?>(null) }
    val lastRenderKey = remember { arrayOf<Any?>(null) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }

        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                val center = mapView.mapCenter
                currentOnMapCenterChanged(center.latitude, center.longitude)
                if (android.os.SystemClock.elapsedRealtime() - lastAnimateStartMs[0] > 550L) {
                    currentOnMapViewportChanged(center.latitude, center.longitude, mapView.zoomLevelDouble)
                }
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                val center = mapView.mapCenter
                currentOnMapCenterChanged(center.latitude, center.longitude)
                if (android.os.SystemClock.elapsedRealtime() - lastAnimateStartMs[0] > 550L) {
                    currentOnMapViewportChanged(center.latitude, center.longitude, mapView.zoomLevelDouble)
                }
                return false
            }
        }
        mapView.addMapListener(listener)

        onDispose {
            mapView.removeMapListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            labelsInvalidationHandler.destroy()
            mapView.onDetach()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view ->
            val canvasBg = Color.rgb(11, 16, 23)
            view.setBackgroundColor(canvasBg)
            view.overlayManager.tilesOverlay?.apply {
                loadingBackgroundColor = Color.rgb(17, 24, 34)
                loadingLineColor = Color.rgb(27, 38, 54)
                setColorFilter(darkTileFilter)
            }

            if (lastAppliedFocus[0] != focusVersion) {
                lastAppliedFocus[0] = focusVersion
                lastAnimateStartMs[0] = android.os.SystemClock.elapsedRealtime()
                view.controller.animateTo(GeoPoint(centerLat, centerLon), zoomLevel.coerceAtLeast(5.4), 450L)
            }
            if (lastZoomIn[0] != zoomInVersion) {
                lastZoomIn[0] = zoomInVersion
                currentOnUserMapInteraction()
                view.controller.zoomIn()
            }
            if (lastZoomOut[0] != zoomOutVersion) {
                lastZoomOut[0] = zoomOutVersion
                currentOnUserMapInteraction()
                view.controller.zoomOut()
            }

            regionsOverlay.updateState(
                staticAtlas = staticAtlas,
                threatAtlas = threatAtlas,
                selectedPath = selectedRegionPath,
                isDark = isDark,
                isCrosshairPickerMode = isCrosshairPickerMode,
                onRegionTap = { polygon, info -> currentOnRegionClicked(polygon, info) },
                onUserTouch = { currentOnUserMapInteraction() }
            )
            maskOverlay.updateState(
                staticAtlas = staticAtlas,
                maskColor = canvasBg,
                isDark = isDark
            )

            val overlayKey = DynamicOverlayKey(
                trackedPoints = trackedPoints,
                threats = threats,
                showThreatsLayer = showThreatsLayer,
                warningRadiusKm = warningRadiusKm,
                highlightNearbyThreats = highlightNearbyThreats,
                gpsCoordinates = gpsCoordinates,
                isCrosshairPickerMode = isCrosshairPickerMode
            )

            var needsInvalidate = false
            if (lastDynamicOverlayKey[0] != overlayKey || view.overlays.isEmpty()) {
                lastDynamicOverlayKey[0] = overlayKey
                needsInvalidate = true
                view.overlays.clear()
                view.overlays.add(regionsOverlay)
                view.overlays.add(labelsOverlay)
                view.overlays.add(maskOverlay)

                if (!isCrosshairPickerMode) {
                    if (showThreatsLayer && highlightNearbyThreats) {
                        val effectiveRadiusKm = ThreatRadiusEvaluator.effectiveRadiusKm(warningRadiusKm)
                        val radiusCenters = buildList {
                            trackedPoints.forEach { status ->
                                val hasInside = threats.any {
                                    it.isWithinWarningRadius && it.nearestPointId == status.point.id
                                }
                                add(Triple(status.point.latitude, status.point.longitude, hasInside))
                            }
                            if (gpsCoordinates != null && trackedPoints.none { it.point.isGpsAnchor }) {
                                val hasInside = threats.any {
                                    it.isWithinWarningRadius &&
                                        GeoUtils.haversineDistanceKm(
                                            gpsCoordinates.first,
                                            gpsCoordinates.second,
                                            it.threat.latitude,
                                            it.threat.longitude
                                        ) <= effectiveRadiusKm
                                }
                                add(Triple(gpsCoordinates.first, gpsCoordinates.second, hasInside))
                            }
                        }

                        radiusCenters.forEach { (lat, lon, hasInside) ->
                            val circle = Polygon(view).apply {
                                points = Polygon.pointsAsCircle(
                                    GeoPoint(lat, lon),
                                    effectiveRadiusKm * 1000.0
                                )
                                if (hasInside) {
                                    fillPaint.color = Color.argb(30, 239, 68, 68)
                                    outlinePaint.color = Color.argb(215, 251, 191, 36)
                                    outlinePaint.strokeWidth = 2.4f * density
                                } else {
                                    fillPaint.color = Color.argb(18, 245, 158, 11)
                                    outlinePaint.color = Color.argb(155, 245, 158, 11)
                                    outlinePaint.strokeWidth = 1.8f * density
                                }
                                infoWindow = null
                                setOnClickListener { _, _, _ -> false }
                            }
                            view.overlays.add(circle)
                        }
                    }

                    gpsCoordinates?.let { (lat, lon) ->
                        val gpsMarker = Marker(view).apply {
                            position = GeoPoint(lat, lon)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = createGpsDotDrawable(context)
                            setInfoWindow(null)
                            setOnMarkerClickListener { _, _ -> false }
                        }
                        view.overlays.add(gpsMarker)
                    }

                    trackedPoints.forEach { status ->
                        val marker = Marker(view).apply {
                            position = GeoPoint(status.point.latitude, status.point.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = createPointDrawable(
                                context = context,
                                isAlertActive = status.isAlertActive,
                                isYellow = status.highestLevel == AlertLevel.YELLOW
                            )
                            title = status.point.name
                            setInfoWindow(null)
                            setOnMarkerClickListener { _, _ ->
                                currentOnPointClicked(status)
                                true
                            }
                        }
                        view.overlays.add(marker)
                    }

                    if (showThreatsLayer) {
                        threats.forEach { item ->
                            val threat = item.threat
                            val isNearbyHighlighted = highlightNearbyThreats && item.isWithinWarningRadius
                            if (threat.trail.isNotEmpty()) {
                                val trailLine = Polyline(view).apply {
                                    setPoints(
                                        threat.trail.map { GeoPoint(it.latitude, it.longitude) } +
                                            GeoPoint(threat.latitude, threat.longitude)
                                    )
                                    outlinePaint.color = if (isNearbyHighlighted) {
                                        Color.argb(230, 251, 191, 36)
                                    } else {
                                        Color.argb(175, 251, 146, 60)
                                    }
                                    outlinePaint.strokeWidth = if (isNearbyHighlighted) 5.5f else 4f
                                    infoWindow = null
                                    setOnClickListener { _, _, _ -> false }
                                }
                                view.overlays.add(trailLine)
                            }

                            val threatMarker = Marker(view).apply {
                                position = GeoPoint(threat.latitude, threat.longitude)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = createThreatDrawable(
                                    context = context,
                                    type = threat.type,
                                    isNearbyWarning = isNearbyHighlighted
                                )
                                rotation = -(threat.headingDeg?.toFloat() ?: 0f)
                                setInfoWindow(null)
                                setOnMarkerClickListener { _, _ ->
                                    currentOnThreatClicked(item)
                                    true
                                }
                            }
                            view.overlays.add(threatMarker)
                        }
                    }
                }
            }

            val renderKey = AtlasRenderKey(staticAtlas, threatAtlas, selectedRegionId, isDark)
            if (lastRenderKey[0] != renderKey) {
                lastRenderKey[0] = renderKey
                needsInvalidate = true
            }

            if (needsInvalidate) {
                view.invalidate()
            }
        }
    )
}

private data class DynamicOverlayKey(
    val trackedPoints: List<PointStatus>,
    val threats: List<ThreatWithProximity>,
    val showThreatsLayer: Boolean,
    val warningRadiusKm: Int,
    val highlightNearbyThreats: Boolean,
    val gpsCoordinates: Pair<Double, Double>?,
    val isCrosshairPickerMode: Boolean
)

private data class AtlasRenderKey(
    val staticAtlas: UkraineStaticMercatorAtlas?,
    val threatAtlas: UkraineThreatAtlas?,
    val selectedRegionId: String?,
    val isDark: Boolean
)

/**
 * Ultra-fast Ukraine region overlay:
 * - Overview (`zoom < 8.0`): 2 pre-rendered Bitmaps (0 Paths, 0 drawLines -> <1ms UI, <2ms GPU).
 * - Mid zoom (`8.0 <= zoom < 9.5`): pre-rendered fill Bitmap + 12×8 spatial cell `drawLines` borders.
 * - Close zoom (`zoom >= 9.5`): viewport-culled individual hromada Paths + 12×8 spatial cell `drawLines`.
 */
private class UkraineRegionsOverlay(
    private val density: Float
) : Overlay() {

    private var staticAtlas: UkraineStaticMercatorAtlas? = null
    private var threatAtlas: UkraineThreatAtlas? = null
    private var selectedPath: Path? = null
    private var isDark: Boolean = false
    private var isCrosshairPickerMode: Boolean = false
    private var onRegionTap: ((RegionPolygon, RegionThreatInfo?) -> Unit)? = null
    private var onUserTouch: (() -> Unit)? = null

    private val drawMatrix = Matrix()
    private val reuseTapGeo = GeoPoint(0.0, 0.0)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private val clearFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val elevatedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val highFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val criticalFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val hromadaBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val raionBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val oblastBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val selectedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val selectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val overviewAlertBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    fun updateState(
        staticAtlas: UkraineStaticMercatorAtlas?,
        threatAtlas: UkraineThreatAtlas?,
        selectedPath: Path?,
        isDark: Boolean,
        isCrosshairPickerMode: Boolean,
        onRegionTap: (RegionPolygon, RegionThreatInfo?) -> Unit,
        onUserTouch: () -> Unit
    ) {
        this.staticAtlas = staticAtlas
        this.threatAtlas = threatAtlas
        this.selectedPath = selectedPath
        this.isDark = isDark
        this.isCrosshairPickerMode = isCrosshairPickerMode
        this.onRegionTap = onRegionTap
        this.onUserTouch = onUserTouch
        refreshColors()
    }

    override fun onTouchEvent(event: MotionEvent?, mapView: MapView?): Boolean {
        if (event != null &&
            (event.actionMasked == MotionEvent.ACTION_DOWN ||
                event.actionMasked == MotionEvent.ACTION_POINTER_DOWN)
        ) {
            onUserTouch?.invoke()
        }
        return false
    }

    private fun refreshColors() {
        if (isDark) {
            clearFillPaint.color = Color.argb(24, 16, 185, 129)
            elevatedFillPaint.color = Color.argb(86, 245, 158, 11)
            highFillPaint.color = Color.argb(104, 239, 68, 68)
            criticalFillPaint.color = Color.argb(148, 200, 30, 30)
            selectedFillPaint.color = Color.argb(38, 56, 189, 248)
            selectedBorderPaint.color = Color.rgb(56, 189, 248)
        } else {
            clearFillPaint.color = Color.argb(20, 22, 163, 74)
            elevatedFillPaint.color = Color.argb(74, 245, 158, 11)
            highFillPaint.color = Color.argb(86, 220, 38, 38)
            criticalFillPaint.color = Color.argb(130, 153, 27, 27)
            selectedFillPaint.color = Color.argb(30, 37, 99, 235)
            selectedBorderPaint.color = Color.rgb(37, 99, 235)
        }
    }

    override fun draw(pCanvas: Canvas?, pProjection: Projection?) {
        val canvas = pCanvas ?: return
        val projection = pProjection ?: return
        val atlas = staticAtlas ?: return

        val scale = MercatorSpace.computeScreenMatrix(projection, drawMatrix)
        if (scale <= 0f) return
        val invScale = 1f / scale
        val zoom = projection.zoomLevel

        val saveCount = canvas.save()
        canvas.concat(drawMatrix)

        val threats = threatAtlas
        if (zoom < 8.0) {
            if (!isCrosshairPickerMode && threats != null) {
                canvas.drawBitmap(threats.overviewFillBitmap, null, MercatorSpace.boundsRect, bitmapPaint)
                // Draw localized (raion/hromada) alert borders subtly UNDER oblast borders.
                for (highlight in threats.overviewAlertHighlights) {
                    if (!highlight.isSubOblast) continue
                    overviewAlertBorderPaint.color = when (highlight.threatLevel) {
                        RegionThreatLevel.CRITICAL -> Color.argb(230, 255, 82, 82)
                        RegionThreatLevel.HIGH -> Color.argb(215, 248, 113, 113)
                        RegionThreatLevel.ELEVATED -> Color.argb(210, 251, 191, 36)
                        RegionThreatLevel.CLEAR -> continue
                    }
                    overviewAlertBorderPaint.strokeWidth = (0.50f * density * invScale).coerceAtLeast(0.001f)
                    canvas.drawPath(highlight.path, overviewAlertBorderPaint)
                }
            }
            // Crisp oblast borders stay on top of sub-oblast borders so oblasts remain clearly distinguishable.
            canvas.drawBitmap(atlas.overviewBordersBitmap, null, MercatorSpace.boundsRect, bitmapPaint)
            if (!isCrosshairPickerMode && threats != null) {
                for (highlight in threats.overviewAlertHighlights) {
                    if (highlight.isSubOblast) continue
                    overviewAlertBorderPaint.color = when (highlight.threatLevel) {
                        RegionThreatLevel.CRITICAL -> Color.rgb(255, 82, 82)
                        RegionThreatLevel.HIGH -> Color.rgb(248, 113, 113)
                        RegionThreatLevel.ELEVATED -> Color.rgb(251, 191, 36)
                        RegionThreatLevel.CLEAR -> continue
                    }
                    overviewAlertBorderPaint.strokeWidth = (0.95f * density * invScale).coerceAtLeast(0.001f)
                    canvas.drawPath(highlight.path, overviewAlertBorderPaint)
                }
            }
        } else {
            updateStrokePaints(zoom, invScale)

            val bbox = projection.boundingBox
            val north = bbox.latNorth.toFloat()
            val south = bbox.latSouth.toFloat()
            val east = bbox.lonEast.toFloat()
            val west = bbox.lonWest.toFloat()

            val viewMinX = MercatorSpace.toLocalX(west.toDouble())
            val viewMaxX = MercatorSpace.toLocalX(east.toDouble())
            val viewMinY = MercatorSpace.toLocalY(north.toDouble())
            val viewMaxY = MercatorSpace.toLocalY(south.toDouble())

            val cells = atlas.cells
            val showHromadas = zoom >= 9.2

            if (!isCrosshairPickerMode && threats != null) {
                if (showHromadas) {
                    val hromadas = atlas.sourceBoundaries.hromadas
                    val hromadaPaths = atlas.detailHromadaPaths
                    val threatLevels = threats.sourceSnapshot.hromadaThreats
                    for (i in cells.indices) {
                        val cell = cells[i]
                        if (cell.maxLocalX < viewMinX ||
                            cell.minLocalX > viewMaxX ||
                            cell.maxLocalY < viewMinY ||
                            cell.minLocalY > viewMaxY
                        ) {
                            continue
                        }
                        for (hIdx in cell.hromadaIndices) {
                            val h = hromadas[hIdx]
                            if (h.maxLat < south || h.minLat > north || h.maxLon < west || h.minLon > east) {
                                continue
                            }
                            val fillPaint = fillPaintForThreat(threatLevels.getOrElse(hIdx) { RegionThreatLevel.CLEAR })
                            canvas.drawPath(hromadaPaths[hIdx], fillPaint)
                        }
                    }
                } else {
                    val raions = atlas.sourceBoundaries.raions
                    val raionPaths = atlas.raionPaths
                    val threatLevels = threats.sourceSnapshot.raionThreats
                    for (i in raions.indices) {
                        val r = raions[i]
                        if (r.maxLat < south || r.minLat > north || r.maxLon < west || r.minLon > east) {
                            continue
                        }
                        val fillPaint = fillPaintForThreat(threatLevels.getOrElse(i) { RegionThreatLevel.CLEAR })
                        canvas.drawPath(raionPaths[i], fillPaint)
                    }
                }
            }

            if (showHromadas) {
                for (i in cells.indices) {
                    val cell = cells[i]
                    if (cell.maxLocalX < viewMinX ||
                        cell.minLocalX > viewMaxX ||
                        cell.maxLocalY < viewMinY ||
                        cell.minLocalY > viewMaxY
                    ) {
                        continue
                    }
                    if (cell.hromadaBorderLines.size >= 4) {
                        canvas.drawLines(cell.hromadaBorderLines, hromadaBorderPaint)
                    }
                }
            }

            // Pass 2: Raion & Oblast borders for visible 12×8 grid cells.
            for (i in cells.indices) {
                val cell = cells[i]
                if (cell.maxLocalX < viewMinX ||
                    cell.minLocalX > viewMaxX ||
                    cell.maxLocalY < viewMinY ||
                    cell.minLocalY > viewMaxY
                ) {
                    continue
                }
                if (cell.raionBorderLines.size >= 4) {
                    canvas.drawLines(cell.raionBorderLines, raionBorderPaint)
                }
                if (cell.oblastBorderLines.size >= 4) {
                    canvas.drawLines(cell.oblastBorderLines, oblastBorderPaint)
                }
            }
        }

        selectedPath?.let { sel ->
            canvas.drawPath(sel, selectedFillPaint)
            selectedBorderPaint.strokeWidth = (2.6f * density * invScale).coerceAtLeast(0.001f)
            canvas.drawPath(sel, selectedBorderPaint)
        }

        canvas.restoreToCount(saveCount)
    }

    private fun fillPaintForThreat(level: RegionThreatLevel): Paint = when (level) {
        RegionThreatLevel.CLEAR -> clearFillPaint
        RegionThreatLevel.ELEVATED -> elevatedFillPaint
        RegionThreatLevel.HIGH -> highFillPaint
        RegionThreatLevel.CRITICAL -> criticalFillPaint
    }

    private fun updateStrokePaints(zoom: Double, invScale: Float) {
        val hromadaPx = 1.15f * density
        hromadaBorderPaint.strokeWidth = (hromadaPx * invScale).coerceAtLeast(0.001f)
        hromadaBorderPaint.color = if (isDark) {
            Color.argb(120, 148, 175, 208)
        } else {
            Color.argb(135, 51, 65, 85)
        }

        val raionPx = if (zoom < 9.2) 1.25f * density else 1.45f * density
        raionBorderPaint.strokeWidth = (raionPx * invScale).coerceAtLeast(0.001f)
        raionBorderPaint.color = if (isDark) Color.argb(150, 176, 198, 224) else Color.argb(150, 30, 41, 59)

        val oblastPx = 2.1f * density
        oblastBorderPaint.strokeWidth = (oblastPx * invScale).coerceAtLeast(0.001f)
        oblastBorderPaint.color = if (isDark) Color.argb(190, 218, 230, 245) else Color.argb(185, 15, 23, 42)
    }

    override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
        if (isCrosshairPickerMode || e == null || mapView == null) return false
        val data = staticAtlas?.sourceBoundaries ?: return false
        val callback = onRegionTap ?: return false

        val tappedGeo = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt(), reuseTapGeo)
        val lat = tappedGeo.latitude
        val lon = tappedGeo.longitude
        val snapshot = threatAtlas?.sourceSnapshot
        val zoom = mapView.projection.zoomLevel

        when {
            zoom < 8.0 -> {
                // If user taps an alerted sub-oblast region whose border is visible on overview, select it.
                val subOblastHit = threatAtlas?.overviewAlertHighlights?.firstOrNull { highlight ->
                    highlight.isSubOblast &&
                        lat >= highlight.polygon.minLat &&
                        lat <= highlight.polygon.maxLat &&
                        lon >= highlight.polygon.minLon &&
                        lon <= highlight.polygon.maxLon &&
                        GeoUtils.isPointInRings(lat, lon, highlight.polygon.rings)
                }
                if (subOblastHit != null) {
                    callback(subOblastHit.polygon, subOblastHit.threatInfo)
                    return true
                }
                val idx = findSmallestMatchingRegionIndex(data.oblasts, lat, lon)
                if (idx >= 0) {
                    callback(data.oblasts[idx], snapshot?.oblastInfos?.getOrNull(idx))
                    return true
                }
            }
            zoom < 9.2 -> {
                val idx = findSmallestMatchingRegionIndex(data.raions, lat, lon)
                if (idx >= 0) {
                    callback(data.raions[idx], snapshot?.raionInfos?.getOrNull(idx))
                    return true
                }
            }
            else -> {
                val hromadas = data.hromadas
                for (i in hromadas.indices) {
                    val hromada = hromadas[i]
                    if (lat < hromada.minLat || lat > hromada.maxLat || lon < hromada.minLon || lon > hromada.maxLon) {
                        continue
                    }
                    if (GeoUtils.isPointInRings(lat, lon, hromada.rings)) {
                        val info = snapshot?.hromadaInfos?.getOrNull(i)
                        callback(hromada, info)
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun findSmallestMatchingRegionIndex(
        regions: List<RegionPolygon>,
        lat: Double,
        lon: Double
    ): Int {
        var bestIdx = -1
        var bestArea = Float.MAX_VALUE
        for (i in regions.indices) {
            val region = regions[i]
            if (lat < region.minLat || lat > region.maxLat || lon < region.minLon || lon > region.maxLon) {
                continue
            }
            if (GeoUtils.isPointInRings(lat, lon, region.rings)) {
                val area = (region.maxLat - region.minLat) * (region.maxLon - region.minLon)
                if (area < bestArea) {
                    bestArea = area
                    bestIdx = i
                }
            }
        }
        return bestIdx
    }
}

/**
 * Masks everything outside Ukraine's detailed international border using 4 outer rectangles +
 * a pre-baked mask Bitmap (zero SW Path Mask!), plus spatial-culled `drawLines` border stroke.
 */
private class UkraineMaskOverlay(
    private val density: Float
) : Overlay() {

    private var staticAtlas: UkraineStaticMercatorAtlas? = null
    private val drawMatrix = Matrix()

    private val maskBitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val maskWingPaint = Paint().apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun updateState(
        staticAtlas: UkraineStaticMercatorAtlas?,
        maskColor: Int,
        isDark: Boolean
    ) {
        this.staticAtlas = staticAtlas
        maskWingPaint.color = maskColor
        borderPaint.color = if (isDark) Color.rgb(78, 168, 255) else Color.rgb(30, 64, 175)
    }

    override fun draw(pCanvas: Canvas?, pProjection: Projection?) {
        val canvas = pCanvas ?: return
        val projection = pProjection ?: return
        val atlas = staticAtlas ?: return

        val scale = MercatorSpace.computeScreenMatrix(projection, drawMatrix)
        if (scale <= 0f) return
        borderPaint.strokeWidth = (1.55f * density / scale).coerceAtLeast(0.001f)

        val minX = MercatorSpace.minLocalX
        val maxX = MercatorSpace.maxLocalX
        val minY = MercatorSpace.minLocalY
        val maxY = MercatorSpace.maxLocalY

        val saveCount = canvas.save()
        canvas.concat(drawMatrix)

        canvas.drawRect(-1_200_000f, -1_200_000f, 1_200_000f, minY, maskWingPaint)
        canvas.drawRect(-1_200_000f, maxY, 1_200_000f, 1_200_000f, maskWingPaint)
        canvas.drawRect(-1_200_000f, minY, minX, maxY, maskWingPaint)
        canvas.drawRect(maxX, minY, 1_200_000f, maxY, maskWingPaint)

        canvas.drawBitmap(atlas.maskBitmap, null, MercatorSpace.boundsRect, maskBitmapPaint)

        val zoom = projection.zoomLevel
        if (zoom < 8.0) {
            if (atlas.overviewCountryLines.size >= 4) {
                canvas.drawLines(atlas.overviewCountryLines, borderPaint)
            }
        } else {
            val bbox = projection.boundingBox
            val viewMinX = MercatorSpace.toLocalX(bbox.lonWest)
            val viewMaxX = MercatorSpace.toLocalX(bbox.lonEast)
            val viewMinY = MercatorSpace.toLocalY(bbox.latNorth)
            val viewMaxY = MercatorSpace.toLocalY(bbox.latSouth)

            val cells = atlas.cells
            for (i in cells.indices) {
                val cell = cells[i]
                if (cell.maxLocalX < viewMinX ||
                    cell.minLocalX > viewMaxX ||
                    cell.maxLocalY < viewMinY ||
                    cell.minLocalY > viewMaxY
                ) {
                    continue
                }
                if (cell.countryBorderLines.size >= 4) {
                    canvas.drawLines(cell.countryBorderLines, borderPaint)
                }
            }
        }

        canvas.restoreToCount(saveCount)
    }
}

private fun createPointDrawable(
    context: Context,
    isAlertActive: Boolean,
    isYellow: Boolean
): Drawable {
    val size = 76
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    val coreColor = when {
        isAlertActive && isYellow -> Color.rgb(245, 158, 11)
        isAlertActive -> Color.rgb(239, 68, 68)
        else -> Color.rgb(34, 197, 94)
    }

    if (isAlertActive) {
        val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(85, Color.red(coreColor), Color.green(coreColor), Color.blue(coreColor))
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, size * 0.46f, pulsePaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coreColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(cx, cy, size * 0.40f, ringPaint)
    }

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(232, 238, 245)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 20f, borderPaint)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = coreColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, 15f, fillPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createGpsDotDrawable(context: Context): Drawable {
    val size = 44
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(85, 91, 178, 255)
    }
    canvas.drawCircle(cx, cy, 20f, halo)

    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(232, 238, 245) }
    canvas.drawCircle(cx, cy, 11f, ring)

    val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(91, 178, 255) }
    canvas.drawCircle(cx, cy, 8f, blue)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createThreatDrawable(
    context: Context,
    type: ThreatType,
    isNearbyWarning: Boolean
): Drawable {
    val size = if (isNearbyWarning) 108 else 80
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    if (isNearbyWarning) {
        val half = size / 2f
        // High-contrast beacon ring (white + crimson + amber) so nearby threats pop over both red alerts & dark tiles.
        val outerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 239, 68, 68)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(half, half, 50f, outerGlow)

        val whiteHaloRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 7.5f
        }
        canvas.drawCircle(half, half, 45f, whiteHaloRing)

        val redDangerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 45, 45)
            style = Paint.Style.STROKE
            strokeWidth = 4.2f
        }
        canvas.drawCircle(half, half, 45f, redDangerRing)

        val amberInnerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(251, 191, 36)
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        canvas.drawCircle(half, half, 39f, amberInnerRing)

        canvas.translate((size - 80f) / 2f, (size - 80f) / 2f)
    }

    val cx = 40f
    val cy = 40f

    val whiteOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6.5f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    val blackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(8, 10, 14)
        style = Paint.Style.FILL
    }
    val detailAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isNearbyWarning) Color.rgb(239, 68, 68) else Color.WHITE
        style = Paint.Style.FILL
    }
    val detailStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    when (type) {
        ThreatType.UAV -> {
            // Shahed-136: cropped delta wing with wingtip stabilizer endplates and 2-blade pusher propeller.
            val bodyPath = Path().apply {
                moveTo(cx, 10f)
                lineTo(cx + 3.2f, 20f)
                lineTo(63f, 48f)
                lineTo(63.5f, 58f)
                lineTo(60.5f, 58f)
                lineTo(59f, 55f)
                lineTo(cx + 4f, 55f)
                lineTo(cx + 3.2f, 59.5f)
                lineTo(cx - 3.2f, 59.5f)
                lineTo(cx - 4f, 55f)
                lineTo(21f, 55f)
                lineTo(19.5f, 58f)
                lineTo(16.5f, 58f)
                lineTo(17f, 48f)
                lineTo(cx - 3.2f, 20f)
                close()
            }
            val propShaftOuter = Paint(whiteOuterPaint).apply { strokeWidth = 7.0f }
            val propBladeOuter = Paint(whiteOuterPaint).apply { strokeWidth = 7.5f }
            val propShaftInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(8, 10, 14)
                style = Paint.Style.STROKE
                strokeWidth = 2.6f
                strokeCap = Paint.Cap.ROUND
            }
            val propBladeInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(8, 10, 14)
                style = Paint.Style.STROKE
                strokeWidth = 3.0f
                strokeCap = Paint.Cap.ROUND
            }
            val spineStroke = Paint(detailStrokePaint).apply { strokeWidth = 1.8f }
            val seamStroke = Paint(detailStrokePaint).apply { strokeWidth = 1.5f }
            val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }

            // Pass 1: White outer border for wing + propeller
            canvas.drawPath(bodyPath, whiteOuterPaint)
            canvas.drawLine(cx, 59f, cx, 64.5f, propShaftOuter)
            canvas.drawLine(cx - 10.5f, 64.5f, cx + 10.5f, 64.5f, propBladeOuter)

            // Pass 2: Jet-black silhouette fill
            canvas.drawPath(bodyPath, blackFillPaint)
            canvas.drawLine(cx, 59f, cx, 64.5f, propShaftInner)
            canvas.drawLine(cx - 10.5f, 64.5f, cx + 10.5f, 64.5f, propBladeInner)

            // Pass 3: Crisp internal spine, wingtip endplate seams & prop hub
            canvas.drawLine(cx, 19f, cx, 52f, spineStroke)
            canvas.drawLine(19.5f, 48.5f, 19.5f, 55.5f, seamStroke)
            canvas.drawLine(60.5f, 48.5f, 60.5f, 55.5f, seamStroke)
            canvas.drawCircle(cx, 64.5f, 1.7f, hubPaint)
            if (isNearbyWarning) {
                canvas.drawCircle(cx, 38f, 3.0f, detailAccentPaint)
            }
        }

        ThreatType.JET_SHAHED -> {
            // Shahed-238 (Jet Shahed): cropped delta wing with dorsal air intake and rear turbojet exhaust flame.
            val bodyPath = Path().apply {
                moveTo(cx, 10f)
                lineTo(cx + 3.2f, 20f)
                lineTo(63f, 48f)
                lineTo(63.5f, 58f)
                lineTo(60.5f, 58f)
                lineTo(59f, 55f)
                lineTo(cx + 4.4f, 55f)
                lineTo(cx + 3f, 60.5f)
                lineTo(cx - 3f, 60.5f)
                lineTo(cx - 4.4f, 55f)
                lineTo(21f, 55f)
                lineTo(19.5f, 58f)
                lineTo(16.5f, 58f)
                lineTo(17f, 48f)
                lineTo(cx - 3.2f, 20f)
                close()
            }
            val flameOuterPath = Path().apply {
                moveTo(cx - 3.6f, 60.5f)
                lineTo(cx, 72.5f)
                lineTo(cx + 3.6f, 60.5f)
                close()
            }
            val flameInnerPath = Path().apply {
                moveTo(cx - 1.8f, 60.5f)
                lineTo(cx, 68f)
                lineTo(cx + 1.8f, 60.5f)
                close()
            }
            val intakePath = Path().apply {
                moveTo(cx, 25.5f)
                lineTo(cx + 3.2f, 31f)
                lineTo(cx + 2.4f, 38.5f)
                lineTo(cx - 2.4f, 38.5f)
                lineTo(cx - 3.2f, 31f)
                close()
            }
            val flameOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 87, 34)
                style = Paint.Style.FILL
            }
            val flameInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 193, 7)
                style = Paint.Style.FILL
            }
            val whiteFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val seamStroke = Paint(detailStrokePaint).apply { strokeWidth = 1.5f }

            // Pass 1: White outer border around jet flame & airframe
            canvas.drawPath(flameOuterPath, whiteOuterPaint)
            canvas.drawPath(bodyPath, whiteOuterPaint)

            // Pass 2: Turbojet exhaust flame + black airframe silhouette
            canvas.drawPath(flameOuterPath, flameOuterPaint)
            canvas.drawPath(flameInnerPath, flameInnerPaint)
            canvas.drawPath(bodyPath, blackFillPaint)

            // Pass 3: Dorsal air intake scoop + wingtip endplate seams
            canvas.drawPath(intakePath, whiteFillPaint)
            canvas.drawLine(19.5f, 48.5f, 19.5f, 55.5f, seamStroke)
            canvas.drawLine(60.5f, 48.5f, 60.5f, 55.5f, seamStroke)
            if (isNearbyWarning) {
                canvas.drawCircle(cx, 46.5f, 3.0f, detailAccentPaint)
            }
        }

        ThreatType.FPV -> {
            // Quadcopter X-frame with 4 circular rotor guards + central chassis.
            val rPos = 17f
            val rotorRadius = 9.5f
            val armPaintOuter = Paint(whiteOuterPaint).apply { strokeWidth = 10.5f }
            val armPaintInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(8, 10, 14)
                style = Paint.Style.STROKE
                strokeWidth = 4.6f
                strokeCap = Paint.Cap.ROUND
            }
            val bodyPath = Path().apply {
                moveTo(cx - 5.5f, cy - 13f)
                lineTo(cx + 5.5f, cy - 13f)
                lineTo(cx + 6.5f, cy + 12f)
                lineTo(cx - 6.5f, cy + 12f)
                close()
            }

            // Pass 1: White outer border for arms, rotors, and body
            canvas.drawLine(cx - rPos, cy - rPos, cx + rPos, cy + rPos, armPaintOuter)
            canvas.drawLine(cx + rPos, cy - rPos, cx - rPos, cy + rPos, armPaintOuter)
            canvas.drawCircle(cx - rPos, cy - rPos, rotorRadius, whiteOuterPaint)
            canvas.drawCircle(cx + rPos, cy - rPos, rotorRadius, whiteOuterPaint)
            canvas.drawCircle(cx - rPos, cy + rPos, rotorRadius, whiteOuterPaint)
            canvas.drawCircle(cx + rPos, cy + rPos, rotorRadius, whiteOuterPaint)
            canvas.drawPath(bodyPath, whiteOuterPaint)

            // Pass 2: Jet-black silhouette fill
            canvas.drawLine(cx - rPos, cy - rPos, cx + rPos, cy + rPos, armPaintInner)
            canvas.drawLine(cx + rPos, cy - rPos, cx - rPos, cy + rPos, armPaintInner)
            canvas.drawCircle(cx - rPos, cy - rPos, rotorRadius, blackFillPaint)
            canvas.drawCircle(cx + rPos, cy - rPos, rotorRadius, blackFillPaint)
            canvas.drawCircle(cx - rPos, cy + rPos, rotorRadius, blackFillPaint)
            canvas.drawCircle(cx + rPos, cy + rPos, rotorRadius, blackFillPaint)
            canvas.drawPath(bodyPath, blackFillPaint)

            // Crisp white rotor hub dots & front camera accent
            val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx - rPos, cy - rPos, 2.5f, hubPaint)
            canvas.drawCircle(cx + rPos, cy - rPos, 2.5f, hubPaint)
            canvas.drawCircle(cx - rPos, cy + rPos, 2.5f, hubPaint)
            canvas.drawCircle(cx + rPos, cy + rPos, 2.5f, hubPaint)
            canvas.drawCircle(cx, cy - 8f, 2.6f, detailAccentPaint)
        }

        ThreatType.RECON -> {
            // High-aspect-ratio reconnaissance glider UAV (wide wingspan + slender fuselage + V-tail).
            val path = Path().apply {
                moveTo(cx, 13f)
                lineTo(cx + 3.5f, 28f)
                lineTo(68f, 33f)
                lineTo(68f, 40f)
                lineTo(cx + 3.5f, 39f)
                lineTo(cx + 3f, 56f)
                lineTo(cx + 12f, 64f)
                lineTo(cx + 10f, 67f)
                lineTo(cx, 62f)
                lineTo(cx - 10f, 67f)
                lineTo(cx - 12f, 64f)
                lineTo(cx - 3f, 56f)
                lineTo(cx - 3.5f, 39f)
                lineTo(12f, 40f)
                lineTo(12f, 33f)
                lineTo(cx - 3.5f, 28f)
                close()
            }
            canvas.drawPath(path, whiteOuterPaint)
            canvas.drawPath(path, blackFillPaint)
            canvas.drawCircle(cx, 25f, 2.4f, detailAccentPaint)
        }

        ThreatType.CRUISE_MISSILE -> {
            // Streamlined cruise missile: needle nose, ultra-thin fuselage (diameter 6 units),
            // straight rectangular pop-out wings (span 16..64), cruciform tail fin cross (+ shape).
            val bodyWidth = 3.2f
            val path = Path().apply {
                moveTo(cx, 8f)
                lineTo(cx + bodyWidth, 18f)
                lineTo(cx + bodyWidth, 34f)
                lineTo(65f, 35f)
                lineTo(65f, 39.5f)
                lineTo(cx + bodyWidth, 39f)
                lineTo(cx + bodyWidth, 61f)
                lineTo(cx + 9f, 66f)
                lineTo(cx + 8f, 68f)
                lineTo(cx + bodyWidth, 66.5f)
                lineTo(cx + bodyWidth, 69f)
                lineTo(cx - bodyWidth, 69f)
                lineTo(cx - bodyWidth, 66.5f)
                lineTo(cx - 8f, 68f)
                lineTo(cx - 9f, 66f)
                lineTo(cx - bodyWidth, 61f)
                lineTo(cx - bodyWidth, 39f)
                lineTo(15f, 39.5f)
                lineTo(15f, 35f)
                lineTo(cx - bodyWidth, 34f)
                lineTo(cx - bodyWidth, 18f)
                close()
            }
            canvas.drawPath(path, whiteOuterPaint)
            canvas.drawPath(path, blackFillPaint)
            canvas.drawLine(cx, 16f, cx, 63f, detailStrokePaint)
            canvas.drawLine(cx - bodyWidth, 37f, cx + bodyWidth, 37f, detailStrokePaint)
        }

        ThreatType.BALLISTIC -> {
            // Heavy ballistic rocket: sharp warhead cone, thick booster, and swept base fins.
            val path = Path().apply {
                moveTo(cx, 10f)
                lineTo(cx + 7.2f, 25f)
                lineTo(cx + 7.2f, 50f)
                lineTo(cx + 16f, 62f)
                lineTo(cx + 16f, 66f)
                lineTo(cx + 6.5f, 63f)
                lineTo(cx + 5f, 67f)
                lineTo(cx - 5f, 67f)
                lineTo(cx - 6.5f, 63f)
                lineTo(cx - 16f, 66f)
                lineTo(cx - 16f, 62f)
                lineTo(cx - 7.2f, 50f)
                lineTo(cx - 7.2f, 25f)
                close()
            }
            canvas.drawPath(path, whiteOuterPaint)
            canvas.drawPath(path, blackFillPaint)
            // Stage separation band + core dot
            canvas.drawLine(cx - 5.5f, 29f, cx + 5.5f, 29f, detailStrokePaint)
            canvas.drawCircle(cx, 44f, 3.0f, detailAccentPaint)
        }

        ThreatType.KAB -> {
            // Guided aerial bomb (KAB): stubby blunt teardrop bomb body with folding dorsal glide wings (UMPK)
            // and cross-shaped empennage tail fins with rear ring/box stabilizer.
            val bodyHalf = 7.5f
            val path = Path().apply {
                moveTo(cx, 16f)
                cubicTo(cx + 6f, 16f, cx + bodyHalf, 21f, cx + bodyHalf, 27f)
                lineTo(cx + bodyHalf, 34f)
                lineTo(63f, 40f)
                lineTo(61f, 44.5f)
                lineTo(cx + bodyHalf, 41f)
                lineTo(cx + 5.5f, 54f)
                lineTo(cx + 13f, 60f)
                lineTo(cx + 13f, 64f)
                lineTo(cx + 4.5f, 62.5f)
                lineTo(cx + 3.5f, 65f)
                lineTo(cx - 3.5f, 65f)
                lineTo(cx - 4.5f, 62.5f)
                lineTo(cx - 13f, 64f)
                lineTo(cx - 13f, 60f)
                lineTo(cx - 5.5f, 54f)
                lineTo(cx - bodyHalf, 41f)
                lineTo(19f, 44.5f)
                lineTo(17f, 40f)
                lineTo(cx - bodyHalf, 34f)
                lineTo(cx - bodyHalf, 27f)
                cubicTo(cx - bodyHalf, 21f, cx - 6f, 16f, cx, 16f)
                close()
            }
            canvas.drawPath(path, whiteOuterPaint)
            canvas.drawPath(path, blackFillPaint)
            canvas.drawLine(cx - 5.5f, 23f, cx + 5.5f, 23f, detailStrokePaint)
            canvas.drawCircle(cx, 32f, 3.2f, detailAccentPaint)
            canvas.drawLine(cx - 3.5f, 57f, cx + 3.5f, 57f, detailStrokePaint)
        }

        ThreatType.AVIATION -> {
            // Tactical aviation (Su-34/Su-35 / MiG-31K): long needle radome, wide blended wing-body (LERX),
            // broad swept delta wings, distinct twin engine nacelles, and protruding tail boom / stinger.
            val path = Path().apply {
                moveTo(cx, 11f)
                lineTo(cx + 4.5f, 24f)
                lineTo(cx + 7.5f, 32f)
                lineTo(67f, 47f)
                lineTo(65f, 52f)
                lineTo(cx + 7f, 47f)
                lineTo(cx + 6f, 55f)
                lineTo(cx + 16f, 63f)
                lineTo(cx + 14f, 67f)
                lineTo(cx + 4.5f, 63f)
                lineTo(cx + 2.5f, 66f)
                lineTo(cx, 63f)
                lineTo(cx - 2.5f, 66f)
                lineTo(cx - 4.5f, 63f)
                lineTo(cx - 14f, 67f)
                lineTo(cx - 16f, 63f)
                lineTo(cx - 6f, 55f)
                lineTo(cx - 7f, 47f)
                lineTo(15f, 52f)
                lineTo(13f, 47f)
                lineTo(cx - 7.5f, 32f)
                lineTo(cx - 4.5f, 24f)
                close()
            }
            canvas.drawPath(path, whiteOuterPaint)
            canvas.drawPath(path, blackFillPaint)
            // Cockpit canopy accent
            canvas.drawLine(cx, 19f, cx, 28f, detailStrokePaint)
        }

        ThreatType.UNKNOWN -> {
            // Crisp 4-point radar diamond blip with inner target dot.
            val path = Path().apply {
                moveTo(cx, 15f)
                lineTo(65f, cy)
                lineTo(cx, 65f)
                lineTo(15f, cy)
                close()
            }
            canvas.drawPath(path, whiteOuterPaint)
            canvas.drawPath(path, blackFillPaint)
            canvas.drawCircle(cx, cy, 4.5f, detailAccentPaint)
        }
    }

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Applies a subtle cool midnight-navy tint to Esri Dark Gray Canvas tiles so neutral charcoal
 * grays harmonize with the `#0B1017` app background and `#4EA8FF` Ukraine border while keeping
 * city labels crisp and subtle.
 */
private fun createDarkMapColorFilter(): ColorMatrixColorFilter {
    val coolMidnightTint = ColorMatrix(
        floatArrayOf(
            0.76f, 0f, 0f, 0f, 0f,
            0f, 0.84f, 0f, 0f, 2f,
            0f, 0f, 1.00f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    return ColorMatrixColorFilter(coolMidnightTint)
}

/**
 * Boosts label brightness while keeping their dark halo intact so city names stay crisp
 * and legible when drawn on top of colored threat/alert overlays.
 */
private fun createLabelsColorFilter(): ColorMatrixColorFilter {
    val crispLabels = ColorMatrix(
        floatArrayOf(
            1.25f, 0f, 0f, 0f, -7.5f,
            0f, 1.25f, 0f, 0f, -7.5f,
            0f, 0f, 1.25f, 0f, -7.5f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    return ColorMatrixColorFilter(crispLabels)
}
