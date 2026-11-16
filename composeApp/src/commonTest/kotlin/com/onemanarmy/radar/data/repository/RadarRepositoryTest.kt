package com.onemanarmy.radar.data.repository

import com.onemanarmy.radar.data.remote.HttpClientFactory
import com.onemanarmy.radar.data.remote.MapaUaRadarApi
import com.onemanarmy.radar.data.remote.NeptunRadarApi
import com.onemanarmy.radar.domain.model.AirThreat
import com.onemanarmy.radar.domain.model.SavedPoint
import com.onemanarmy.radar.domain.model.ThreatDataSource
import com.onemanarmy.radar.domain.model.ThreatType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadarRepositoryTest {

    private val emptyClient = HttpClient(MockEngine { respondOk("[]") })

    private val repo = RadarRepository(
        neptunRadarApi = NeptunRadarApi(emptyClient),
        mapaUaRadarApi = MapaUaRadarApi(emptyClient)
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

    @Test
    fun fetchRawThreats_routesToSelectedSource() = runTest {
        val neptunClient = HttpClient(
            MockEngine {
                respond(
                    content = """
                        {
                          "threats": [
                            { "id": "neptun_1", "type": "uav", "title": "Neptun UAV", "lat": 50.45, "lon": 30.52 }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        ) {
            install(ContentNegotiation) { json(HttpClientFactory.sharedJson) }
        }

        val mapaClient = HttpClient(
            MockEngine {
                respond(
                    content = """
                        {
                          "objects": [
                            { "id": 99, "kind": "missile_cruise", "title": "Mapa Missile", "status": "active", "lat": 49.0, "lon": 32.0 }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        ) {
            install(ContentNegotiation) { json(HttpClientFactory.sharedJson) }
        }

        val switchingRepo = RadarRepository(
            neptunRadarApi = NeptunRadarApi(neptunClient),
            mapaUaRadarApi = MapaUaRadarApi(mapaClient)
        )

        val neptunThreats = switchingRepo.fetchRawThreats(ThreatDataSource.NEPTUN).getOrThrow()
        assertEquals(1, neptunThreats.size)
        assertEquals("neptun_1", neptunThreats.first().id)
        assertEquals(ThreatType.UAV, neptunThreats.first().type)

        val mapaThreats = switchingRepo.fetchRawThreats(ThreatDataSource.MAPA_UA).getOrThrow()
        assertEquals(1, mapaThreats.size)
        assertEquals("mapa_99", mapaThreats.first().id)
        assertEquals(ThreatType.CRUISE_MISSILE, mapaThreats.first().type)
    }

    @Test
    fun fetchRawThreats_throttlesRepeatedCallsPerSourceWithinCooldown() = runTest {
        var neptunRequests = 0
        var mapaRequests = 0
        var fakeNowMs = 10_000L

        val neptunClient = HttpClient(
            MockEngine {
                neptunRequests++
                respond(
                    content = """
                        {
                          "threats": [
                            { "id": "neptun_$neptunRequests", "type": "uav", "title": "Neptun UAV", "lat": 50.45, "lon": 30.52 }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        ) {
            install(ContentNegotiation) { json(HttpClientFactory.sharedJson) }
        }

        val mapaClient = HttpClient(
            MockEngine {
                mapaRequests++
                respond(
                    content = """
                        {
                          "objects": [
                            { "id": $mapaRequests, "kind": "uav", "title": "Mapa UAV", "status": "active", "lat": 49.0, "lon": 32.0 }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        ) {
            install(ContentNegotiation) { json(HttpClientFactory.sharedJson) }
        }

        val throttledRepo = RadarRepository(
            neptunRadarApi = NeptunRadarApi(neptunClient),
            mapaUaRadarApi = MapaUaRadarApi(mapaClient),
            minRequestIntervalMs = 10_000L,
            nowEpochMs = { fakeNowMs }
        )

        // First NEPTUN fetch hits the API once
        val firstNeptun = throttledRepo.fetchRawThreats(ThreatDataSource.NEPTUN).getOrThrow()
        assertEquals(1, neptunRequests)
        assertEquals("neptun_1", firstNeptun.first().id)

        // Switching to MAPA_UA is independent and hits MAPA_UA once
        fakeNowMs += 1_000L
        val firstMapa = throttledRepo.fetchRawThreats(ThreatDataSource.MAPA_UA).getOrThrow()
        assertEquals(1, mapaRequests)
        assertEquals("mapa_1", firstMapa.first().id)

        // Switching back to NEPTUN within 10s reuses cached response without hitting NEPTUN again
        fakeNowMs += 4_000L
        val cachedNeptun = throttledRepo.fetchRawThreats(ThreatDataSource.NEPTUN).getOrThrow()
        assertEquals(1, neptunRequests)
        assertEquals("neptun_1", cachedNeptun.first().id)

        // Advancing past 10s allows a fresh NEPTUN request
        fakeNowMs += 5_000L
        val refreshedNeptun = throttledRepo.fetchRawThreats(ThreatDataSource.NEPTUN).getOrThrow()
        assertEquals(2, neptunRequests)
        assertEquals("neptun_2", refreshedNeptun.first().id)
    }

    @Test
    fun fetchRawThreats_throttlesRetriesAfterFailure() = runTest {
        var neptunAttempts = 0
        var fakeNowMs = 50_000L

        val failingClient = HttpClient(
            MockEngine {
                neptunAttempts++
                respond(
                    content = "Too Many Requests",
                    status = HttpStatusCode.TooManyRequests
                )
            }
        ) {
            install(ContentNegotiation) { json(HttpClientFactory.sharedJson) }
        }

        val repoWithCooldown = RadarRepository(
            neptunRadarApi = NeptunRadarApi(failingClient),
            mapaUaRadarApi = MapaUaRadarApi(emptyClient),
            minRequestIntervalMs = 10_000L,
            nowEpochMs = { fakeNowMs }
        )

        val firstAttempt = repoWithCooldown.fetchRawThreats(ThreatDataSource.NEPTUN)
        assertTrue(firstAttempt.isFailure)
        // NeptunRadarApi tries /live then /threats fallback = 2 HTTP calls for the single fetch attempt
        val attemptsAfterFirstFetch = neptunAttempts

        // Immediate retry within 10s cooldown must NOT hit the network again
        fakeNowMs += 5_000L
        val secondAttempt = repoWithCooldown.fetchRawThreats(ThreatDataSource.NEPTUN)
        assertTrue(secondAttempt.isFailure)
        assertEquals(attemptsAfterFirstFetch, neptunAttempts)

        // After 10s cooldown elapses, retry is allowed
        fakeNowMs += 5_000L
        val thirdAttempt = repoWithCooldown.fetchRawThreats(ThreatDataSource.NEPTUN)
        assertTrue(thirdAttempt.isFailure)
        assertTrue(neptunAttempts > attemptsAfterFirstFetch)
    }
}
