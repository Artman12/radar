package com.onemanarmy.radar.data.remote

import com.onemanarmy.radar.domain.model.AlertLevel
import com.onemanarmy.radar.domain.model.ThreatType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NeptunApisTest {

    private fun mockClient(jsonPayload: String): HttpClient {
        val engine = MockEngine {
            respond(
                content = jsonPayload,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(HttpClientFactory.sharedJson)
            }
        }
    }

    @Test
    fun parsesNeptunAlertsSnapshotWithOblastsAndRaions() = runTest {
        val payload = """
            {
              "version": 1794775060,
              "updatedAt": "2026-11-15T20:37:40.434130786Z",
              "raions": [
                {
                  "key": "білоцерківський",
                  "name": "Білоцерківський район",
                  "oblast": "Київська область",
                  "since": "2026-11-15T20:33:53.40262Z",
                  "level": "yellow"
                }
              ],
              "oblasts": [
                {
                  "key": "луганська",
                  "name": "Луганська область",
                  "oblast": "Луганська область",
                  "since": "2026-11-02T02:06:27.934655Z",
                  "level": "red"
                }
              ]
            }
        """.trimIndent()

        val api = NeptunAlertsApi(mockClient(payload))
        val snapshot = api.fetchAlertsSnapshot().getOrThrow()

        assertEquals(1794775060L, snapshot.version)
        assertEquals(2, snapshot.alerts.size)

        val luhansk = snapshot.alerts[0]
        assertEquals("16", luhansk.regionId)
        assertTrue(luhansk.isOblastWide)
        assertEquals(AlertLevel.RED, luhansk.level)

        val bilaTserkva = snapshot.alerts[1]
        assertEquals("Білоцерківський район", bilaTserkva.regionName)
        assertEquals("Київська область", bilaTserkva.oblastName)
        assertEquals(AlertLevel.YELLOW, bilaTserkva.level)
    }

    @Test
    fun parsesNeptunThreatsWithFractionalUncertaintyAndFpv() = runTest {
        val payload = """
            {
              "epoch": "rt-203547fe",
              "seq": 93471,
              "threats": [
                {
                  "id": "trk_00364223",
                  "type": "fpv",
                  "title": "FPV-дрон",
                  "region": "Херсонська область",
                  "locality": "Корабел",
                  "lat": 46.6216,
                  "lon": 32.5742,
                  "heading": null,
                  "count": 2,
                  "uncertaintyKm": 3.9,
                  "trail": [
                    {"lat": 46.6354, "lon": 32.6169, "t": "2026-11-15T20:30:51Z"}
                  ]
                }
              ]
            }
        """.trimIndent()

        val api = NeptunRadarApi(mockClient(payload))
        val threats = api.fetchThreats().getOrThrow()

        assertEquals(1, threats.size)
        val item = threats.first()
        assertEquals(ThreatType.FPV, item.type)
        assertEquals(4, item.uncertaintyKm)
        assertEquals(2, item.count)
        assertEquals(1, item.trail.size)
    }

    @Test
    fun distinguishesPropellerShahedAndJetShahedAndMig31k() = runTest {
        val payload = """
            {
              "epoch": "rt-203547fe",
              "seq": 95800,
              "threats": [
                {
                  "id": "trk_uav_prop",
                  "type": "uav",
                  "title": "БпЛА",
                  "lat": 49.99,
                  "lon": 36.23,
                  "velocity": {"bearingDeg": 270.0, "speedKmh": 165.0}
                },
                {
                  "id": "trk_uav_jet_title",
                  "type": "uav",
                  "title": "Реактивний шахед",
                  "lat": 48.50,
                  "lon": 35.10,
                  "velocity": {"bearingDeg": 250.0, "speedKmh": 520.0}
                },
                {
                  "id": "trk_uav_jet_type",
                  "type": "jet_shahed",
                  "title": "Shahed-238",
                  "lat": 47.90,
                  "lon": 34.80
                },
                {
                  "id": "trk_mig",
                  "type": "mig31k",
                  "title": "МіГ-31К",
                  "lat": 51.00,
                  "lon": 33.00
                }
              ]
            }
        """.trimIndent()

        val api = NeptunRadarApi(mockClient(payload))
        val threats = api.fetchThreats().getOrThrow()

        assertEquals(4, threats.size)
        assertEquals(ThreatType.UAV, threats[0].type)
        assertEquals(ThreatType.JET_SHAHED, threats[1].type)
        assertEquals(ThreatType.JET_SHAHED, threats[2].type)
        assertEquals(ThreatType.AVIATION, threats[3].type)
    }

    @Test
    fun parsesJetSubtypeFromLiveEndpointAndFallsBackToThreatsOn403() = runTest {
        val livePayload = """
            {
              "epoch": "rt-live",
              "seq": 100,
              "threats": [
                {
                  "id": "trk_00364655",
                  "type": "uav",
                  "subtype": "jet",
                  "title": "БпЛА",
                  "lat": 49.12439388,
                  "lon": 36.45618559,
                  "motion": {"speedKmh": 330.0, "courseDeg": 278.1}
                },
                {
                  "id": "trk_00364622",
                  "type": "uav",
                  "title": "БпЛА",
                  "lat": 50.05090252,
                  "lon": 36.17651712,
                  "motion": {"speedKmh": 150.0, "courseDeg": 219.0}
                }
              ]
            }
        """.trimIndent()

        val liveApi = NeptunRadarApi(mockClient(livePayload))
        val liveThreats = liveApi.fetchThreats().getOrThrow()
        assertEquals(2, liveThreats.size)
        assertEquals(ThreatType.JET_SHAHED, liveThreats[0].type)
        assertEquals(330.0, liveThreats[0].speedKmh)
        assertEquals(278.1, liveThreats[0].headingDeg)
        assertEquals(ThreatType.UAV, liveThreats[1].type)

        // Verify 403 on /api/v1/live falls back cleanly to /api/v1/threats
        val fallbackEngine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/api/v1/live")) {
                respond(
                    content = """{"error":"Forbidden"}""",
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """
                        {
                          "epoch": "rt-fallback",
                          "seq": 101,
                          "threats": [
                            {
                              "id": "trk_fallback",
                              "type": "uav",
                              "title": "БпЛА",
                              "lat": 50.0,
                              "lon": 36.0
                            }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val fallbackClient = HttpClient(fallbackEngine) {
            install(ContentNegotiation) {
                json(HttpClientFactory.sharedJson)
            }
        }
        val fallbackThreats = NeptunRadarApi(fallbackClient).fetchThreats().getOrThrow()
        assertEquals(1, fallbackThreats.size)
        assertEquals("trk_fallback", fallbackThreats.first().id)
        assertEquals(ThreatType.UAV, fallbackThreats.first().type)
    }
}
