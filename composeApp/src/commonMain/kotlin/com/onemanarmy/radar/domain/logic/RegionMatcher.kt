package com.onemanarmy.radar.domain.logic

import com.onemanarmy.radar.domain.model.ActiveAlertInfo
import com.onemanarmy.radar.domain.model.RegionItem
import com.onemanarmy.radar.domain.model.RegionType
import com.onemanarmy.radar.domain.model.SavedPoint

/**
 * Matches WGS84 coordinates and reverse-geocoded place names to UkraineAlarm & NEPTUN regions.
 */
object RegionMatcher {

    data class OblastCentroid(
        val regionId: String,
        val ukName: String,
        val engName: String,
        val lat: Double,
        val lon: Double,
        val aliases: List<String> = emptyList()
    )

    /**
     * Built-in reference centroids for Ukrainian Oblasts and cities with special status (UkraineAlarm State IDs).
     */
    val oblastCentroids: List<OblastCentroid> = listOf(
        OblastCentroid("31", "м. Київ", "Kyiv", 50.4501, 30.5234, listOf("м. київ", "київ", "kyiv", "kiev")),
        OblastCentroid("14", "Київська область", "Kyivska oblast", 50.1500, 30.6500, listOf("київська", "kyivska", "kyiv oblast")),
        OblastCentroid("4", "Вінницька область", "Vinnytska oblast", 49.2331, 28.4682, listOf("вінниц", "vinnyts")),
        OblastCentroid("8", "Волинська область", "Volynska oblast", 50.7472, 25.3254, listOf("волин", "луцьк", "volyn", "lutsk")),
        OblastCentroid("9", "Дніпропетровська область", "Dnipropetrovska oblast", 48.4647, 35.0462, listOf("дніпро", "dnipro", "камянськ", "кам'янськ", "kamianske", "кривий ріг", "kryvyi")),
        OblastCentroid("28", "Донецька область", "Donetska oblast", 48.0159, 37.8028, listOf("донец", "donets", "краматорськ", "слов'янськ")),
        OblastCentroid("10", "Житомирська область", "Zhytomyrska oblast", 50.2547, 28.6587, listOf("житомир", "zhytomyr")),
        OblastCentroid("11", "Закарпатська область", "Zakarpatska oblast", 48.6208, 22.2879, listOf("закарпат", "ужгород", "zakarpat", "uzhhorod")),
        OblastCentroid("12", "Запорізька область", "Zaporizka oblast", 47.8388, 35.1396, listOf("запоріз", "запоріж", "zaporiz")),
        OblastCentroid("13", "Івано-Франківська область", "Ivano-Frankivska oblast", 48.9226, 24.7111, listOf("івано-франків", "ivano-frankiv")),
        OblastCentroid("15", "Кіровоградська область", "Kirovohradska oblast", 48.5079, 32.2623, listOf("кіровоград", "кропивниц", "kirovohrad", "kropyvnyts")),
        OblastCentroid("16", "Луганська область", "Luhanska oblast", 48.5740, 39.3078, listOf("луган", "luhan")),
        OblastCentroid("27", "Львівська область", "Lvivska oblast", 49.8397, 24.0297, listOf("львів", "lviv")),
        OblastCentroid("17", "Миколаївська область", "Mykolaivska oblast", 46.9750, 31.9946, listOf("миколаїв", "mykolaiv")),
        OblastCentroid("18", "Одеська область", "Odeska oblast", 46.4825, 30.7233, listOf("одес", "odes")),
        OblastCentroid("19", "Полтавська область", "Poltavska oblast", 49.5883, 34.5514, listOf("полтав", "poltav", "кременчук")),
        OblastCentroid("5", "Рівненська область", "Rivnenska oblast", 50.6199, 26.2516, listOf("рівнен", "рівне", "rivne")),
        OblastCentroid("20", "Сумська область", "Sumska oblast", 50.9077, 34.7981, listOf("сумськ", "суми", "sumy", "sumska")),
        OblastCentroid("21", "Тернопільська область", "Ternopilska oblast", 49.5535, 25.5948, listOf("терноп", "ternopil")),
        OblastCentroid("22", "Харківська область", "Kharkivska oblast", 49.9935, 36.2304, listOf("харків", "kharkiv")),
        OblastCentroid("23", "Херсонська область", "Khersonska oblast", 46.6354, 32.6169, listOf("херсон", "kherson")),
        OblastCentroid("3", "Хмельницька область", "Khmelnytska oblast", 49.4230, 26.9871, listOf("хмельниц", "khmelnyts")),
        OblastCentroid("24", "Черкаська область", "Cherkaska oblast", 49.4444, 32.0598, listOf("черкас", "cherkas")),
        OblastCentroid("26", "Чернівецька область", "Chernivetska oblast", 48.2921, 25.9358, listOf("чернівец", "чернівц", "cherniv")),
        OblastCentroid("25", "Чернігівська область", "Chernihivska oblast", 51.4982, 31.2893, listOf("чернігів", "chernihiv")),
        OblastCentroid("9999", "Автономна Республіка Крим", "Crimea", 44.9521, 34.1024, listOf("крим", "crimea", "сімферополь", "севастополь"))
    )

    private data class NormalizedOblastEntry(
        val centroid: OblastCentroid,
        val normUk: String,
        val normEn: String,
        val normAliases: List<String>
    )

    private val normalizedOblasts: List<NormalizedOblastEntry> by lazy {
        oblastCentroids.map { c ->
            NormalizedOblastEntry(
                centroid = c,
                normUk = normalizeName(c.ukName),
                normEn = normalizeName(c.engName),
                normAliases = c.aliases.map { normalizeName(it) }
            )
        }
    }

    private val kyivCityCentroid: OblastCentroid? by lazy {
        oblastCentroids.firstOrNull { it.regionId == "31" }
    }

    private val kyivOblastCentroid: OblastCentroid? by lazy {
        oblastCentroids.firstOrNull { it.regionId == "14" }
    }

    fun findOblastByName(rawName: String?): OblastCentroid? {
        if (rawName.isNullOrBlank()) return null
        val normalized = normalizeName(rawName)
        if (normalized.isBlank()) return null

        // Check Kyiv vs Kyivska oblast explicitly first because "київ" is a prefix of "київська".
        if (normalized == "київ" || normalized == "kyiv" || normalized == "kiev") {
            return kyivCityCentroid
        }
        if (normalized.startsWith("київськ") || normalized.startsWith("kyivsk")) {
            return kyivOblastCentroid
        }

        for (entry in normalizedOblasts) {
            if (entry.normUk == normalized || entry.normEn == normalized) {
                return entry.centroid
            }
            for (alias in entry.normAliases) {
                if (normalized == alias || normalized.contains(alias)) {
                    return entry.centroid
                }
            }
        }
        return null
    }

    /**
     * Resolves the best matching region using cached directory + geocoded names + coordinate fallback.
     */
    fun matchRegion(
        latitude: Double,
        longitude: Double,
        localityName: String?,
        districtName: String?,
        adminAreaName: String?,
        cachedRegions: List<RegionItem>
    ): RegionItem {
        val candidates = listOfNotNull(localityName, districtName, adminAreaName)
            .map { normalizeName(it) }
            .filter { it.isNotBlank() }

        if (cachedRegions.isNotEmpty() && candidates.isNotEmpty()) {
            // 1. Prefer exact or partial community/city match.
            for (candidate in candidates) {
                val communityMatch = cachedRegions.firstOrNull { region ->
                    region.type == RegionType.COMMUNITY && matchesCandidate(region, candidate)
                }
                if (communityMatch != null) return communityMatch
            }
            // 2. Try district match.
            for (candidate in candidates) {
                val districtMatch = cachedRegions.firstOrNull { region ->
                    region.type == RegionType.DISTRICT && matchesCandidate(region, candidate)
                }
                if (districtMatch != null) return districtMatch
            }
            // 3. Try state/oblast match.
            for (candidate in candidates) {
                val stateMatch = cachedRegions.firstOrNull { region ->
                    region.type == RegionType.STATE && matchesCandidate(region, candidate)
                }
                if (stateMatch != null) return stateMatch
            }
        }

        // 4. Try explicit oblast match (prefers adminArea before locality so Kyivska oblast cities map to oblast 14, not Kyiv 31).
        val orderedOblastCandidates = listOfNotNull(adminAreaName, districtName, localityName)
        for (rawCandidate in orderedOblastCandidates) {
            val hit = findOblastByName(rawCandidate)
            if (hit != null) {
                val fromCache = cachedRegions.firstOrNull { it.regionId == hit.regionId }
                return fromCache ?: RegionItem(
                    regionId = hit.regionId,
                    name = hit.ukName,
                    engName = hit.engName,
                    type = RegionType.STATE
                )
            }
        }

        // 5. Fallback: nearest oblast centroid via Haversine distance.
        val nearest = oblastCentroids.minBy { centroid ->
            GeoUtils.haversineDistanceKm(latitude, longitude, centroid.lat, centroid.lon)
        }
        val fromCache = cachedRegions.firstOrNull { it.regionId == nearest.regionId }
        return fromCache ?: RegionItem(
            regionId = nearest.regionId,
            name = nearest.ukName,
            engName = nearest.engName,
            type = RegionType.STATE
        )
    }

    /**
     * Checks if an active alert regionId matches a tracked regionId, including parent/child hierarchy
     * when cachedRegions are available.
     */
    fun isRegionAffectedByAlert(
        trackedRegionId: String,
        alertRegionId: String,
        cachedRegions: List<RegionItem>
    ): Boolean {
        if (trackedRegionId == alertRegionId) return true
        val regionMap = cachedRegions.associateBy { it.regionId }
        var current = regionMap[trackedRegionId]
        var depth = 0
        while (current?.parentId != null && depth < 4) {
            if (current.parentId == alertRegionId) return true
            current = regionMap[current.parentId]
            depth++
        }
        return false
    }

    /**
     * Unified matcher supporting both UkraineAlarm region IDs and NEPTUN oblast/raion alerts.
     */
    fun isPointAffectedByAlert(
        point: SavedPoint,
        alert: ActiveAlertInfo,
        cachedRegions: List<RegionItem>
    ): Boolean {
        if (isRegionAffectedByAlert(point.regionId, alert.regionId, cachedRegions)) {
            return true
        }

        val pointOblast = point.oblastName.ifBlank {
            findOblastByName(point.regionName)?.ukName
                ?: oblastCentroids.firstOrNull { it.regionId == point.regionId }?.ukName
                ?: ""
        }
        val alertOblast = alert.oblastName.ifBlank {
            findOblastByName(alert.regionName)?.ukName.orEmpty()
        }

        val normPointOblast = normalizeName(pointOblast)
        val normAlertOblast = normalizeName(alertOblast)

        if (alert.isOblastWide) {
            if (normPointOblast.isNotBlank() && normAlertOblast.isNotBlank() &&
                matchesNormalized(normPointOblast, normAlertOblast)
            ) {
                return true
            }
            return matchesNormalized(normalizeName(point.regionName), normalizeName(alert.regionName))
        }

        // Raion-level alert (from NEPTUN raions list):
        // First verify oblast matches when both sides know their oblast.
        if (normPointOblast.isNotBlank() && normAlertOblast.isNotBlank() &&
            !matchesNormalized(normPointOblast, normAlertOblast)
        ) {
            return false
        }

        val normAlertTarget = normalizeName(alert.regionName)
        if (normAlertTarget.isBlank()) return false

        // Match against explicit hromadaName first (e.g., hromada-level alerts).
        if (point.hromadaName.isNotBlank()) {
            val normHromada = normalizeName(point.hromadaName)
            if (matchesNormalized(normHromada, normAlertTarget)) {
                return true
            }
        }

        // Match against explicit districtName (including post-2020 raion renames).
        if (point.districtName.isNotBlank()) {
            val normDistrict = normalizeRaionAlias(normalizeName(point.districtName))
            val normAlertRaion = normalizeRaionAlias(normAlertTarget)
            if (matchesNormalized(normDistrict, normAlertRaion)) {
                return true
            }
        }

        // Also match against point.regionName or point.name stems (e.g., "Херсон" -> "Херсонський район").
        val normRegion = normalizeName(point.regionName)
        val normPointName = normalizeName(point.name)
        if (normRegion.isNotBlank() && normRegion != normPointOblast && matchesNormalized(normRegion, normAlertTarget)) {
            return true
        }
        if (normPointName.isNotBlank() && matchesNormalized(normPointName, normAlertTarget)) {
            return true
        }

        return false
    }

    private fun normalizeRaionAlias(norm: String): String = when {
        norm.contains("новоград") || norm.contains("звягель") -> "звягельськ"
        norm.contains("новомосков") || norm.contains("самарів") -> "самарівськ"
        norm.contains("красноград") || norm.contains("берестин") -> "берестинськ"
        norm.contains("червоноград") || norm.contains("шептицьк") -> "шептицьк"
        norm.contains("дніпропетров") -> "дніпровськ"
        norm.contains("кіровоград") -> "кропивницьк"
        norm.contains("артемів") -> "бахмутськ"
        else -> norm
    }

    private fun matchesCandidate(region: RegionItem, normalizedCandidate: String): Boolean {
        val uk = normalizeName(region.name)
        val en = normalizeName(region.engName)
        return matchesNormalized(uk, normalizedCandidate) || matchesNormalized(en, normalizedCandidate)
    }

    private fun matchesNormalized(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        if (a == b || a.contains(b) || b.contains(a)) return true
        if (a.length >= 6 && b.length >= 6) {
            val stemA = a.take(6)
            val stemB = b.take(6)
            if (stemA == stemB) return true
        }
        return false
    }

    internal fun normalizeName(raw: String): String {
        return raw.lowercase()
            .replace("м.", "")
            .replace("місто", "")
            .replace("city", "")
            .replace("oblast", "")
            .replace("область", "")
            .replace("області", "")
            .replace("району", "")
            .replace("район", "")
            .replace("raion", "")
            .replace("district", "")
            .replace("територіальна громада", "")
            .replace("громада", "")
            .replace("міськрада", "")
            .replace("community", "")
            .replace("'", "")
            .replace("’", "")
            .replace("`", "")
            .trim()
    }
}
