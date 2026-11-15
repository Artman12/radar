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
    }

    fun evaluate(
        boundaries: UkraineMapBoundaries,
        activeAlerts: List<ActiveAlertInfo>,
        threats: List<ThreatWithProximity>
    ): RegionThreatSnapshot {
        val oblastAlerts = activeAlerts.filter { it.isOblastWide }
        val localAlerts = activeAlerts.filter { !it.isOblastWide }

        val oblastAlertLevels = boundaries.oblasts.map { oblast ->
            highestAlertForOblast(oblast, oblastAlerts, localAlerts)
        }

        val hromadaThreats = ArrayList<RegionThreatLevel>(boundaries.hromadas.size)
        val hromadaInfos = ArrayList<RegionThreatInfo>(boundaries.hromadas.size)

        var criticalCount = 0
        var highCount = 0
        var elevatedCount = 0

        for (hromada in boundaries.hromadas) {
            val oblastAlert = highestOblastWideAlert(hromada.oblastName, oblastAlerts)
            val raionAlert = highestLocalAlertForRaion(hromada.raionName, hromada.oblastName, localAlerts)
            val hromadaOnlyAlert = highestLocalAlertForHromada(hromada, localAlerts)
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
                alertLevel == AlertLevel.YELLOW || nearbyCount > 0 -> {
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

        val raionThreats = ArrayList<RegionThreatLevel>(boundaries.raions.size)
        val raionInfos = ArrayList<RegionThreatInfo>(boundaries.raions.size)
        for (raion in boundaries.raions) {
            val oblastAlert = highestOblastWideAlert(raion.oblastName, oblastAlerts)
            val localAlert = highestLocalAlertForRaion(raion.name, raion.oblastName, localAlerts)
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

        val oblastThreats = ArrayList<RegionThreatLevel>(boundaries.oblasts.size)
        val oblastInfos = ArrayList<RegionThreatInfo>(boundaries.oblasts.size)
        for (idx in boundaries.oblasts.indices) {
            val oblast = boundaries.oblasts[idx]
            val oblastWideAlert = oblastAlertLevels[idx]
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

    private fun toRegionThreatLevel(alertLevel: AlertLevel, hasNearbyThreat: Boolean): RegionThreatLevel =
        when {
            alertLevel == AlertLevel.RED && hasNearbyThreat -> RegionThreatLevel.CRITICAL
            alertLevel == AlertLevel.RED -> RegionThreatLevel.HIGH
            alertLevel == AlertLevel.YELLOW || hasNearbyThreat -> RegionThreatLevel.ELEVATED
            else -> RegionThreatLevel.CLEAR
        }

    private fun highestAlertForOblast(
        oblast: RegionPolygon,
        oblastAlerts: List<ActiveAlertInfo>,
        localAlerts: List<ActiveAlertInfo>
    ): AlertLevel {
        val key = canonicalOblastKey(oblast.name)
        var best = AlertLevel.NONE

        for (alert in oblastAlerts) {
            if (oblastKeysMatch(key, canonicalOblastKey(alert.oblastName.ifBlank { alert.regionName }))) {
                best = maxAlertLevel(best, alert.level)
            }
        }

        // Handle special-status cities (Kyiv, Sevastopol) if reported as local alerts.
        if (key == "31" || key == "sevastopol") {
            for (alert in localAlerts) {
                val alertOblast = canonicalOblastKey(alert.oblastName.ifBlank { alert.regionName })
                if (oblastKeysMatch(key, alertOblast)) {
                    best = maxAlertLevel(best, alert.level)
                }
            }
        }

        return best
    }

    private fun highestOblastWideAlert(
        oblastName: String,
        oblastAlerts: List<ActiveAlertInfo>
    ): AlertLevel {
        val oblastKey = canonicalOblastKey(oblastName)
        var best = AlertLevel.NONE
        for (alert in oblastAlerts) {
            val alertOblastKey = canonicalOblastKey(alert.oblastName.ifBlank { alert.regionName })
            if (oblastKeysMatch(oblastKey, alertOblastKey)) {
                best = maxAlertLevel(best, alert.level)
                if (best == AlertLevel.RED) return best
            }
        }
        return best
    }

    private fun highestLocalAlertForRaion(
        raionName: String,
        oblastName: String,
        localAlerts: List<ActiveAlertInfo>
    ): AlertLevel {
        val raionKey = canonicalRaionKey(raionName)
        if (raionKey.isBlank()) return AlertLevel.NONE
        val oblastKey = canonicalOblastKey(oblastName)
        var best = AlertLevel.NONE
        for (alert in localAlerts) {
            val alertOblast = canonicalOblastKey(alert.oblastName)
            if (alertOblast.isNotBlank() && !oblastKeysMatch(oblastKey, alertOblast)) continue
            if (raionKeysMatch(raionKey, canonicalRaionKey(alert.regionName))) {
                best = maxAlertLevel(best, alert.level)
                if (best == AlertLevel.RED) return best
            }
        }
        return best
    }

    private fun highestLocalAlertForHromada(
        hromada: RegionPolygon,
        localAlerts: List<ActiveAlertInfo>
    ): AlertLevel {
        val hromadaKey = canonicalHromadaKey(hromada.name)
        if (hromadaKey.isBlank()) return AlertLevel.NONE
        val oblastKey = canonicalOblastKey(hromada.oblastName)
        var best = AlertLevel.NONE
        for (alert in localAlerts) {
            val alertOblastKey = canonicalOblastKey(alert.oblastName)
            if (alertOblastKey.isNotBlank() && !oblastKeysMatch(oblastKey, alertOblastKey)) {
                continue
            }
            if (hromadaKeysMatch(hromadaKey, canonicalHromadaKey(alert.regionName))) {
                best = maxAlertLevel(best, alert.level)
                if (best == AlertLevel.RED) return best
            }
        }
        return best
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

            val radiusKm = if (threat.type == ThreatType.BALLISTIC || threat.type == ThreatType.CRUISE_MISSILE) {
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

    private fun oblastKeysMatch(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        // Crimea-wide alert ("9999") also applies to Sevastopol if no separate Sevastopol code is emitted.
        if ((a == "sevastopol" && b == "9999") || (a == "9999" && b == "sevastopol")) return true
        return false
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

    private fun raionKeysMatch(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        return a == b
    }

    private fun hromadaKeysMatch(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        if (a.length >= 5 && b.length >= 5 && (a.startsWith(b) || b.startsWith(a))) return true
        return false
    }

    private fun stripAdjectivalSuffix(input: String): String {
        val s = input.trim()
        val suffixes = listOf("ський", "ська", "ське", "цький", "цька", "цьке", "зький", "зька", "зьке")
        for (suffix in suffixes) {
            if (s.length > suffix.length + 3 && s.endsWith(suffix)) {
                return s.dropLast(suffix.length)
            }
        }
        val shortSuffixes = listOf("ий", "ій", "а", "я", "е", "о", "и", "і")
        for (suffix in shortSuffixes) {
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
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
