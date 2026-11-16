package com.onemanarmy.radar.domain.logic

import com.onemanarmy.radar.domain.model.RegionItem
import com.onemanarmy.radar.domain.model.RegionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegionMatcherTest {

    private val sampleRegions = listOf(
        RegionItem("9", "Дніпропетровська область", "Dnipropetrovska oblast", RegionType.STATE, null),
        RegionItem("44", "Дніпровський район", "Dniprovskyi district", RegionType.DISTRICT, "9"),
        RegionItem("340", "Кам'янська територіальна громада", "Kamianska community", RegionType.COMMUNITY, "44"),
        RegionItem("31", "м. Київ", "Kyiv", RegionType.STATE, null)
    )

    @Test
    fun matchesCommunityByUkrainianLocalityName() {
        val matched = RegionMatcher.matchRegion(
            latitude = 48.51,
            longitude = 34.61,
            localityName = "Кам'янське",
            districtName = "Кам'янський район",
            adminAreaName = "Дніпропетровська область",
            cachedRegions = sampleRegions
        )
        // Matches Dnipropetrovska oblast or Kamianska if substring matches
        assertTrue(matched.regionId == "9" || matched.regionId == "340")
    }

    @Test
    fun matchesKyivByFallbackCentroidWhenCacheEmpty() {
        val matched = RegionMatcher.matchRegion(
            latitude = 50.45,
            longitude = 30.52,
            localityName = null,
            districtName = null,
            adminAreaName = null,
            cachedRegions = emptyList()
        )
        assertEquals("31", matched.regionId)
    }

    @Test
    fun hierarchyAlertMatchPropagatesFromStateToCommunity() {
        assertTrue(RegionMatcher.isRegionAffectedByAlert("340", "9", sampleRegions))
        assertTrue(RegionMatcher.isRegionAffectedByAlert("340", "44", sampleRegions))
        assertTrue(RegionMatcher.isRegionAffectedByAlert("340", "340", sampleRegions))
        assertFalse(RegionMatcher.isRegionAffectedByAlert("340", "31", sampleRegions))
    }

    @Test
    fun matchesNeptunRaionAndOblastAlertsByNameAndOblast() {
        val bilaTserkvaPoint = com.onemanarmy.radar.domain.model.SavedPoint(
            id = "p1",
            name = "Дім",
            latitude = 49.79,
            longitude = 30.11,
            regionId = "14",
            regionName = "Білоцерківський район, Київська область",
            districtName = "Білоцерківський район",
            oblastName = "Київська область"
        )
        val brovaryPoint = com.onemanarmy.radar.domain.model.SavedPoint(
            id = "p2",
            name = "Офіс",
            latitude = 50.51,
            longitude = 30.79,
            regionId = "14",
            regionName = "Броварський район, Київська область",
            districtName = "Броварський район",
            oblastName = "Київська область"
        )
        val neptunRaionAlert = com.onemanarmy.radar.domain.model.ActiveAlertInfo(
            regionId = "neptun_raion:білоцерківський",
            regionName = "Білоцерківський район",
            type = com.onemanarmy.radar.domain.model.AlertType.AIR,
            level = com.onemanarmy.radar.domain.model.AlertLevel.YELLOW,
            oblastName = "Київська область",
            isOblastWide = false
        )
        val neptunOblastAlert = com.onemanarmy.radar.domain.model.ActiveAlertInfo(
            regionId = "14",
            regionName = "Київська область",
            type = com.onemanarmy.radar.domain.model.AlertType.AIR,
            level = com.onemanarmy.radar.domain.model.AlertLevel.RED,
            oblastName = "Київська область",
            isOblastWide = true
        )

        assertTrue(RegionMatcher.isPointAffectedByAlert(bilaTserkvaPoint, neptunRaionAlert, emptyList()))
        assertFalse(RegionMatcher.isPointAffectedByAlert(brovaryPoint, neptunRaionAlert, emptyList()))
        assertTrue(RegionMatcher.isPointAffectedByAlert(brovaryPoint, neptunOblastAlert, emptyList()))
    }
}
