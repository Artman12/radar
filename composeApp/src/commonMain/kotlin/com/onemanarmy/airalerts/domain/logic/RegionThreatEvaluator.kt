package com.onemanarmy.airalerts.domain.logic

import com.onemanarmy.airalerts.domain.model.ActiveAlertInfo
import com.onemanarmy.airalerts.domain.model.AlertLevel
import com.onemanarmy.airalerts.domain.model.RegionPolygon
import com.onemanarmy.airalerts.domain.model.RegionThreatInfo
import com.onemanarmy.airalerts.domain.model.RegionThreatLevel
import com.onemanarmy.airalerts.domain.model.RegionThreatSnapshot
import com.onemanarmy.airalerts.domain.model.ThreatType
import com.onemanarmy.airalerts.domain.model.ThreatWithProximity
import com.onemanarmy.airalerts.domain.model.UkraineMapBoundaries
import kotlin.concurrent.Volatile

/**
 * Evaluates real-time threat levels for every oblast, raion, and hromada in Ukraine
 * by combining official/NEPTUN alerts with live radar aerial target positions.
 */
class RegionThreatEvaluator {

    companion object {
        private const val BBOX_PAD_LAT = 0.28f
        private const val BBOX_PAD_LON = 0.42f
        private const val DEFAULT_THREAT_RADIUS_KM = 18.0
        private const val MISSILE_THREAT_RADIUS_KM = 26.0
        private val MULTI_SPACE_REGEX = Regex("\\s+")
        private val ADJ_SUFFIXES = listOf("ський", "ська", "ське", "цький", "цька", "цьке", "зький", "зька", "зьке")
        private val SHORT_SUFFIXES = listOf("ий", "ій", "а", "я", "е", "о", "и", "і")
    }

    private class BoundaryThreatIndex(
        val source: UkraineMapBoundaries,
        val oblastKeys: Array<String>,
        val raionOblastKeys: Array<String>,
        val raionKeys: Array<String>,
        val hromadaOblastKeys: Array<String>,
        val hromadaRaionKeys: Array<String>,
        val hromadaKeys: Array<String>
    )

    private class IndexedLocalAlert(
        val raionKey: String,
        val hromadaKey: String,
        val level: AlertLevel
    )

    @Volatile
    private var cachedIndex: BoundaryThreatIndex? = null

    private fun getOrBuildIndex(boundaries: UkraineMapBoundaries): BoundaryThreatIndex {
        val existing = cachedIndex
        if (existing != null && existing.source === boundaries) {
            return existing
        }
        val oblastKeyCache = HashMap<String, String>(48)
        fun cachedOblast(raw: String): String =
            oblastKeyCache.getOrPut(raw) { canonicalOblastKey(raw) }

        val raionKeyCache = HashMap<String, String>(192)
        fun cachedRaion(raw: String): String =
            raionKeyCache.getOrPut(raw) { canonicalRaionKey(raw) }

        val oblastKeys = Array(boundaries.oblasts.size) { i ->
            cachedOblast(boundaries.oblasts[i].name)
        }
        val raionOblastKeys = Array(boundaries.raions.size) { i ->
            cachedOblast(boundaries.raions[i].oblastName)
        }
        val raionKeys = Array(boundaries.raions.size) { i ->
            cachedRaion(boundaries.raions[i].name)
        }
        val hromadaOblastKeys = Array(boundaries.hromadas.size) { i ->
            cachedOblast(boundaries.hromadas[i].oblastName)
        }
        val hromadaRaionKeys = Array(boundaries.hromadas.size) { i ->
            cachedRaion(boundaries.hromadas[i].raionName)
        }
        val hromadaKeys = Array(boundaries.hromadas.size) { i ->
            canonicalHromadaKey(boundaries.hromadas[i].name)
        }

        val built = BoundaryThreatIndex(
            source = boundaries,
            oblastKeys = oblastKeys,
            raionOblastKeys = raionOblastKeys,
            raionKeys = raionKeys,
            hromadaOblastKeys = hromadaOblastKeys,
            hromadaRaionKeys = hromadaRaionKeys,
            hromadaKeys = hromadaKeys
        )
        cachedIndex = built
        return built
    }

    fun evaluate(
        boundaries: UkraineMapBoundaries,
        activeAlerts: List<ActiveAlertInfo>,
        threats: List<ThreatWithProximity>
    ): RegionThreatSnapshot {
        val index = getOrBuildIndex(boundaries)

        val oblastWideMap = HashMap<String, AlertLevel>(32)
        val localByOblast = HashMap<String, ArrayList<IndexedLocalAlert>>(32)
        val unscopedLocalAlerts = ArrayList<IndexedLocalAlert>(8)

        for (alert in activeAlerts) {
            if (alert.isOblastWide) {
                val key = canonicalOblastKey(alert.oblastName.ifBlank { alert.regionName })
                if (key.isNotBlank()) {
                    val prev = oblastWideMap[key] ?: AlertLevel.NONE
                    val next = maxAlertLevel(prev, alert.level)
                    oblastWideMap[key] = next
                    // Crimea-wide alert ("9999") also applies to Sevastopol.
                    if (key == "9999") {
                        val prevSev = oblastWideMap["sevastopol"] ?: AlertLevel.NONE
                        oblastWideMap["sevastopol"] = maxAlertLevel(prevSev, alert.level)
                    } else if (key == "sevastopol") {
                        val prevCrimea = oblastWideMap["9999"] ?: AlertLevel.NONE
                        oblastWideMap["9999"] = maxAlertLevel(prevCrimea, alert.level)
                    }
                }
            } else {
                val oblastKey = canonicalOblastKey(alert.oblastName)
                val fallbackOblastKey = if (oblastKey.isBlank()) {
                    canonicalOblastKey(alert.regionName)
                } else {
                    oblastKey
                }
                val entry = IndexedLocalAlert(
                    raionKey = canonicalRaionKey(alert.regionName),
                    hromadaKey = canonicalHromadaKey(alert.regionName),
                    level = alert.level
                )
                if (oblastKey.isBlank()) {
                    unscopedLocalAlerts.add(entry)
                } else {
                    localByOblast.getOrPut(oblastKey) { ArrayList(4) }.add(entry)
                    if (oblastKey == "9999") {
                        localByOblast.getOrPut("sevastopol") { ArrayList(4) }.add(entry)
                    } else if (oblastKey == "sevastopol") {
                        localByOblast.getOrPut("9999") { ArrayList(4) }.add(entry)
                    }
                }
                // Handle special-status cities (Kyiv, Sevastopol) if reported as local alerts without oblastName.
                if (oblastKey.isBlank() && (fallbackOblastKey == "31" || fallbackOblastKey == "sevastopol")) {
                    localByOblast.getOrPut(fallbackOblastKey) { ArrayList(2) }.add(entry)
                }
            }
        }

        val hromadas = boundaries.hromadas
        val hromadaThreats = ArrayList<RegionThreatLevel>(hromadas.size)
        val hromadaInfos = ArrayList<RegionThreatInfo>(hromadas.size)

        var criticalCount = 0
        var highCount = 0
        var elevatedCount = 0

        for (i in hromadas.indices) {
            val hromada = hromadas[i]
            val oblastKey = index.hromadaOblastKeys[i]
            val raionKey = index.hromadaRaionKeys[i]
            val hromadaKey = index.hromadaKeys[i]

            val oblastAlert = oblastWideMap[oblastKey] ?: AlertLevel.NONE
            val scopedLocals = localByOblast[oblastKey]
            val raionAlert = highestLocalAlertForRaionIndexed(raionKey, scopedLocals, unscopedLocalAlerts)
            val hromadaOnlyAlert = highestLocalAlertForHromadaIndexed(hromadaKey, scopedLocals, unscopedLocalAlerts)
            val alertLevel = maxAlertLevel(oblastAlert, maxAlertLevel(raionAlert, hromadaOnlyAlert))
            val nearbyCount = countNearbyRadarThreats(hromada, threats)

            val threatLevel = when {
                alertLevel == AlertLevel.RED && nearbyCount > 0 -> {
                    criticalCount++
                    RegionThreatLevel.CRITICAL
                }
                alertLevel == AlertLevel.RED -> {
                    highCount++
                    RegionThreatLevel.HIGH
                }
                alertLevel == AlertLevel.YELLOW -> {
                    elevatedCount++
                    RegionThreatLevel.ELEVATED
                }
                else -> RegionThreatLevel.CLEAR
            }

            hromadaThreats.add(threatLevel)
            hromadaInfos.add(
                RegionThreatInfo(
                    threatLevel = threatLevel,
                    alertLevel = alertLevel,
                    hasActiveAlert = alertLevel != AlertLevel.NONE,
                    nearbyThreatCount = nearbyCount,
                    isOblastWideAlert = oblastAlert != AlertLevel.NONE,
                    isStandaloneAlert = oblastAlert == AlertLevel.NONE &&
                        raionAlert == AlertLevel.NONE &&
                        hromadaOnlyAlert != AlertLevel.NONE
                )
            )
        }

        val raions = boundaries.raions
        val raionThreats = ArrayList<RegionThreatLevel>(raions.size)
        val raionInfos = ArrayList<RegionThreatInfo>(raions.size)
        for (i in raions.indices) {
            val raion = raions[i]
            val oblastKey = index.raionOblastKeys[i]
            val raionKey = index.raionKeys[i]
            val oblastAlert = oblastWideMap[oblastKey] ?: AlertLevel.NONE
            val localAlert = highestLocalAlertForRaionIndexed(raionKey, localByOblast[oblastKey], unscopedLocalAlerts)
            val alertLevel = maxAlertLevel(oblastAlert, localAlert)
            val nearbyCount = countNearbyRadarThreats(raion, threats)
            val threatLevel = toRegionThreatLevel(alertLevel, nearbyCount > 0)
            raionThreats.add(threatLevel)
            raionInfos.add(
                RegionThreatInfo(
                    threatLevel = threatLevel,
                    alertLevel = alertLevel,
                    hasActiveAlert = alertLevel != AlertLevel.NONE,
                    nearbyThreatCount = nearbyCount,
                    isOblastWideAlert = oblastAlert != AlertLevel.NONE,
                    isStandaloneAlert = oblastAlert == AlertLevel.NONE && localAlert != AlertLevel.NONE
                )
            )
        }

        val oblasts = boundaries.oblasts
        val oblastThreats = ArrayList<RegionThreatLevel>(oblasts.size)
        val oblastInfos = ArrayList<RegionThreatInfo>(oblasts.size)
        for (idx in oblasts.indices) {
            val oblast = oblasts[idx]
            val key = index.oblastKeys[idx]
            var oblastWideAlert = oblastWideMap[key] ?: AlertLevel.NONE
            if (key == "31" || key == "sevastopol") {
                localByOblast[key]?.let { specials ->
                    for (entry in specials) {
                        oblastWideAlert = maxAlertLevel(oblastWideAlert, entry.level)
                    }
                }
            }
            val nearbyCount = countNearbyRadarThreats(oblast, threats)
            val threatLevel = toRegionThreatLevel(oblastWideAlert, nearbyCount > 0)
            oblastThreats.add(threatLevel)
            oblastInfos.add(
                RegionThreatInfo(
                    threatLevel = threatLevel,
                    alertLevel = oblastWideAlert,
                    hasActiveAlert = oblastWideAlert != AlertLevel.NONE,
                    nearbyThreatCount = nearbyCount,
                    isOblastWideAlert = oblastWideAlert != AlertLevel.NONE
                )
            )
        }

        return RegionThreatSnapshot(
            hromadaThreats = hromadaThreats,
            raionThreats = raionThreats,
            oblastThreats = oblastThreats,
            hromadaInfos = hromadaInfos,
            raionInfos = raionInfos,
            oblastInfos = oblastInfos,
            criticalHromadaCount = criticalCount,
            highHromadaCount = highCount,
            elevatedHromadaCount = elevatedCount
        )
    }

    private fun highestLocalAlertForRaionIndexed(
        raionKey: String,
        scopedLocals: List<IndexedLocalAlert>?,
        unscopedLocals: List<IndexedLocalAlert>
    ): AlertLevel {
        if (raionKey.isBlank()) return AlertLevel.NONE
        var best = AlertLevel.NONE
        if (scopedLocals != null) {
            for (i in scopedLocals.indices) {
                val entry = scopedLocals[i]
                if (raionKey == entry.raionKey) {
                    best = maxAlertLevel(best, entry.level)
                    if (best == AlertLevel.RED) return best
                }
            }
        }
        for (i in unscopedLocals.indices) {
            val entry = unscopedLocals[i]
            if (raionKey == entry.raionKey) {
                best = maxAlertLevel(best, entry.level)
                if (best == AlertLevel.RED) return best
            }
        }
        return best
    }

    private fun highestLocalAlertForHromadaIndexed(
        hromadaKey: String,
        scopedLocals: List<IndexedLocalAlert>?,
        unscopedLocals: List<IndexedLocalAlert>
    ): AlertLevel {
        if (hromadaKey.isBlank()) return AlertLevel.NONE
        var best = AlertLevel.NONE
        if (scopedLocals != null) {
            for (i in scopedLocals.indices) {
                val entry = scopedLocals[i]
                if (hromadaKeysMatch(hromadaKey, entry.hromadaKey)) {
                    best = maxAlertLevel(best, entry.level)
                    if (best == AlertLevel.RED) return best
                }
            }
        }
        for (i in unscopedLocals.indices) {
            val entry = unscopedLocals[i]
            if (hromadaKeysMatch(hromadaKey, entry.hromadaKey)) {
                best = maxAlertLevel(best, entry.level)
                if (best == AlertLevel.RED) return best
            }
        }
        return best
    }

    private fun toRegionThreatLevel(alertLevel: AlertLevel, hasNearbyThreat: Boolean): RegionThreatLevel =
        when {
            alertLevel == AlertLevel.RED && hasNearbyThreat -> RegionThreatLevel.CRITICAL
            alertLevel == AlertLevel.RED -> RegionThreatLevel.HIGH
            alertLevel == AlertLevel.YELLOW -> RegionThreatLevel.ELEVATED
            else -> RegionThreatLevel.CLEAR
        }

    private fun countNearbyRadarThreats(
        region: RegionPolygon,
        threats: List<ThreatWithProximity>
    ): Int {
        if (threats.isEmpty()) return 0
        var count = 0
        for (item in threats) {
            val threat = item.threat
            val tLat = threat.latitude.toFloat()
            val tLon = threat.longitude.toFloat()

            // Fast bounding box rejection before polygon / Haversine math.
            if (tLat < region.minLat - BBOX_PAD_LAT ||
                tLat > region.maxLat + BBOX_PAD_LAT ||
                tLon < region.minLon - BBOX_PAD_LON ||
                tLon > region.maxLon + BBOX_PAD_LON
            ) {
                continue
            }

            val insideBbox = tLat in region.minLat..region.maxLat && tLon in region.minLon..region.maxLon
            if (insideBbox && GeoUtils.isPointInRings(threat.latitude, threat.longitude, region.rings)) {
                count++
                continue
            }

            val radiusKm = if (
                threat.type == ThreatType.BALLISTIC ||
                threat.type == ThreatType.CRUISE_MISSILE ||
                threat.type == ThreatType.JET_SHAHED
            ) {
                MISSILE_THREAT_RADIUS_KM
            } else {
                DEFAULT_THREAT_RADIUS_KM
            }

            val distKm = GeoUtils.haversineDistanceKm(
                threat.latitude,
                threat.longitude,
                region.centerLat.toDouble(),
                region.centerLon.toDouble()
            )
            if (distKm <= radiusKm) {
                count++
            }
        }
        return count
    }

    private fun maxAlertLevel(a: AlertLevel, b: AlertLevel): AlertLevel = when {
        a == AlertLevel.RED || b == AlertLevel.RED -> AlertLevel.RED
        a == AlertLevel.YELLOW || b == AlertLevel.YELLOW -> AlertLevel.YELLOW
        else -> AlertLevel.NONE
    }

    internal fun canonicalOblastKey(raw: String): String {
        val norm = normalizeCommon(raw)
        if (norm.isBlank()) return ""
        if (norm.contains("севастопол") || norm.contains("sevastopol")) return "sevastopol"
        if (norm == "київ" || norm == "kyiv" || norm == "kiev") return "31"
        if (norm.startsWith("київськ") || norm.startsWith("kyivsk")) return "14"
        return RegionMatcher.findOblastByName(raw)?.regionId ?: norm
    }

    internal fun canonicalRaionKey(raw: String): String {
        var norm = normalizeCommon(raw)
            .replace("району", "")
            .replace("район", "")
            .replace("raion", "")
            .replace("district", "")
            .trim()
        if (norm.isBlank()) return ""

        // Handle post-2020/2024 Ukrainian raion renames and common typos.
        norm = when {
            norm.contains("новоград") || norm.contains("звягель") -> "звягельськ"
            norm.contains("новомосков") || norm.contains("самарів") -> "самарівськ"
            norm.contains("красноград") || norm.contains("берестин") -> "берестинськ"
            norm.contains("червоноград") || norm.contains("шептицьк") -> "шептицьк"
            norm.contains("володимир") -> "володимирськ"
            norm.contains("маріупол") -> "маріупольськ"
            norm.contains("дніпропетров") -> "дніпровськ"
            norm.contains("кіровоград") -> "кропивницьк"
            norm.contains("артемів") -> "бахмутськ"
            else -> norm
        }
        return stripAdjectivalSuffix(norm)
    }

    internal fun canonicalHromadaKey(raw: String): String {
        val norm = normalizeCommon(raw)
            .replace("територіальна громада", "")
            .replace("громада", "")
            .replace("міська", "")
            .replace("селищна", "")
            .replace("сільська", "")
            .replace("міськрада", "")
            .replace("community", "")
            .replace("смт", "")
            .trim()
        return stripAdjectivalSuffix(norm)
    }

    private fun hromadaKeysMatch(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        if (a.length >= 5 && b.length >= 5 && (a.startsWith(b) || b.startsWith(a))) return true
        return false
    }

    private fun stripAdjectivalSuffix(input: String): String {
        val s = input.trim()
        for (suffix in ADJ_SUFFIXES) {
            if (s.length > suffix.length + 3 && s.endsWith(suffix)) {
                return s.dropLast(suffix.length)
            }
        }
        for (suffix in SHORT_SUFFIXES) {
            if (s.length > 4 && s.endsWith(suffix)) {
                return s.dropLast(suffix.length)
            }
        }
        return s
    }

    private fun normalizeCommon(raw: String): String {
        return raw.lowercase()
            .replace("м.", "")
            .replace("місто", "")
            .replace("city", "")
            .replace("області", "")
            .replace("область", "")
            .replace("oblast", "")
            .replace("'", "")
            .replace("’", "")
            .replace("`", "")
            .replace("-", " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }
}
