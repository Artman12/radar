package com.onemanarmy.radar.domain.logic

import com.onemanarmy.radar.domain.model.UkraineMapBoundaries
import com.onemanarmy.radar.platform.GeocodedAddress

/**
 * Pure-Kotlin partial & prefix place matcher across Ukraine's administrative divisions
 * (oblasts, cities with special status, 137 raions, 1,472 hromadas) with Ukrainian/Latin support.
 */
object PlaceSearchMatcher {

    private data class ScoredPlace(
        val score: Int,
        val levelPriority: Int,
        val nameLength: Int,
        val address: GeocodedAddress
    )

    fun search(
        query: String,
        boundaries: UkraineMapBoundaries?,
        limit: Int = 15
    ): List<GeocodedAddress> {
        val rawNorm = normalizeSearchText(query)
        if (rawNorm.isBlank()) return emptyList()
        val queryTranslit = normalizeSearchText(ukrainianToLatin(rawNorm))

        val candidates = mutableListOf<ScoredPlace>()
        val seenKeys = HashSet<String>()

        fun addCandidate(
            score: Int,
            levelPriority: Int,
            primaryName: String,
            address: GeocodedAddress
        ) {
            val key = "${normalizeSearchText(address.locality ?: primaryName)}|${normalizeSearchText(address.adminArea.orEmpty())}"
            if (seenKeys.add(key)) {
                candidates += ScoredPlace(
                    score = score,
                    levelPriority = levelPriority,
                    nameLength = primaryName.length,
                    address = address
                )
            }
        }

        if (boundaries != null) {
            for (oblast in boundaries.oblasts) {
                val primary = stripCityPrefix(oblast.name)
                val matchScore = matchScore(primary, "", oblast.name, rawNorm, queryTranslit)
                if (matchScore != null) {
                    addCandidate(
                        score = matchScore,
                        levelPriority = 0,
                        primaryName = primary,
                        address = GeocodedAddress(
                            displayName = oblast.name,
                            locality = primary,
                            district = null,
                            adminArea = oblast.name,
                            latitude = oblast.centerLat.toDouble(),
                            longitude = oblast.centerLon.toDouble()
                        )
                    )
                }
            }

            for (hromada in boundaries.hromadas) {
                val matchScore = matchScore(
                    primary = hromada.name,
                    secondary = hromada.raionName,
                    tertiary = hromada.oblastName,
                    normQuery = rawNorm,
                    translitQuery = queryTranslit
                )
                if (matchScore != null) {
                    val display = listOf(hromada.name, hromada.raionName, hromada.oblastName)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(", ")
                    addCandidate(
                        score = matchScore,
                        levelPriority = 1,
                        primaryName = hromada.name,
                        address = GeocodedAddress(
                            displayName = display,
                            locality = hromada.name,
                            district = hromada.raionName.ifBlank { null },
                            adminArea = hromada.oblastName.ifBlank { null },
                            latitude = hromada.centerLat.toDouble(),
                            longitude = hromada.centerLon.toDouble()
                        )
                    )
                }
            }

            for (raion in boundaries.raions) {
                val matchScore = matchScore(
                    primary = raion.name,
                    secondary = "",
                    tertiary = raion.oblastName,
                    normQuery = rawNorm,
                    translitQuery = queryTranslit
                )
                if (matchScore != null) {
                    val display = listOf(raion.name, raion.oblastName)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(", ")
                    addCandidate(
                        score = matchScore,
                        levelPriority = 2,
                        primaryName = raion.name,
                        address = GeocodedAddress(
                            displayName = display,
                            locality = raion.name,
                            district = raion.name,
                            adminArea = raion.oblastName.ifBlank { null },
                            latitude = raion.centerLat.toDouble(),
                            longitude = raion.centerLon.toDouble()
                        )
                    )
                }
            }
        }

        // Also match RegionMatcher built-in centroids (includes English names & major city aliases).
        for (centroid in RegionMatcher.oblastCentroids) {
            val primary = stripCityPrefix(centroid.ukName)
            val enPrimary = centroid.engName
            val ukScore = matchScore(primary, "", centroid.ukName, rawNorm, queryTranslit)
            val enScore = matchScore(enPrimary, "", centroid.engName, rawNorm, queryTranslit)
            val bestScore = listOfNotNull(ukScore, enScore).minOrNull()
            if (bestScore != null) {
                addCandidate(
                    score = bestScore,
                    levelPriority = 0,
                    primaryName = primary,
                    address = GeocodedAddress(
                        displayName = centroid.ukName,
                        locality = primary,
                        district = null,
                        adminArea = centroid.ukName,
                        latitude = centroid.lat,
                        longitude = centroid.lon
                    )
                )
            }
        }

        return candidates
            .sortedWith(
                compareBy<ScoredPlace> { it.score }
                    .thenBy { it.levelPriority }
                    .thenBy { it.nameLength }
                    .thenBy { it.address.displayName }
            )
            .take(limit)
            .map { it.address }
    }

    fun mergeResults(
        local: List<GeocodedAddress>,
        remote: List<GeocodedAddress>,
        limit: Int = 15
    ): List<GeocodedAddress> {
        if (local.isEmpty()) return remote.take(limit)
        if (remote.isEmpty()) return local.take(limit)

        val merged = ArrayList<GeocodedAddress>(local.size + remote.size)
        val seenKeys = HashSet<String>()

        fun addIfUnique(item: GeocodedAddress) {
            val normLoc = normalizeSearchText(item.locality ?: item.displayName.substringBefore(","))
            val normAdmin = normalizeSearchText(item.adminArea.orEmpty())
            val key = "$normLoc|$normAdmin"
            val nearDuplicate = merged.any { existing ->
                val sameName = normalizeSearchText(existing.locality ?: existing.displayName.substringBefore(",")) == normLoc
                sameName && GeoUtils.haversineDistanceKm(
                    existing.latitude,
                    existing.longitude,
                    item.latitude,
                    item.longitude
                ) < 5.0
            }
            if (!nearDuplicate && seenKeys.add(key)) {
                merged += item
            }
        }

        local.forEach(::addIfUnique)
        remote.forEach(::addIfUnique)
        return merged.take(limit)
    }

    private fun matchScore(
        primary: String,
        secondary: String,
        tertiary: String,
        normQuery: String,
        translitQuery: String
    ): Int? {
        val normPrimary = normalizeSearchText(primary)
        val translitPrimary = normalizeSearchText(ukrainianToLatin(normPrimary))

        if (normPrimary == normQuery || translitPrimary == translitQuery) return 0
        if (normPrimary.startsWith(normQuery) || translitPrimary.startsWith(translitQuery)) return 1

        val words = normPrimary.split(' ').filter { it.isNotBlank() }
        val translitWords = translitPrimary.split(' ').filter { it.isNotBlank() }
        if (words.any { it.startsWith(normQuery) } || translitWords.any { it.startsWith(translitQuery) }) {
            return 2
        }

        if (normPrimary.contains(normQuery) || translitPrimary.contains(translitQuery)) return 3

        val normSecondary = normalizeSearchText(secondary)
        val normTertiary = normalizeSearchText(tertiary)
        val translitSecondary = normalizeSearchText(ukrainianToLatin(normSecondary))
        val translitTertiary = normalizeSearchText(ukrainianToLatin(normTertiary))

        if (normSecondary.contains(normQuery) || translitSecondary.contains(translitQuery) ||
            normTertiary.contains(normQuery) || translitTertiary.contains(translitQuery)
        ) {
            return 4
        }

        return null
    }

    private fun stripCityPrefix(raw: String): String {
        return raw.removePrefix("м. ").removePrefix("м.").trim()
    }

    internal fun normalizeSearchText(raw: String): String {
        return raw.lowercase()
            .replace("'", "")
            .replace("’", "")
            .replace("`", "")
            .replace("-", " ")
            .replace("–", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    internal fun ukrainianToLatin(input: String): String {
        val sb = StringBuilder(input.length + 8)
        for (ch in input.lowercase()) {
            sb.append(
                when (ch) {
                    'а' -> "a"
                    'б' -> "b"
                    'в' -> "v"
                    'г' -> "h"
                    'ґ' -> "g"
                    'д' -> "d"
                    'е' -> "e"
                    'є' -> "ye"
                    'ж' -> "zh"
                    'з' -> "z"
                    'и' -> "y"
                    'і' -> "i"
                    'ї' -> "yi"
                    'й' -> "y"
                    'к' -> "k"
                    'л' -> "l"
                    'м' -> "m"
                    'н' -> "n"
                    'о' -> "o"
                    'п' -> "p"
                    'р' -> "r"
                    'с' -> "s"
                    'т' -> "t"
                    'у' -> "u"
                    'ф' -> "f"
                    'х' -> "kh"
                    'ц' -> "ts"
                    'ч' -> "ch"
                    'ш' -> "sh"
                    'щ' -> "shch"
                    'ь' -> ""
                    'ю' -> "yu"
                    'я' -> "ya"
                    else -> ch.toString()
                }
            )
        }
        return sb.toString()
    }
}
