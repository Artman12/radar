package com.onemanarmy.radar.data.remote

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

class MapaUaRadarApiTest {

    private fun mockClient(
        jsonPayload: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ): HttpClient {
        val engine = MockEngine {
            respond(
                content = jsonPayload,
                status = status,
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
    fun parsesActiveObjectsOnlyWithMixedIdTypesAndLonLatTrails() = runTest {
        val payload = """
            {
              "attack": {
                "id": 2341,
                "status": "active"
              },
              "objects": [
                {
                  "id": 444756,
                  "kind": "drone_jet",
                  "subkind": "drone_jet",
                  "amount": 1,
                  "title": "Реактивний БпЛА на заході",
                  "status": "lost",
                  "heading": 276,
                  "lat": 48.52155,
                  "lon": 32.15919,
                  "speed_kmh": 450
                },
                {
                  "id": "445858_u0",
                  "kind": "drone_jet",
                  "subkind": "banderol",
                  "amount": 1,
                  "title": "По береговій смузі на Нововоронцовку",
                  "status": "active",
                  "heading": 304,
                  "lat": 47.72177,
                  "lon": 33.63889,
                  "speed_kmh": 450,
                  "last_seen": 1794851102,
                  "trail": [
                    [33.95048, 46.93807, 1794850622],
                    [33.63889, 47.72177, 1794851162]
                  ]
                },
                {
                  "id": 446001,
                  "kind": "drone_piston",
                  "subkind": "decoy",
                  "amount": 2,
                  "title": "Shahed на Київ",
                  "status": "active",
                  "heading": 340,
                  "lat": 50.12,
                  "lon": 30.85,
                  "speed_kmh": 165
                },
                {
                  "id": 446002,
                  "kind": "missile_cruise",
                  "subkind": "x59",
                  "status": "active",
                  "lat": 48.90,
                  "lon": 34.10
                },
                {
                  "id": 446003,
                  "kind": "missile_ballistic",
                  "subkind": "ballistic",
                  "status": "active",
                  "lat": 49.50,
                  "lon": 35.50
                },
                {
                  "id": 446004,
                  "kind": "bomb",
                  "subkind": "future_unknown_subkind",
                  "status": "active",
                  "lat": 50.20,
                  "lon": 37.11
                },
                {
                  "id": 446005,
                  "kind": "drone_fpv",
                  "subkind": "drone_fpv",
                  "status": "eliminated",
                  "lat": 47.50,
                  "lon": 35.10
                }
              ],
              "ts": 1794851200
            }
        """.trimIndent()

        val api = MapaUaRadarApi(mockClient(payload), nowEpochSeconds = { 1794851200L })
        val threats = api.fetchThreats().getOrThrow()

        assertEquals(5, threats.size)

        val jet = threats[0]
        assertEquals("mapa_445858_u0", jet.id)
        assertEquals(ThreatType.JET_SHAHED, jet.type)
        assertEquals(47.72177, jet.latitude)
        assertEquals(33.63889, jet.longitude)
        assertEquals(304.0, jet.headingDeg)
        assertEquals(450.0, jet.speedKmh)
        assertEquals("2026-11-16T17:46:02Z", jet.updatedAtIso)
        assertEquals(2, jet.trail.size)
        // Ensure [lon, lat] was flipped into ThreatTrailPoint(latitude, longitude)
        assertEquals(46.93807, jet.trail[0].latitude)
        assertEquals(33.95048, jet.trail[0].longitude)

        val piston = threats[1]
        assertEquals("mapa_446001", piston.id)
        assertEquals(ThreatType.UAV, piston.type)
        assertEquals(2, piston.count)
        assertEquals("2026-11-16T17:46:40Z", piston.updatedAtIso)

        assertEquals(ThreatType.CRUISE_MISSILE, threats[2].type)
        assertEquals(ThreatType.BALLISTIC, threats[3].type)
        assertEquals(ThreatType.KAB, threats[4].type)
    }

    @Test
    fun filtersOutStaleActiveObjectsAndKeepsRecentOnes() = runTest {
        val now = 1794860715L
        val payload = """
            {
              "attack": { "id": 2341, "status": "active" },
              "ts": $now,
              "objects": [
                {
                  "id": "fresh_uav",
                  "kind": "drone_piston",
                  "status": "active",
                  "lat": 50.1,
                  "lon": 30.5,
                  "last_seen": ${now - 8 * 60}
                },
                {
                  "id": "stale_uav",
                  "kind": "drone_piston",
                  "status": "active",
                  "lat": 50.2,
                  "lon": 30.6,
                  "last_seen": ${now - 45 * 60}
                },
                {
                  "id": "uav_fresh_trail",
                  "kind": "drone_piston",
                  "status": "active",
                  "lat": 50.3,
                  "lon": 30.7,
                  "last_seen": ${now - 25 * 60},
                  "trail": [[30.7, 50.3, ${now - 5 * 60}]]
                },
                {
                  "id": "stale_ballistic",
                  "kind": "missile_ballistic",
                  "status": "active",
                  "lat": 49.5,
                  "lon": 35.5,
                  "last_seen": ${now - 12 * 60}
                },
                {
                  "id": "fresh_ballistic",
                  "kind": "missile_ballistic",
                  "status": "active",
                  "lat": 49.6,
                  "lon": 35.6,
                  "last_seen": ${now - 3 * 60}
                }
              ]
            }
        """.trimIndent()

        val api = MapaUaRadarApi(mockClient(payload), nowEpochSeconds = { now })
        val threats = api.fetchThreats().getOrThrow()

        assertEquals(
            listOf("mapa_fresh_uav", "mapa_uav_fresh_trail", "mapa_fresh_ballistic"),
            threats.map { it.id }
        )
    }

    @Test
    fun handlesNullAttackAndEmptyObjectsGracefully() = runTest {
        val payload = """{"attack": null, "objects": [], "raw_count": 0, "ts": 1794851200}"""
        val api = MapaUaRadarApi(mockClient(payload), nowEpochSeconds = { 1794851200L })
        val threats = api.fetchThreats().getOrThrow()
        assertTrue(threats.isEmpty())
    }

    @Test
    fun returnsFailureOnNonSuccessStatus() = runTest {
        val api = MapaUaRadarApi(
            mockClient("""{"error":"rate limited"}""", HttpStatusCode.TooManyRequests),
            nowEpochSeconds = { 1794851200L }
        )
        val result = api.fetchThreats()
        assertTrue(result.isFailure)
    }
}
