package com.onemanarmy.airalerts.data.repository

import com.onemanarmy.airalerts.data.remote.NextAlertDistrictTimelineResponseDto
import com.onemanarmy.airalerts.data.remote.NextAlertExplosionsApi
import com.onemanarmy.airalerts.domain.logic.ExplosionLocationResolver
import com.onemanarmy.airalerts.domain.logic.GeoUtils
import com.onemanarmy.airalerts.domain.model.ActiveAlertInfo
import com.onemanarmy.airalerts.domain.model.ExplosionEvent
import com.onemanarmy.airalerts.domain.model.ExplosionWithProximity
import com.onemanarmy.airalerts.domain.model.SavedPoint
import com.onemanarmy.airalerts.domain.model.UkraineMapBoundaries
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Aggregates live blast reports from NextAlert district timelines, resolving district/text
 * reports to map coordinates.
 */
class NextAlertExplosionsRepository(
    private val api: NextAlertExplosionsApi
) {
    companion object {
        const val MAX_BLAST_AGE_MS = 2 * 60 * 60 * 1000L // 2 hours
        const val RECENT_BLAST_AGE_MS = 30 * 60 * 1000L // 30 minutes
        private const val MAX_DISTRICTS_PER_CYCLE = 12
        private const val MAX_CONCURRENT_REQUESTS = 4
    }

    data class DistrictQueryTarget(
        val raionName: String,
        val oblastName: String
    )

    suspend fun fetchLiveExplosions(
        activeAlerts: List<ActiveAlertInfo>,
        trackedPoints: List<SavedPoint>,
        boundaries: UkraineMapBoundaries?,
        nowEpochMs: Long = Clock.System.now().toEpochMilliseconds()
    ): Result<List<ExplosionEvent>> = runCatching {
        val targets = buildDistrictQueryTargets(activeAlerts, trackedPoints)
        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

        val timelines: List<NextAlertDistrictTimelineResponseDto> = coroutineScope {
            targets.map { target ->
                async {
                    semaphore.withPermit {
                        api.fetchDistrictTimeline(target.raionName, target.oblastName).getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val events = mapTimelinesToExplosions(
            timelines = timelines,
            boundaries = boundaries,
            nowEpochMs = nowEpochMs
        )
        ExplosionLocationResolver.spreadOverlappingExplosions(events)
    }

    internal suspend fun buildDistrictQueryTargets(
        activeAlerts: List<ActiveAlertInfo>,
        trackedPoints: List<SavedPoint>
    ): List<DistrictQueryTarget> {
        val ordered = LinkedHashMap<String, DistrictQueryTarget>()

        fun addTarget(raion: String, oblast: String) {
            val cleanRaion = raion.trim()
            val cleanOblast = oblast.trim()
            if (cleanRaion.isBlank()) return
            val key = "${cleanRaion.lowercase()}|${cleanOblast.lowercase()}"
            if (!ordered.containsKey(key)) {
                ordered[key] = DistrictQueryTarget(cleanRaion, cleanOblast)
            }
        }

        // 1. Prioritize user's tracked districts first
        trackedPoints.forEach { point ->
            val raion = point.districtName.ifBlank { point.regionName }
            addTarget(raion, point.oblastName)
        }

        // 2. Currently alerted sub-oblast regions from our main alert feed
        activeAlerts
            .filter { !it.isOblastWide }
            .forEach { alert ->
                addTarget(alert.regionName, alert.oblastName)
            }

        // 3. Supplement with NextAlert's active raions endpoint when slots remain
        if (ordered.size < MAX_DISTRICTS_PER_CYCLE) {
            api.fetchActiveRaions().getOrNull().orEmpty()
                .sortedByDescending { it.status.equals("red", ignoreCase = true) }
                .forEach { raion ->
                    if (ordered.size >= MAX_DISTRICTS_PER_CYCLE) return@forEach
                    addTarget(raion.name, raion.oblast)
                }
        }

        return ordered.values.take(MAX_DISTRICTS_PER_CYCLE)
    }

    internal fun mapTimelinesToExplosions(
        timelines: List<NextAlertDistrictTimelineResponseDto>,
        boundaries: UkraineMapBoundaries?,
        nowEpochMs: Long
    ): List<ExplosionEvent> {
        val cutoffMs = nowEpochMs - MAX_BLAST_AGE_MS
        val deduplicated = LinkedHashMap<String, ExplosionEvent>()

        for (timeline in timelines) {
            val raionName = timeline.name
            val oblastName = timeline.oblast
            for (interval in timeline.intervals) {
                for ((idx, blast) in interval.blasts.withIndex()) {
                    val blastEpochMs = runCatching { Instant.parse(blast.date).toEpochMilliseconds() }
                        .getOrNull() ?: continue
                    if (blastEpochMs < cutoffMs || blastEpochMs > nowEpochMs + 60_000L) continue

                    val coords = ExplosionLocationResolver.resolveCoordinates(
                        raionName = raionName,
                        oblastName = oblastName,
                        blastText = blast.text,
                        boundaries = boundaries
                    ) ?: continue

                    val id = "na_${raionName}_${blast.date}_$idx"
                    val cleanTitle = blast.text.trim().ifBlank { raionName }
                    val isRecent = (nowEpochMs - blastEpochMs) <= RECENT_BLAST_AGE_MS

                    deduplicated[id] = ExplosionEvent(
                        id = id,
                        title = cleanTitle,
                        rawText = blast.text.trim(),
                        raionName = raionName,
                        oblastName = oblastName,
                        localityName = coords.localityName,
                        latitude = coords.latitude,
                        longitude = coords.longitude,
                        timestampIso = blast.date,
                        timestampEpochMs = blastEpochMs,
                        isRecent = isRecent,
                        isApproximate = coords.isApproximate
                    )
                }
            }
        }

        return deduplicated.values.sortedByDescending { it.timestampEpochMs }
    }

    fun enrichProximity(
        explosions: List<ExplosionEvent>,
        points: List<SavedPoint>
    ): List<ExplosionWithProximity> {
        return explosions.map { explosion ->
            var minDist = Double.MAX_VALUE
            var nearestPoint: SavedPoint? = null
            for (point in points) {
                val d = GeoUtils.haversineDistanceKm(
                    explosion.latitude,
                    explosion.longitude,
                    point.latitude,
                    point.longitude
                )
                if (d < minDist) {
                    minDist = d
                    nearestPoint = point
                }
            }
            ExplosionWithProximity(
                explosion = explosion,
                nearestPointId = nearestPoint?.id,
                nearestPointName = nearestPoint?.name,
                distanceKm = if (nearestPoint != null) minDist else null
            )
        }
    }
}
