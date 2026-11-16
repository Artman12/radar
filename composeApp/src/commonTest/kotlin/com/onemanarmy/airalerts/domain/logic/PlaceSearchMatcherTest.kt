package com.onemanarmy.airalerts.domain.logic

import com.onemanarmy.airalerts.domain.model.AdminLevel
import com.onemanarmy.airalerts.domain.model.RegionPolygon
import com.onemanarmy.airalerts.domain.model.UkraineMapBoundaries
import com.onemanarmy.airalerts.platform.GeocodedAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceSearchMatcherTest {

    private fun sampleBoundaries() = UkraineMapBoundaries(
        countryRings = emptyList(),
        oblasts = listOf(
            RegionPolygon(
                id = "oblast_0",
                name = "м. Київ",
                raionName = "",
                oblastName = "м. Київ",
                level = AdminLevel.OBLAST,
                minLat = 50.2f,
                maxLat = 50.6f,
                minLon = 30.2f,
                maxLon = 30.8f,
                centerLat = 50.45f,
                centerLon = 30.52f,
                rings = emptyList()
            ),
            RegionPolygon(
                id = "oblast_1",
                name = "Київська область",
                raionName = "",
                oblastName = "Київська область",
                level = AdminLevel.OBLAST,
                minLat = 49.0f,
                maxLat = 51.5f,
                minLon = 29.0f,
                maxLon = 32.0f,
                centerLat = 50.15f,
                centerLon = 30.65f,
                rings = emptyList()
            )
        ),
        raions = listOf(
            RegionPolygon(
                id = "raion_0",
                name = "Броварський район",
                raionName = "Броварський район",
                oblastName = "Київська область",
                level = AdminLevel.RAION,
                minLat = 50.3f,
                maxLat = 50.8f,
                minLon = 30.6f,
                maxLon = 31.3f,
                centerLat = 50.52f,
                centerLon = 30.85f,
                rings = emptyList()
            )
        ),
        hromadas = listOf(
            RegionPolygon(
                id = "hromada_0",
                name = "Броварська",
                raionName = "Броварський район",
                oblastName = "Київська область",
                level = AdminLevel.HROMADA,
                minLat = 50.45f,
                maxLat = 50.58f,
                minLon = 30.72f,
                maxLon = 30.88f,
                centerLat = 50.51f,
                centerLon = 30.79f,
                rings = emptyList()
            ),
            RegionPolygon(
                id = "hromada_1",
                name = "Ірпінська",
                raionName = "Бучанський район",
                oblastName = "Київська область",
                level = AdminLevel.HROMADA,
                minLat = 50.48f,
                maxLat = 50.56f,
                minLon = 30.18f,
                maxLon = 30.30f,
                centerLat = 50.52f,
                centerLon = 30.24f,
                rings = emptyList()
            ),
            RegionPolygon(
                id = "hromada_2",
                name = "Кам'янська",
                raionName = "Кам'янський район",
                oblastName = "Дніпропетровська область",
                level = AdminLevel.HROMADA,
                minLat = 48.45f,
                maxLat = 48.56f,
                minLon = 34.50f,
                maxLon = 34.70f,
                centerLat = 48.51f,
                centerLon = 34.61f,
                rings = emptyList()
            )
        )
    )

    @Test
    fun partialUkrainianPrefixMatchesHromadaAndRaion() {
        val results = PlaceSearchMatcher.search("Бров", sampleBoundaries())

        assertTrue(results.isNotEmpty())
        assertEquals("Броварська", results.first().locality)
        assertTrue(results.any { it.district == "Броварський район" })
    }

    @Test
    fun partialQueryIgnoresApostrophesAndCase() {
        val results = PlaceSearchMatcher.search("камян", sampleBoundaries())

        assertTrue(results.isNotEmpty())
        assertEquals("Кам'янська", results.first().locality)
    }

    @Test
    fun partialLatinQueryMatchesUkrainianAndEnglishPlaces() {
        val brovResults = PlaceSearchMatcher.search("Brov", sampleBoundaries())
        assertTrue(brovResults.any { it.locality == "Броварська" })

        val lvivResults = PlaceSearchMatcher.search("Lvi", sampleBoundaries())
        assertTrue(lvivResults.any { it.displayName.contains("Львівська") })
    }

    @Test
    fun partialKyivMatchesCityFirstThenOblast() {
        val results = PlaceSearchMatcher.search("Киї", sampleBoundaries())

        assertTrue(results.size >= 2)
        assertEquals("Київ", results.first().locality)
    }

    @Test
    fun mergeResultsDeduplicatesNearbySameNameEntries() {
        val local = listOf(
            GeocodedAddress("Броварська, Броварський район, Київська область", "Броварська", "Броварський район", "Київська область", 50.51, 30.79)
        )
        val remote = listOf(
            GeocodedAddress("Броварська громада, Україна", "Броварська", "Броварський район", "Київська область", 50.512, 30.795),
            GeocodedAddress("Бровари, Київська область", "Бровари", "Броварський район", "Київська область", 50.511, 30.790)
        )

        val merged = PlaceSearchMatcher.mergeResults(local, remote)
        assertEquals(2, merged.size)
    }
}
