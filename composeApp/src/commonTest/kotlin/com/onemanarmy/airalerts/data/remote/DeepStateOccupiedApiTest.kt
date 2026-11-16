package com.onemanarmy.airalerts.data.remote

import com.onemanarmy.airalerts.domain.model.OccupiedTerritoriesSnapshot
import com.onemanarmy.airalerts.domain.model.OccupiedZoneCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepStateOccupiedApiTest {

    private fun mockClient(jsonPayload: String): HttpClient {
        val engine = MockEngine {
            respond(
                content = jsonPayload,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return HttpClient(engine)
    }

    @Test
    fun parsesOccupiedUkrainianTerritoriesAndExcludesLiberatedUnknownAndForeignZones() = runTest {
        val payload = """
            {
              "id": 1794773750,
              "datetime": "15.11 o 21:15",
              "map": {
                "type": "FeatureCollection",
                "features": [
                  {
                    "type": "Feature",
                    "geometry": {
                      "type": "Polygon",
                      "coordinates": [[[34.0, 44.9, 0], [34.5, 44.9, 0], [34.5, 45.4, 0], [34.0, 44.9, 0]]]
                    },
                    "properties": {
                      "name": "Окупований Крим /// Occupied Crimea\u00A0/// geoJSON.territories.crimea",
                      "fill": "#880e4f"
                    }
                  },
                  {
                    "type": "Feature",
                    "geometry": {
                      "type": "MultiPolygon",
                      "coordinates": [
                        [[[37.8, 48.0, 0], [38.2, 48.0, 0], [38.2, 48.3, 0], [37.8, 48.0, 0]]],
                        [[[35.2, 46.8, 0], [35.6, 46.8, 0], [35.6, 47.1, 0], [35.2, 46.8, 0]]]
                      ]
                    },
                    "properties": {
                      "name": "Окуповано /// Occupied /// geoJSON.status.occupied\n",
                      "fill": "#a52714"
                    }
                  },
                  {
                    "type": "Feature",
                    "geometry": {
                      "type": "Polygon",
                      "coordinates": [[[31.0, 50.5, 0], [31.2, 50.5, 0], [31.2, 50.7, 0], [31.0, 50.5, 0]]]
                    },
                    "properties": {
                      "name": "Звільнено /// Liberated /// geoJSON.status.dismissed",
                      "fill": "#0f9d58"
                    }
                  },
                  {
                    "type": "Feature",
                    "geometry": {
                      "type": "Polygon",
                      "coordinates": [[[37.0, 48.5, 0], [37.2, 48.5, 0], [37.2, 48.7, 0], [37.0, 48.5, 0]]]
                    },
                    "properties": {
                      "name": "Статус невідомий /// Unknown status /// geoJSON.status.unknown",
                      "fill": "#bcaaa4"
                    }
                  },
                  {
                    "type": "Feature",
                    "geometry": {
                      "type": "Polygon",
                      "coordinates": [[[29.2, 47.0, 0], [29.6, 47.0, 0], [29.6, 47.3, 0], [29.2, 47.0, 0]]]
                    },
                    "properties": {
                      "name": "Придністров'я /// Transnistria /// geoJSON.territories.transnistria",
                      "fill": "#ff5252"
                    }
                  },
                  {
                    "type": "Feature",
                    "geometry": {
                      "type": "Polygon",
                      "coordinates": [[[146.0, 44.0, 0], [147.0, 44.0, 0], [147.0, 45.0, 0], [146.0, 44.0, 0]]]
                    },
                    "properties": {
                      "name": "Окуповано /// Occupied /// geoJSON.status.occupied",
                      "fill": "#a52714"
                    }
                  },
                  {
                    "type": "Feature",
                    "geometry": {
                      "type": "Point",
                      "coordinates": [38.0, 48.0, 0]
                    },
                    "properties": {
                      "name": "geoJSON.status.attack_direction"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val api = DeepStateOccupiedApi(mockClient(payload))
        val snapshot = api.fetchLatestOccupied().getOrThrow()

        assertEquals(1794773750L, snapshot.sourceHistoryId)
        assertEquals("15.11 o 21:15", snapshot.sourceDatetime)
        // 1 Crimea Polygon + 2 polygons from MultiPolygon = 3
        assertEquals(3, snapshot.polygons.size)

        val crimea = snapshot.polygons[0]
        assertEquals(OccupiedZoneCategory.OCCUPIED_2014, crimea.category)
        assertEquals(44.9f, crimea.minLat, 0.001f)
        assertEquals(45.4f, crimea.maxLat, 0.001f)
        assertEquals(34.0f, crimea.minLon, 0.001f)
        assertEquals(34.5f, crimea.maxLon, 0.001f)
        // Packed as [lat0, lon0, lat1, lon1, ...]
        assertEquals(44.9f, crimea.rings[0][0], 0.001f)
        assertEquals(34.0f, crimea.rings[0][1], 0.001f)

        assertTrue(snapshot.polygons.drop(1).all { it.category == OccupiedZoneCategory.OCCUPIED_2022 })
    }

    @Test
    fun snapshotSerializesAndDeserializesCleanlyForOfflineCache() {
        val original = OccupiedTerritoriesSnapshot(
            sourceHistoryId = 42L,
            sourceDatetime = "16.11 o 12:00",
            fetchedAtEpochMs = 1_700_000_000_000L,
            polygons = listOf(
                com.onemanarmy.airalerts.domain.model.OccupiedTerritoryPolygon(
                    category = OccupiedZoneCategory.OCCUPIED_2022,
                    minLat = 47.0f,
                    maxLat = 48.0f,
                    minLon = 35.0f,
                    maxLon = 36.0f,
                    rings = listOf(floatArrayOf(47.0f, 35.0f, 47.0f, 36.0f, 48.0f, 36.0f))
                )
            )
        )

        val encoded = HttpClientFactory.sharedJson.encodeToString(original)
        val decoded = HttpClientFactory.sharedJson.decodeFromString<OccupiedTerritoriesSnapshot>(encoded)

        assertEquals(original.sourceHistoryId, decoded.sourceHistoryId)
        assertEquals(original.sourceDatetime, decoded.sourceDatetime)
        assertEquals(original.fetchedAtEpochMs, decoded.fetchedAtEpochMs)
        assertEquals(1, decoded.polygons.size)
        assertEquals(OccupiedZoneCategory.OCCUPIED_2022, decoded.polygons[0].category)
        assertEquals(6, decoded.polygons[0].rings[0].size)
    }
}
