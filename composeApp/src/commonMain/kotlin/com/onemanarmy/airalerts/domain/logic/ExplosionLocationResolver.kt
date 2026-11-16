package com.onemanarmy.airalerts.domain.logic

import com.onemanarmy.airalerts.domain.model.ExplosionEvent
import com.onemanarmy.airalerts.domain.model.RegionPolygon
import com.onemanarmy.airalerts.domain.model.UkraineMapBoundaries
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Resolves NextAlert district-level blast notifications to approximate WGS84 coordinates
 * using Ukraine's 3-tier administrative boundaries (hromada -> raion -> oblast centroid).
 */
object ExplosionLocationResolver {

    data class ResolvedExplosionCoordinates(
        val latitude: Double,
        val longitude: Double,
        val localityName: String,
        val isApproximate: Boolean
    )

    private val ignoredBlastWords = setOf(
        "вибух", "вибухи", "вибухів", "вибуху", "звук", "звуки",
        "чути", "чутно", "приліт", "прильот", "ппо", "робота",
        "район", "районі", "району", "область", "області", "місто",
        "громада", "громаді", "напрямку", "бік", "поблизу", "поруч"
    )

    fun resolveCoordinates(
        raionName: String,
        oblastName: String,
        blastText: String,
        boundaries: UkraineMapBoundaries?
    ): ResolvedExplosionCoordinates? {
        if (boundaries != null) {
            val matchedRaion = findMatchingRaion(raionName, oblastName, boundaries.raions)
            if (matchedRaion != null) {
                val matchedHromada = findMatchingHromadaInRaion(
                    blastText = blastText,
                    raion = matchedRaion,
                    hromadas = boundaries.hromadas
                )
                if (matchedHromada != null) {
                    return ResolvedExplosionCoordinates(
                        latitude = matchedHromada.centerLat.toDouble(),
                        longitude = matchedHromada.centerLon.toDouble(),
                        localityName = matchedHromada.name,
                        isApproximate = true
                    )
                }
                return ResolvedExplosionCoordinates(
                    latitude = matchedRaion.centerLat.toDouble(),
                    longitude = matchedRaion.centerLon.toDouble(),
                    localityName = matchedRaion.name,
                    isApproximate = true
                )
            }

            val matchedOblast = findMatchingOblast(raionName, oblastName, boundaries.oblasts)
            if (matchedOblast != null) {
                return ResolvedExplosionCoordinates(
                    latitude = matchedOblast.centerLat.toDouble(),
                    longitude = matchedOblast.centerLon.toDouble(),
                    localityName = matchedOblast.name,
                    isApproximate = true
                )
            }
        }

        val centroid = RegionMatcher.findOblastByName(raionName)
            ?: RegionMatcher.findOblastByName(oblastName)
            ?: return null

        return ResolvedExplosionCoordinates(
            latitude = centroid.lat,
            longitude = centroid.lon,
            localityName = raionName.ifBlank { centroid.ukName },
            isApproximate = true
        )
    }

    /**
     * Spreads overlapping explosion markers slightly (~1.5 km) in a deterministic spiral
     * when multiple blasts resolve to the same district centroid.
     */
    fun spreadOverlappingExplosions(events: List<ExplosionEvent>): List<ExplosionEvent> {
        if (events.size <= 1) return events
        val result = ArrayList<ExplosionEvent>(events.size)
        val occupiedCounts = mutableListOf<Pair<Double, Double>>()

        for (event in events) {
            val dupIndex = occupiedCounts.count { (lat, lon) ->
                abs(lat - event.latitude) < 0.004 && abs(lon - event.longitude) < 0.004
            }
            occupiedCounts.add(event.latitude to event.longitude)

            if (dupIndex == 0) {
                result.add(event)
            } else {
                val angleRad = (dupIndex * 137.5) * PI / 180.0
                val radiusDeg = 0.015 * (1 + (dupIndex - 1) / 4)
                val latOffset = radiusDeg * sin(angleRad)
                val lonOffset = (radiusDeg * cos(angleRad)) /
                    cos(event.latitude * PI / 180.0).coerceAtLeast(0.4)
                result.add(
                    event.copy(
                        latitude = event.latitude + latOffset,
                        longitude = event.longitude + lonOffset
                    )
                )
            }
        }
        return result
    }

    private fun findMatchingRaion(
        raionName: String,
        oblastName: String,
        raions: List<RegionPolygon>
    ): RegionPolygon? {
        val normTarget = normalizeAdminName(raionName)
        if (normTarget.isBlank()) return null
        val normOblast = normalizeAdminName(oblastName)

        val oblastFiltered = if (normOblast.isNotBlank()) {
            raions.filter { sameStem(normalizeAdminName(it.oblastName), normOblast) }
        } else {
            emptyList()
        }
        val pool = oblastFiltered.ifEmpty { raions }

        pool.firstOrNull { normalizeAdminName(it.name) == normTarget }?.let { return it }
        return pool.firstOrNull { sameStem(normalizeAdminName(it.name), normTarget) }
    }

    private fun findMatchingOblast(
        raionName: String,
        oblastName: String,
        oblasts: List<RegionPolygon>
    ): RegionPolygon? {
        val candidates = listOf(raionName, oblastName)
            .map { normalizeAdminName(it) }
            .filter { it.isNotBlank() }

        for (candidate in candidates) {
            oblasts.firstOrNull { normalizeAdminName(it.name) == candidate }?.let { return it }
            oblasts.firstOrNull { sameStem(normalizeAdminName(it.name), candidate) }?.let { return it }
        }
        return null
    }

    private fun findMatchingHromadaInRaion(
        blastText: String,
        raion: RegionPolygon,
        hromadas: List<RegionPolygon>
    ): RegionPolygon? {
        val tokens = tokenizeBlastText(blastText)
        if (tokens.isEmpty()) return null

        val normRaion = normalizeAdminName(raion.name)
        val normOblast = normalizeAdminName(raion.oblastName)

        val candidateHromadas = hromadas.filter { h ->
            sameStem(normalizeAdminName(h.raionName), normRaion) &&
                (normOblast.isBlank() || sameStem(normalizeAdminName(h.oblastName), normOblast))
        }
        if (candidateHromadas.isEmpty()) return null

        var bestMatch: RegionPolygon? = null
        var bestScore = 0

        for (hromada in candidateHromadas) {
            val normHromada = normalizeAdminName(hromada.name)
            if (normHromada.length < 3) continue
            for (token in tokens) {
                val score = matchScore(token, normHromada)
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = hromada
                }
            }
        }
        return bestMatch
    }

    private fun tokenizeBlastText(raw: String): List<String> {
        return raw.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s'-]"""), " ")
            .split(Regex("""\s+"""))
            .map { it.replace("'", "").replace("’", "").trim() }
            .filter { it.length >= 3 && it !in ignoredBlastWords }
    }

    private fun matchScore(token: String, normHromada: String): Int {
        if (token == normHromada) return 100 + token.length
        if (normHromada.startsWith(token) && token.length >= 4) return 70 + token.length
        if (token.startsWith(normHromada) && normHromada.length >= 4) return 60 + normHromada.length
        val prefixLen = commonPrefixLength(token, normHromada)
        return if (prefixLen >= 5) 40 + prefixLen else 0
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val max = minOf(a.length, b.length)
        var i = 0
        while (i < max && a[i] == b[i]) i++
        return i
    }

    private fun sameStem(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        if (a == b || a.contains(b) || b.contains(a)) return true
        return a.length >= 5 && b.length >= 5 && a.take(5) == b.take(5)
    }

    private fun normalizeAdminName(raw: String): String {
        return RegionMatcher.normalizeName(raw)
            .replace("м.", "")
            .trim()
    }
}
