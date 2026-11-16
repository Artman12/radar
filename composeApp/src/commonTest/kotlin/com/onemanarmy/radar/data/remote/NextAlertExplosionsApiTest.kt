package com.onemanarmy.radar.data.remote

import com.onemanarmy.radar.data.repository.NextAlertExplosionsRepository
import com.onemanarmy.radar.domain.logic.ExplosionLocationResolver
import com.onemanarmy.radar.domain.model.ActiveAlertInfo
import com.onemanarmy.radar.domain.model.AdminLevel
import com.onemanarmy.radar.domain.model.AlertType
import com.onemanarmy.radar.domain.model.RegionPolygon
import com.onemanarmy.radar.domain.model.UkraineMapBoundaries
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NextAlertExplosionsApiTest {

    private fun testBoundaries(): UkraineMapBoundaries {
        val oblast = RegionPolygon(
            id = "oblast_0",
            name = "Харківська область",
            raionName = "",
            oblastName = "Харківська область",
            level = AdminLevel.OBLAST,
            minLat = 48.5f,
            maxLat = 50.5f,
            minLon = 34.5f,
            maxLon = 38.0f,
            centerLat = 49.70f,
            centerLon = 36.40f,
            rings = emptyList()
        )
        val raion = RegionPolygon(
            id = "raion_0",
            name = "Харківський район",
            raionName = "Харківський район",
            oblastName = "Харківська область",
            level = AdminLevel.RAION,
            minLat = 49.7f,
            maxLat = 50.3f,
            minLon = 35.8f,
            maxLon = 36.7f,
            centerLat = 49.90f,
            centerLon = 36.10f,
            rings = emptyList()
        )
        val kharkivHromada = RegionPolygon(
            id = "hromada_0",
            name = "Харківська",
            raionName = "Харківський район",
            oblastName = "Харківська область",
            level = AdminLevel.HROMADA,
            minLat = 49.9f,
            maxLat = 50.1f,
            minLon = 36.1f,
            maxLon = 36.4f,
            centerLat = 49.9935f,
            centerLon = 36.2304f,
            rings = emptyList()
        )
        val merefaHromada = RegionPolygon(
            id = "hromada_1",
            name = "Мереф'янська",
            raionName = "Харківський район",
            oblastName = "Харківська область",
            level = AdminLevel.HROMADA,
            minLat = 49.75f,
            maxLat = 49.88f,
            minLon = 36.0f,
            maxLon = 36.15f,
            centerLat = 49.8167f,
            centerLon = 36.0667f,
            rings = emptyList()
        )
        return UkraineMapBoundaries(
            countryRings = emptyList(),
            oblasts = listOf(oblast),
            raions = listOf(raion),
            hromadas = listOf(kharkivHromada, merefaHromada)
        )
    }

    @Test
    fun parsesDistrictTimelineAndEncodesCyrillicParams() = runTest {
        var capturedName: String? = null
        var capturedOblast: String? = null
        val engine = MockEngine { request ->
            capturedName = request.url.parameters["name"]
            capturedOblast = request.url.parameters["oblast"]
            respond(
                content = """
                    {
                      "name": "Харківський район",
                      "oblast": "Харківська область",
                      "intervals": [
                        {
                          "id": 28871,
                          "start_ts": "2026-11-16T14:01:38Z",
                          "ended_at": null,
                          "is_active": true,
                          "dur_min": 45,
                          "max_level": "red",
                          "blasts": [
                            { "date": "2026-11-16T14:15:00Z", "text": "ВИБУХ Харків" }
                          ]
                        }
                      ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(HttpClientFactory.sharedJson) }
        }
        val api = NextAlertExplosionsApi(client)

        val result = api.fetchDistrictTimeline("Харківський район", "Харківська область").getOrThrow()
        assertEquals("Харківський район", capturedName)
        assertEquals("Харківська область", capturedOblast)
        assertEquals(1, result.intervals.size)
        assertEquals("ВИБУХ Харків", result.intervals.first().blasts.first().text)
    }

    @Test
    fun parsesActiveRaionsBothArrayAndWrappedFormats() = runTest {
        val arrayEngine = MockEngine {
            respond(
                content = """[{"key":"x","name":"Харківський район","oblast":"Харківська область","status":"red","has_kab":true}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val arrayClient = HttpClient(arrayEngine) {
            install(ContentNegotiation) { json(HttpClientFactory.sharedJson) }
        }
        val arrayList = NextAlertExplosionsApi(arrayClient).fetchActiveRaions().getOrThrow()
        assertEquals(1, arrayList.size)
        assertTrue(arrayList.first().hasKab)

        val wrappedEngine = MockEngine {
            respond(
                content = """{"raions":[{"key":"y","name":"Запорізький район","oblast":"Запорізька область","status":"yellow"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val wrappedClient = HttpClient(wrappedEngine) {
            install(ContentNegotiation) { json(HttpClientFactory.sharedJson) }
        }
        val wrappedList = NextAlertExplosionsApi(wrappedClient).fetchActiveRaions().getOrThrow()
        assertEquals(1, wrappedList.size)
        assertEquals("Запорізький район", wrappedList.first().name)
    }

    @Test
    fun resolvesHromadaMatchFromBlastTextAndFallsBackToRaion() {
        val boundaries = testBoundaries()

        val kharkiv = ExplosionLocationResolver.resolveCoordinates(
            raionName = "Харківський район",
            oblastName = "Харківська область",
            blastText = "ВИБУХ Харків",
            boundaries = boundaries
        )
        assertNotNull(kharkiv)
        assertEquals(49.9935, kharkiv.latitude, 0.001)
        assertEquals("Харківська", kharkiv.localityName)

        val genericRaion = ExplosionLocationResolver.resolveCoordinates(
            raionName = "Харківський район",
            oblastName = "Харківська область",
            blastText = "Чути вибух у районі",
            boundaries = boundaries
        )
        assertNotNull(genericRaion)
        assertEquals(49.90, genericRaion.latitude, 0.001)
        assertEquals("Харківський район", genericRaion.localityName)
    }

    @Test
    fun repositoryFiltersOldBlastsAndFlagsRecent() = runTest {
        val boundaries = testBoundaries()
        val nowMs = Instant.parse("2026-11-16T15:00:00Z").toEpochMilliseconds()

        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/district-timeline")) {
                respond(
                    content = """
                        {
                          "name": "Харківський район",
                          "oblast": "Харківська область",
                          "intervals": [
                            {
                              "id": 1,
                              "is_active": true,
                              "blasts": [
                                { "date": "2026-11-16T14:50:00Z", "text": "ВИБУХ Харків" },
                                { "date": "2026-11-16T14:00:00Z", "text": "ВИБУХ у районі" },
                                { "date": "2026-11-16T11:00:00Z", "text": "Старий вибух" }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = "[]",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(HttpClientFactory.sharedJson) }
        }
        val repo = NextAlertExplosionsRepository(NextAlertExplosionsApi(client))

        val events = repo.fetchLiveExplosions(
            activeAlerts = listOf(
                ActiveAlertInfo(
                    regionId = "100",
                    regionName = "Харківський район",
                    type = AlertType.AIR,
                    oblastName = "Харківська область",
                    isOblastWide = false
                )
            ),
            trackedPoints = emptyList(),
            boundaries = boundaries,
            nowEpochMs = nowMs
        ).getOrThrow()

        assertEquals(2, events.size)
        assertTrue(events[0].isRecent) // 10 min ago
        assertFalse(events[1].isRecent) // 60 min ago
    }
}
