package com.onemanarmy.airalerts.data.repository

import com.onemanarmy.airalerts.data.remote.NeptunRadarApi
import com.onemanarmy.airalerts.domain.model.AirThreat
import com.onemanarmy.airalerts.domain.model.SavedPoint
import com.onemanarmy.airalerts.domain.model.ThreatType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadarRepositoryTest {

    private val repo = RadarRepository(
        neptunRadarApi = NeptunRadarApi(HttpClient(MockEngine { respondOk("[]") }))
    )

    @Test
    fun enrichProximity_marksThreatsInsideWarningRadiusAndRespondsToRadiusChanges() {
        val kyivPoint = SavedPoint(
            id = "p_kyiv",
            name = "Home",
            latitude = 50.4501,
            longitude = 30.5234,
            regionId = "31",
            regionName = "м. Київ"
        )
        // ~24 km east of central Kyiv (Brovary area)
        val nearThreat = AirThreat(
            id = "near_1",
            type = ThreatType.UAV,
            title = "Shahed",
            region = "Київська область",
            locality = "Бровари",
            latitude = 50.511,
            longitude = 30.790
        )
        // ~130 km west (Zhytomyr)
        val farThreat = AirThreat(
            id = "far_1",
            type = ThreatType.CRUISE_MISSILE,
            title = "Cruise Missile",
            region = "Житомирська область",
            locality = "Житомир",
            latitude = 50.2547,
            longitude = 28.6587
        )

        val within30 = repo.enrichProximity(
            threats = listOf(nearThreat, farThreat),
            trackedPoints = listOf(kyivPoint),
            warningRadiusKm = 30.0
        )
        assertEquals(2, within30.size)
        assertTrue(within30[0].isWithinWarningRadius)
        assertEquals("Home", within30[0].nearestPointName)
        assertFalse(within30[1].isWithinWarningRadius)

        // Shrinking radius to 10 km immediately excludes the ~20 km target
        val within10 = repo.enrichProximity(
            threats = listOf(nearThreat, farThreat),
            trackedPoints = listOf(kyivPoint),
            warningRadiusKm = 10.0
        )
        assertFalse(within10[0].isWithinWarningRadius)
        assertFalse(within10[1].isWithinWarningRadius)
    }
}
