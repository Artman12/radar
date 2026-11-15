package com.onemanarmy.airalerts.data.remote

import com.onemanarmy.airalerts.domain.model.AlertLevel
import com.onemanarmy.airalerts.domain.model.ThreatType
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
}
