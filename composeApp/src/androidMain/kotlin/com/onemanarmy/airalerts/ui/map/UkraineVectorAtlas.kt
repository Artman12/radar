package com.onemanarmy.airalerts.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.onemanarmy.airalerts.domain.model.OccupiedTerritoriesSnapshot
import com.onemanarmy.airalerts.domain.model.OccupiedZoneCategory
import com.onemanarmy.airalerts.domain.model.RegionPolygon
import com.onemanarmy.airalerts.domain.model.RegionThreatInfo
import com.onemanarmy.airalerts.domain.model.RegionThreatLevel
import com.onemanarmy.airalerts.domain.model.RegionThreatSnapshot
import com.onemanarmy.airalerts.domain.model.UkraineBounds
import com.onemanarmy.airalerts.domain.model.UkraineMapBoundaries
import kotlinx.coroutines.launch
import org.osmdroid.util.TileSystem
import org.osmdroid.views.Projection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Web Mercator projection helpers in a Ukraine-centered local coordinate space (`2^22` units).
 * 1 local unit ≈ 6.36 meters at 48°N.
 */
internal object MercatorSpace {
    const val SCALE = 4_194_304.0 // 2^22 -> high Float precision at all zooms 5..18
    const val DETAIL_MIN_DIST = 12f // ~76m (<1px up to zoom 12.5)
    const val COUNTRY_OVERVIEW_MIN_DIST = 280f // ~1.8km -> ~1,100 segments (~17 KB)

    private val centerX01 = lonToX01(UkraineBounds.CENTER_LON)
    private val centerY01 = latToY01(UkraineBounds.CENTER_LAT)

    val minLocalX = toLocalX(UkraineBounds.MIN_LON)
    val maxLocalX = toLocalX(UkraineBounds.MAX_LON)
    val minLocalY = toLocalY(UkraineBounds.MAX_LAT) // North is smaller Y in Mercator
    val maxLocalY = toLocalY(UkraineBounds.MIN_LAT) // South is larger Y in Mercator
    val boundsRect = RectF(minLocalX, minLocalY, maxLocalX, maxLocalY)

    fun lonToX01(lon: Double): Double = (lon + 180.0) / 360.0

    fun latToY01(lat: Double): Double {
        val clamped = lat.coerceIn(-85.05112878, 85.05112878)
        val sinLat = sin(clamped * PI / 180.0)
        return 0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)
    }

    fun toLocalX(lon: Double): Float = ((lonToX01(lon) - centerX01) * SCALE).toFloat()

    fun toLocalY(lat: Double): Float = ((latToY01(lat) - centerY01) * SCALE).toFloat()

    fun toLocalXDouble(lon: Double): Double = (lonToX01(lon) - centerX01) * SCALE

    fun toLocalYDouble(lat: Double): Double = (latToY01(lat) - centerY01) * SCALE

    fun computeScreenMatrix(projection: Projection, outMatrix: Matrix): Float {
        val mapSize = TileSystem.MapSize(projection.zoomLevel)
        val scaleDouble = mapSize / SCALE
        val scale = scaleDouble.toFloat()

        val center = projection.currentCenter
        val cLat = center.latitude
        val cLon = center.longitude
        val sx = projection.getLongPixelXFromLongitude(cLon).toDouble()
        val sy = projection.getLongPixelYFromLatitude(cLat).toDouble()

        val tx = (sx - toLocalXDouble(cLon) * scaleDouble).toFloat()
        val ty = (sy - toLocalYDouble(cLat) * scaleDouble).toFloat()

        outMatrix.setScale(scale, scale)
        outMatrix.postTranslate(tx, ty)
        return scale
    }

    fun projectRings(rings: List<FloatArray>, minStepDist: Float): List<FloatArray> {
        if (rings.isEmpty()) return emptyList()
        val result = ArrayList<FloatArray>(rings.size)
        for (ring in rings) {
            val size = ring.size
            if (size < 6) continue

            if (minStepDist <= 0f) {
                val out = FloatArray(size)
                var idx = 0
                while (idx < size) {
                    out[idx] = toLocalX(ring[idx + 1].toDouble())
                    out[idx + 1] = toLocalY(ring[idx].toDouble())
                    idx += 2
                }
                result.add(out)
            } else {
                val firstX = toLocalX(ring[1].toDouble())
                val firstY = toLocalY(ring[0].toDouble())
                val temp = FloatArray(size)
                temp[0] = firstX
                temp[1] = firstY
                var outLen = 2
                var lastX = firstX
                var lastY = firstY
                var idx = 2
                val lastIdx = size - 2
                while (idx < size) {
                    val x = toLocalX(ring[idx + 1].toDouble())
                    val y = toLocalY(ring[idx].toDouble())
                    if (idx == lastIdx || abs(x - lastX) + abs(y - lastY) >= minStepDist) {
                        temp[outLen++] = x
                        temp[outLen++] = y
                        lastX = x
                        lastY = y
                    }
                    idx += 2
                }
                if (outLen >= 6) {
                    result.add(temp.copyOf(outLen))
                }
            }
        }
        return result
    }

    fun ringsToLineSegments(ringsList: List<List<FloatArray>>): FloatArray {
        var totalSegments = 0
        for (rings in ringsList) {
            for (ring in rings) {
                val pts = ring.size / 2
                if (pts >= 2) totalSegments += pts
            }
        }
        val out = FloatArray(totalSegments * 4)
        var outIdx = 0
        for (rings in ringsList) {
            for (ring in rings) {
                val size = ring.size
                if (size < 4) continue
                var i = 0
                while (i + 2 < size) {
                    out[outIdx++] = ring[i]
                    out[outIdx++] = ring[i + 1]
                    out[outIdx++] = ring[i + 2]
                    out[outIdx++] = ring[i + 3]
                    i += 2
                }
                out[outIdx++] = ring[size - 2]
                out[outIdx++] = ring[size - 1]
                out[outIdx++] = ring[0]
                out[outIdx++] = ring[1]
            }
        }
        return if (outIdx == out.size) out else out.copyOf(outIdx)
    }

    fun appendLocalRingsToPath(rings: List<FloatArray>, targetPath: Path) {
        for (ring in rings) {
            val size = ring.size
            if (size < 6) continue
            targetPath.moveTo(ring[0], ring[1])
            var idx = 2
            while (idx < size) {
                targetPath.lineTo(ring[idx], ring[idx + 1])
                idx += 2
            }
            targetPath.close()
        }
    }
}

/**
 * Spatial cell (12×8 grid over Ukraine) holding only the geometry whose center/midpoint falls in this cell,
 * with bounding box expanded to cover every contained polygon & segment (no clipping, no duplicates).
 */
internal class SpatialVectorCell(
    val minLocalX: Float,
    val maxLocalX: Float,
    val minLocalY: Float,
    val maxLocalY: Float,
    val hromadaIndices: IntArray,
    val hromadaBorderLines: FloatArray,
    val raionBorderLines: FloatArray,
    val oblastBorderLines: FloatArray,
    val countryBorderLines: FloatArray
)

/**
 * Immutable pre-projected Mercator vector & bitmap atlas:
 * - Overview (`zoom < 8.0`): pre-rendered Bitmaps (`overviewBordersBitmap` + `maskBitmap`) = zero DisplayList bloat!
 * - Mid/High (`zoom >= 8.0`): 12×8 spatial grid cells with simplified (`~76m`) segments + individual hromada Paths.
 */
internal class UkraineStaticMercatorAtlas private constructor(
    val sourceBoundaries: UkraineMapBoundaries,
    val isDark: Boolean,
    val maskBitmap: Bitmap,
    val overviewBordersBitmap: Bitmap,
    val overviewCountryLines: FloatArray,
    val cells: List<SpatialVectorCell>,
    val oblastPaths: Array<Path>,
    val raionPaths: Array<Path>,
    val detailHromadaPaths: Array<Path>
) {
    fun buildThreatAtlas(
        snapshot: RegionThreatSnapshot,
        previous: UkraineThreatAtlas?
    ): UkraineThreatAtlas {
        val alertHighlights = buildOverviewAlertHighlights(snapshot)

        // Reuse existing overview fill bitmap if alert & threat colors didn't change.
        if (previous != null &&
            previous.isDark == isDark &&
            previous.sourceSnapshot.oblastThreats == snapshot.oblastThreats &&
            previous.sourceSnapshot.raionThreats == snapshot.raionThreats &&
            previous.sourceSnapshot.hromadaThreats == snapshot.hromadaThreats &&
            previous.sourceSnapshot.oblastInfos == snapshot.oblastInfos &&
            previous.sourceSnapshot.raionInfos == snapshot.raionInfos &&
            previous.sourceSnapshot.hromadaInfos == snapshot.hromadaInfos
        ) {
            return UkraineThreatAtlas(
                sourceSnapshot = snapshot,
                isDark = isDark,
                overviewFillBitmap = previous.overviewFillBitmap,
                overviewAlertHighlights = alertHighlights
            )
        }

        val overviewBitmap = Bitmap.createBitmap(FILL_BITMAP_W, FILL_BITMAP_H, Bitmap.Config.ARGB_8888)
        val bmpCanvas = Canvas(overviewBitmap)
        val m = Matrix()
        val sx = FILL_BITMAP_W.toFloat() / (MercatorSpace.maxLocalX - MercatorSpace.minLocalX)
        val sy = FILL_BITMAP_H.toFloat() / (MercatorSpace.maxLocalY - MercatorSpace.minLocalY)
        m.setTranslate(-MercatorSpace.minLocalX, -MercatorSpace.minLocalY)
        m.postScale(sx, sy)
        bmpCanvas.setMatrix(m)

        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isDark) Color.argb(24, 16, 185, 129) else Color.argb(22, 22, 163, 74)
        }
        val elevatedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isDark) Color.argb(86, 245, 158, 11) else Color.argb(74, 245, 158, 11)
        }
        val highPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isDark) Color.argb(104, 239, 68, 68) else Color.argb(86, 220, 38, 38)
        }
        val criticalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isDark) Color.argb(148, 200, 30, 30) else Color.argb(130, 153, 27, 27)
        }

        fun paintFor(level: RegionThreatLevel): Paint = when (level) {
            RegionThreatLevel.CLEAR -> clearPaint
            RegionThreatLevel.ELEVATED -> elevatedPaint
            RegionThreatLevel.HIGH -> highPaint
            RegionThreatLevel.CRITICAL -> criticalPaint
        }

        val threats = snapshot.oblastThreats
        for (i in oblastPaths.indices) {
            val level = threats.getOrElse(i) { RegionThreatLevel.CLEAR }
            bmpCanvas.drawPath(oblastPaths[i], paintFor(level))
        }

        // Fill standalone localized alerts (raions/hromadas) inside otherwise-clear oblasts.
        for (highlight in alertHighlights) {
            if (highlight.isSubOblast) {
                bmpCanvas.drawPath(highlight.path, paintFor(highlight.threatLevel))
            }
        }

        return UkraineThreatAtlas(
            sourceSnapshot = snapshot,
            isDark = isDark,
            overviewFillBitmap = overviewBitmap,
            overviewAlertHighlights = alertHighlights
        )
    }

    private fun buildOverviewAlertHighlights(snapshot: RegionThreatSnapshot): List<OverviewAlertHighlight> {
        val result = ArrayList<OverviewAlertHighlight>()

        for (i in sourceBoundaries.oblasts.indices) {
            val info = snapshot.oblastInfos.getOrNull(i) ?: continue
            if (info.isOblastWideAlert && info.threatLevel != RegionThreatLevel.CLEAR) {
                result.add(
                    OverviewAlertHighlight(
                        polygon = sourceBoundaries.oblasts[i],
                        threatInfo = info,
                        path = oblastPaths[i],
                        threatLevel = info.threatLevel,
                        isSubOblast = false
                    )
                )
            }
        }

        for (i in sourceBoundaries.raions.indices) {
            val info = snapshot.raionInfos.getOrNull(i) ?: continue
            if (info.isStandaloneAlert && info.threatLevel != RegionThreatLevel.CLEAR) {
                result.add(
                    OverviewAlertHighlight(
                        polygon = sourceBoundaries.raions[i],
                        threatInfo = info,
                        path = raionPaths[i],
                        threatLevel = info.threatLevel,
                        isSubOblast = true
                    )
                )
            }
        }

        for (i in sourceBoundaries.hromadas.indices) {
            val info = snapshot.hromadaInfos.getOrNull(i) ?: continue
            if (info.isStandaloneAlert && info.threatLevel != RegionThreatLevel.CLEAR) {
                result.add(
                    OverviewAlertHighlight(
                        polygon = sourceBoundaries.hromadas[i],
                        threatInfo = info,
                        path = detailHromadaPaths[i],
                        threatLevel = info.threatLevel,
                        isSubOblast = true
                    )
                )
            }
        }

        return result
    }

    fun getSelectedRegionPath(regionId: String?): Path? {
        if (regionId == null) return null
        return when {
            regionId.startsWith("oblast_") -> {
                val index = regionId.removePrefix("oblast_").toIntOrNull()
                    ?: sourceBoundaries.oblasts.indexOfFirst { it.id == regionId }
                oblastPaths.getOrNull(index)
            }
            regionId.startsWith("raion_") -> {
                val index = regionId.removePrefix("raion_").toIntOrNull()
                    ?: sourceBoundaries.raions.indexOfFirst { it.id == regionId }
                raionPaths.getOrNull(index)
            }
            else -> {
                val index = regionId.removePrefix("hromada_").toIntOrNull()
                    ?: sourceBoundaries.hromadas.indexOfFirst { it.id == regionId }
                detailHromadaPaths.getOrNull(index)
            }
        }
    }

    companion object {
        private const val GRID_COLS = 12
        private const val GRID_ROWS = 8
        private const val FILL_BITMAP_W = 2048
        private const val FILL_BITMAP_H = 1386
        private const val BORDER_BITMAP_W = 2560
        private const val BORDER_BITMAP_H = 1732

        private class CellAccumulator {
            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            val hromadaIndices = ArrayList<Int>(24)
            var hromadaFloats = FloatArray(1024)
            var hromadaCount = 0
            var raionFloats = FloatArray(512)
            var raionCount = 0
            var oblastFloats = FloatArray(512)
            var oblastCount = 0
            var countryFloats = FloatArray(256)
            var countryCount = 0

            fun expandBounds(x0: Float, y0: Float, x1: Float, y1: Float) {
                minX = min(minX, min(x0, x1))
                maxX = max(maxX, max(x0, x1))
                minY = min(minY, min(y0, y1))
                maxY = max(maxY, max(y0, y1))
            }

            fun appendHromadaSeg(x0: Float, y0: Float, x1: Float, y1: Float) {
                expandBounds(x0, y0, x1, y1)
                if (hromadaCount + 4 > hromadaFloats.size) {
                    hromadaFloats = hromadaFloats.copyOf(hromadaFloats.size * 2)
                }
                hromadaFloats[hromadaCount++] = x0
                hromadaFloats[hromadaCount++] = y0
                hromadaFloats[hromadaCount++] = x1
                hromadaFloats[hromadaCount++] = y1
            }

            fun appendRaionSeg(x0: Float, y0: Float, x1: Float, y1: Float) {
                expandBounds(x0, y0, x1, y1)
                if (raionCount + 4 > raionFloats.size) {
                    raionFloats = raionFloats.copyOf(raionFloats.size * 2)
                }
                raionFloats[raionCount++] = x0
                raionFloats[raionCount++] = y0
                raionFloats[raionCount++] = x1
                raionFloats[raionCount++] = y1
            }

            fun appendOblastSeg(x0: Float, y0: Float, x1: Float, y1: Float) {
                expandBounds(x0, y0, x1, y1)
                if (oblastCount + 4 > oblastFloats.size) {
                    oblastFloats = oblastFloats.copyOf(oblastFloats.size * 2)
                }
                oblastFloats[oblastCount++] = x0
                oblastFloats[oblastCount++] = y0
                oblastFloats[oblastCount++] = x1
                oblastFloats[oblastCount++] = y1
            }

            fun appendCountrySeg(x0: Float, y0: Float, x1: Float, y1: Float) {
                expandBounds(x0, y0, x1, y1)
                if (countryCount + 4 > countryFloats.size) {
                    countryFloats = countryFloats.copyOf(countryFloats.size * 2)
                }
                countryFloats[countryCount++] = x0
                countryFloats[countryCount++] = y0
                countryFloats[countryCount++] = x1
                countryFloats[countryCount++] = y1
            }
        }

        private fun cellIndexForLocalPoint(x: Float, y: Float, spanX: Float, spanY: Float): Int {
            val col = (((x - MercatorSpace.minLocalX) / spanX) * GRID_COLS).toInt().coerceIn(0, GRID_COLS - 1)
            val row = (((y - MercatorSpace.minLocalY) / spanY) * GRID_ROWS).toInt().coerceIn(0, GRID_ROWS - 1)
            return row * GRID_COLS + col
        }

        private fun binRingsSegments(
            rings: List<FloatArray>,
            accumulators: Array<CellAccumulator>,
            spanX: Float,
            spanY: Float,
            addSegment: CellAccumulator.(Float, Float, Float, Float) -> Unit
        ) {
            for (ring in rings) {
                val size = ring.size
                if (size < 4) continue
                var i = 0
                while (i + 2 < size) {
                    val x0 = ring[i]
                    val y0 = ring[i + 1]
                    val x1 = ring[i + 2]
                    val y1 = ring[i + 3]
                    val cellIdx = cellIndexForLocalPoint((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, spanX, spanY)
                    accumulators[cellIdx].addSegment(x0, y0, x1, y1)
                    i += 2
                }
                val x0 = ring[size - 2]
                val y0 = ring[size - 1]
                val x1 = ring[0]
                val y1 = ring[1]
                val cellIdx = cellIndexForLocalPoint((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, spanX, spanY)
                accumulators[cellIdx].addSegment(x0, y0, x1, y1)
            }
        }

        private fun buildMaskBitmap(countryRings: List<FloatArray>, maskColor: Int): Bitmap {
            val bmp = Bitmap.createBitmap(FILL_BITMAP_W, FILL_BITMAP_H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(maskColor)

            val m = Matrix()
            val sx = FILL_BITMAP_W.toFloat() / (MercatorSpace.maxLocalX - MercatorSpace.minLocalX)
            val sy = FILL_BITMAP_H.toFloat() / (MercatorSpace.maxLocalY - MercatorSpace.minLocalY)
            m.setTranslate(-MercatorSpace.minLocalX, -MercatorSpace.minLocalY)
            m.postScale(sx, sy)
            c.setMatrix(m)

            val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            val countryPath = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                MercatorSpace.appendLocalRingsToPath(countryRings, this)
            }
            c.drawPath(countryPath, clearPaint)
            return bmp
        }

        private fun buildOverviewBordersBitmap(
            accumulators: Array<CellAccumulator>,
            isDark: Boolean
        ): Bitmap {
            val bmp = Bitmap.createBitmap(BORDER_BITMAP_W, BORDER_BITMAP_H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)

            val spanX = MercatorSpace.maxLocalX - MercatorSpace.minLocalX
            val spanY = MercatorSpace.maxLocalY - MercatorSpace.minLocalY
            val sx = BORDER_BITMAP_W.toFloat() / spanX
            val sy = BORDER_BITMAP_H.toFloat() / spanY

            val m = Matrix()
            m.setTranslate(-MercatorSpace.minLocalX, -MercatorSpace.minLocalY)
            m.postScale(sx, sy)
            c.setMatrix(m)

            val invScale = 1f / sx
            val oblastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.BUTT
                // 2560px bitmap maps to ~718 screen px at overview zoom -> 7.2px ≈ 2.0 screen px.
                strokeWidth = 4.2f * invScale
                color = if (isDark) Color.argb(235, 205, 222, 245) else Color.argb(215, 15, 23, 42)
            }

            // Overview zoom shows only oblast boundaries so the country map stays clean and distinguishable.
            for (acc in accumulators) {
                if (acc.oblastCount >= 4) {
                    c.drawLines(acc.oblastFloats, 0, acc.oblastCount, oblastPaint)
                }
            }
            return bmp
        }

        fun build(boundaries: UkraineMapBoundaries, isDark: Boolean): UkraineStaticMercatorAtlas {
            val spanX = (MercatorSpace.maxLocalX - MercatorSpace.minLocalX).coerceAtLeast(1f)
            val spanY = (MercatorSpace.maxLocalY - MercatorSpace.minLocalY).coerceAtLeast(1f)
            val accumulators = Array(GRID_COLS * GRID_ROWS) { CellAccumulator() }

            val countryOverviewRings = MercatorSpace.projectRings(
                boundaries.countryRings,
                MercatorSpace.COUNTRY_OVERVIEW_MIN_DIST
            )
            val overviewCountryLines = MercatorSpace.ringsToLineSegments(listOf(countryOverviewRings))

            val countryDetailRings = MercatorSpace.projectRings(
                boundaries.countryRings,
                MercatorSpace.DETAIL_MIN_DIST
            )
            val maskColor = if (isDark) Color.rgb(11, 16, 23) else Color.rgb(234, 240, 246)
            val maskBitmap = buildMaskBitmap(countryDetailRings, maskColor)
            binRingsSegments(countryDetailRings, accumulators, spanX, spanY) { x0, y0, x1, y1 ->
                appendCountrySeg(x0, y0, x1, y1)
            }

            val oblastPaths = Array(boundaries.oblasts.size) { i ->
                val oblast = boundaries.oblasts[i]
                val rings = MercatorSpace.projectRings(oblast.rings, MercatorSpace.DETAIL_MIN_DIST)
                binRingsSegments(rings, accumulators, spanX, spanY) { x0, y0, x1, y1 ->
                    appendOblastSeg(x0, y0, x1, y1)
                }
                Path().apply {
                    fillType = if (rings.size > 1) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                    MercatorSpace.appendLocalRingsToPath(rings, this)
                }
            }

            val raionPaths = Array(boundaries.raions.size) { i ->
                val raion = boundaries.raions[i]
                val rings = MercatorSpace.projectRings(raion.rings, MercatorSpace.DETAIL_MIN_DIST)
                binRingsSegments(rings, accumulators, spanX, spanY) { x0, y0, x1, y1 ->
                    appendRaionSeg(x0, y0, x1, y1)
                }
                Path().apply {
                    fillType = if (rings.size > 1) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                    MercatorSpace.appendLocalRingsToPath(rings, this)
                }
            }

            val detailHromadaPaths = Array(boundaries.hromadas.size) { i ->
                val h = boundaries.hromadas[i]
                val rings = MercatorSpace.projectRings(h.rings, MercatorSpace.DETAIL_MIN_DIST)
                binRingsSegments(rings, accumulators, spanX, spanY) { x0, y0, x1, y1 ->
                    appendHromadaSeg(x0, y0, x1, y1)
                }
                val cx = MercatorSpace.toLocalX(h.centerLon.toDouble())
                val cy = MercatorSpace.toLocalY(h.centerLat.toDouble())
                val cellIdx = cellIndexForLocalPoint(cx, cy, spanX, spanY)
                accumulators[cellIdx].hromadaIndices.add(i)

                Path().apply {
                    fillType = if (rings.size > 1) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                    MercatorSpace.appendLocalRingsToPath(rings, this)
                }
            }

            val overviewBordersBitmap = buildOverviewBordersBitmap(accumulators, isDark)

            val cells = ArrayList<SpatialVectorCell>(accumulators.size)
            for (acc in accumulators) {
                if (acc.hromadaIndices.isEmpty() &&
                    acc.hromadaCount == 0 &&
                    acc.raionCount == 0 &&
                    acc.oblastCount == 0 &&
                    acc.countryCount == 0
                ) {
                    continue
                }
                cells.add(
                    SpatialVectorCell(
                        minLocalX = acc.minX,
                        maxLocalX = acc.maxX,
                        minLocalY = acc.minY,
                        maxLocalY = acc.maxY,
                        hromadaIndices = acc.hromadaIndices.toIntArray(),
                        hromadaBorderLines = acc.hromadaFloats.copyOf(acc.hromadaCount),
                        raionBorderLines = acc.raionFloats.copyOf(acc.raionCount),
                        oblastBorderLines = acc.oblastFloats.copyOf(acc.oblastCount),
                        countryBorderLines = acc.countryFloats.copyOf(acc.countryCount)
                    )
                )
            }

            return UkraineStaticMercatorAtlas(
                sourceBoundaries = boundaries,
                isDark = isDark,
                maskBitmap = maskBitmap,
                overviewBordersBitmap = overviewBordersBitmap,
                overviewCountryLines = overviewCountryLines,
                cells = cells,
                oblastPaths = oblastPaths,
                raionPaths = raionPaths,
                detailHromadaPaths = detailHromadaPaths
            )
        }
    }
}

internal class OverviewAlertHighlight(
    val polygon: RegionPolygon,
    val threatInfo: RegionThreatInfo,
    val path: Path,
    val threatLevel: RegionThreatLevel,
    val isSubOblast: Boolean
)

internal class UkraineThreatAtlas(
    val sourceSnapshot: RegionThreatSnapshot,
    val isDark: Boolean,
    val overviewFillBitmap: Bitmap,
    val overviewAlertHighlights: List<OverviewAlertHighlight>
)

internal class OccupiedPolygonEntry(
    val minLocalX: Float,
    val maxLocalX: Float,
    val minLocalY: Float,
    val maxLocalY: Float,
    val borderLines: FloatArray
)

internal class UkraineOccupiedAtlas(
    val sourceSnapshot: OccupiedTerritoriesSnapshot,
    val entries: List<OccupiedPolygonEntry>,
    val combined2014Path: Path,
    val combined2022Path: Path,
    val allBorderLines: FloatArray
) {
    companion object {
        fun build(snapshot: OccupiedTerritoriesSnapshot): UkraineOccupiedAtlas {
            val entries = ArrayList<OccupiedPolygonEntry>(snapshot.polygons.size)
            val combined2014Path = Path()
            val combined2022Path = Path()
            val allRingGroups = ArrayList<List<FloatArray>>(snapshot.polygons.size)

            for (poly in snapshot.polygons) {
                val localRings = MercatorSpace.projectRings(poly.rings, MercatorSpace.DETAIL_MIN_DIST)
                if (localRings.isEmpty()) continue
                allRingGroups.add(localRings)

                val targetCombined = when (poly.category) {
                    OccupiedZoneCategory.OCCUPIED_2014 -> combined2014Path
                    OccupiedZoneCategory.OCCUPIED_2022 -> combined2022Path
                }
                MercatorSpace.appendLocalRingsToPath(localRings, targetCombined)

                val borderLines = MercatorSpace.ringsToLineSegments(listOf(localRings))
                entries.add(
                    OccupiedPolygonEntry(
                        minLocalX = MercatorSpace.toLocalX(poly.minLon.toDouble()),
                        maxLocalX = MercatorSpace.toLocalX(poly.maxLon.toDouble()),
                        minLocalY = MercatorSpace.toLocalY(poly.maxLat.toDouble()),
                        maxLocalY = MercatorSpace.toLocalY(poly.minLat.toDouble()),
                        borderLines = borderLines
                    )
                )
            }

            combined2014Path.fillType = Path.FillType.EVEN_ODD
            combined2022Path.fillType = Path.FillType.EVEN_ODD
            val allBorderLines = MercatorSpace.ringsToLineSegments(allRingGroups)

            return UkraineOccupiedAtlas(
                sourceSnapshot = snapshot,
                entries = entries,
                combined2014Path = combined2014Path,
                combined2022Path = combined2022Path,
                allBorderLines = allBorderLines
            )
        }
    }
}

/**
 * Single-entry process-wide cache for the static Mercator atlas, threat fill bitmap,
 * and occupied-territories vector atlas.
 * Prevents rebuilding 30-40 MB of bitmaps and re-projecting 150k vertices when switching tabs
 * or opening the crosshair picker.
 */
internal object UkraineAtlasCache {
    private val staticLock = Any()
    private val threatLock = Any()
    private val occupiedLock = Any()

    @Volatile
    private var cachedStatic: UkraineStaticMercatorAtlas? = null

    @Volatile
    private var cachedThreat: UkraineThreatAtlas? = null

    @Volatile
    private var cachedOccupied: UkraineOccupiedAtlas? = null

    fun peekStatic(
        boundaries: UkraineMapBoundaries?,
        isDark: Boolean
    ): UkraineStaticMercatorAtlas? {
        val existing = cachedStatic ?: return null
        return if (boundaries != null && existing.sourceBoundaries === boundaries && existing.isDark == isDark) {
            existing
        } else {
            null
        }
    }

    fun getOrBuildStatic(
        boundaries: UkraineMapBoundaries,
        isDark: Boolean
    ): UkraineStaticMercatorAtlas {
        peekStatic(boundaries, isDark)?.let { return it }
        synchronized(staticLock) {
            peekStatic(boundaries, isDark)?.let { return it }
            val built = UkraineStaticMercatorAtlas.build(boundaries, isDark)
            cachedStatic = built
            return built
        }
    }

    fun peekThreat(
        atlas: UkraineStaticMercatorAtlas?,
        snapshot: RegionThreatSnapshot?
    ): UkraineThreatAtlas? {
        val existing = cachedThreat ?: return null
        return if (atlas != null && snapshot != null &&
            existing.isDark == atlas.isDark &&
            existing.sourceSnapshot === snapshot
        ) {
            existing
        } else {
            null
        }
    }

    fun getOrBuildThreat(
        atlas: UkraineStaticMercatorAtlas,
        snapshot: RegionThreatSnapshot
    ): UkraineThreatAtlas {
        peekThreat(atlas, snapshot)?.let { return it }
        synchronized(threatLock) {
            peekThreat(atlas, snapshot)?.let { return it }
            val built = atlas.buildThreatAtlas(snapshot, cachedThreat)
            cachedThreat = built
            return built
        }
    }

    fun peekOccupied(snapshot: OccupiedTerritoriesSnapshot?): UkraineOccupiedAtlas? {
        val existing = cachedOccupied ?: return null
        return if (snapshot != null &&
            existing.sourceSnapshot.fetchedAtEpochMs == snapshot.fetchedAtEpochMs &&
            existing.sourceSnapshot.sourceHistoryId == snapshot.sourceHistoryId &&
            existing.sourceSnapshot.polygons.size == snapshot.polygons.size
        ) {
            existing
        } else {
            null
        }
    }

    fun getOrBuildOccupied(snapshot: OccupiedTerritoriesSnapshot): UkraineOccupiedAtlas {
        peekOccupied(snapshot)?.let { return it }
        synchronized(occupiedLock) {
            peekOccupied(snapshot)?.let { return it }
            val built = UkraineOccupiedAtlas.build(snapshot)
            cachedOccupied = built
            return built
        }
    }
}

/**
 * Application-startup preloader that warms osmdroid configuration, DNS resolution,
 * binary boundary parsing, and the static Mercator atlas on background threads before
 * the Map screen even enters composition.
 */
object UkraineMapPreloader {
    private val osmLock = Any()
    @Volatile
    private var osmConfigured = false
    private val preloadStarted = java.util.concurrent.atomic.AtomicBoolean(false)
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )

    fun ensureOsmDroidConfigured(context: android.content.Context) {
        if (osmConfigured) return
        synchronized(osmLock) {
            if (osmConfigured) return
            val appContext = context.applicationContext
            org.osmdroid.config.Configuration.getInstance().apply {
                load(appContext, appContext.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
                userAgentValue = "${appContext.packageName}/1.0 (Android; Radar)"
                osmdroidBasePath = java.io.File(appContext.cacheDir, "osmdroid")
                osmdroidTileCache = java.io.File(appContext.cacheDir, "osmdroid/tiles")
                cacheMapTileOvershoot = 48
                tileDownloadThreads = 4
                tileFileSystemThreads = 4
            }
            runCatching { java.io.File(appContext.cacheDir, "osmdroid/tiles/CartoDarkMatter").deleteRecursively() }
            runCatching { java.io.File(appContext.cacheDir, "osmdroid/tiles/EsriDarkGrayCanvas").deleteRecursively() }
            osmConfigured = true
        }
    }

    fun startPreload(
        context: android.content.Context,
        boundaryRepo: com.onemanarmy.airalerts.data.local.UkraineBoundaryRepository
    ) {
        if (!preloadStarted.compareAndSet(false, true)) return
        val appContext = context.applicationContext

        com.onemanarmy.airalerts.data.remote.ResilientOkHttpProvider.prewarmDnsAsync(
            "neptun.in.ua",
            "deepstatemap.live",
            "server.arcgisonline.com",
            "nominatim.openstreetmap.org"
        )

        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            ensureOsmDroidConfigured(appContext)
        }

        scope.launch {
            val boundaries = boundaryRepo.loadIfNeeded()
            if (boundaries != null) {
                UkraineAtlasCache.getOrBuildStatic(boundaries, isDark = true)
            }
        }
    }
}


