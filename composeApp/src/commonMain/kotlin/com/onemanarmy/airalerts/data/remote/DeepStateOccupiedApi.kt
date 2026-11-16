package com.onemanarmy.airalerts.data.remote

import com.onemanarmy.airalerts.domain.model.OccupiedTerritoriesSnapshot
import com.onemanarmy.airalerts.domain.model.OccupiedTerritoryPolygon
import com.onemanarmy.airalerts.domain.model.OccupiedZoneCategory
import com.onemanarmy.airalerts.domain.model.UkraineBounds
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.max
import kotlin.math.min

/**
 * Client for DeepStateMap's public latest frontline GeoJSON endpoint (`deepstatemap.live/api/history/last`).
 * Extracts only currently occupied Ukrainian territory polygons and ignores liberated, contested,
 * point-unit, or foreign-territory features.
 */
class DeepStateOccupiedApi(
    private val client: HttpClient,
    private val json: Json = HttpClientFactory.sharedJson
) {
    companion object {
        const val DEEPSTATE_BASE_URL = "https://deepstatemap.live"
        const val LATEST_ENDPOINT = "$DEEPSTATE_BASE_URL/api/history/last"

        private val TAGS_2014 = listOf(
            "geojson.territories.crimea",
            "geojson.territories.ordlo",
            "geojson.territories.tuzla"
        )
        private const val TAG_OCCUPIED_2022 = "geojson.status.occupied"
    }

    suspend fun fetchLatestOccupied(): Result<OccupiedTerritoriesSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.get(LATEST_ENDPOINT) {
                header(HttpHeaders.Accept, "application/json")
            }
            check(response.status.isSuccess()) {
                "DeepStateMap endpoint returned ${response.status}"
            }
            val rawJson = response.bodyAsText()
            parseGeoJsonSnapshot(rawJson, Clock.System.now().toEpochMilliseconds())
        }
    }

    internal fun parseGeoJsonSnapshot(
        rawJson: String,
        fetchedAtEpochMs: Long
    ): OccupiedTerritoriesSnapshot {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val historyId = root["id"]?.jsonPrimitive?.longOrNull ?: 0L
        val datetime = root["datetime"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val mapObj = root["map"]?.jsonObject ?: root
        val features = mapObj["features"]?.jsonArray ?: JsonArray(emptyList())

        val polygons = ArrayList<OccupiedTerritoryPolygon>(32)

        for (element in features) {
            val feature = element as? JsonObject ?: continue
            val props = feature["properties"] as? JsonObject ?: continue
            val category = classifyOccupiedCategory(props) ?: continue
            val geometry = feature["geometry"] as? JsonObject ?: continue
            val geomType = geometry["type"]?.jsonPrimitive?.contentOrNull ?: continue
            val coords = geometry["coordinates"] as? JsonArray ?: continue

            when (geomType) {
                "Polygon" -> {
                    extractPolygon(coords, category)?.let { polygons.add(it) }
                }
                "MultiPolygon" -> {
                    for (polyEl in coords) {
                        val polyRings = polyEl as? JsonArray ?: continue
                        extractPolygon(polyRings, category)?.let { polygons.add(it) }
                    }
                }
            }
        }

        return OccupiedTerritoriesSnapshot(
            sourceHistoryId = historyId,
            sourceDatetime = datetime,
            fetchedAtEpochMs = fetchedAtEpochMs,
            polygons = polygons
        )
    }

    private fun classifyOccupiedCategory(props: JsonObject): OccupiedZoneCategory? {
        val rawName = props["name"]?.jsonPrimitive?.contentOrNull
            ?.replace('\u00A0', ' ')
            ?.lowercase()
            .orEmpty()

        if (TAGS_2014.any { rawName.contains(it) }) {
            return OccupiedZoneCategory.OCCUPIED_2014
        }
        if (rawName.contains(TAG_OCCUPIED_2022)) {
            return OccupiedZoneCategory.OCCUPIED_2022
        }

        // Fallback if DeepState ever alters the i18n tag while keeping the occupied fill color.
        val fill = props["fill"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase().orEmpty()
        val isExcludedStatus = rawName.contains("dismissed") ||
            rawName.contains("unknown") ||
            rawName.contains("zmiinyi") ||
            rawName.contains("geojson.territories.")
        if (!isExcludedStatus && fill == "#a52714") {
            return OccupiedZoneCategory.OCCUPIED_2022
        }
        return null
    }

    private fun extractPolygon(
        ringsArray: JsonArray,
        category: OccupiedZoneCategory
    ): OccupiedTerritoryPolygon? {
        val rings = ArrayList<FloatArray>(ringsArray.size)
        var minLat = Float.MAX_VALUE
        var maxLat = -Float.MAX_VALUE
        var minLon = Float.MAX_VALUE
        var maxLon = -Float.MAX_VALUE

        for (ringEl in ringsArray) {
            val ringPoints = ringEl as? JsonArray ?: continue
            if (ringPoints.size < 3) continue
            val packed = FloatArray(ringPoints.size * 2)
            var outIdx = 0
            for (ptEl in ringPoints) {
                val pt = ptEl as? JsonArray ?: continue
                if (pt.size < 2) continue
                val lon = pt[0].jsonPrimitive.doubleOrNull?.toFloat() ?: continue
                val lat = pt[1].jsonPrimitive.doubleOrNull?.toFloat() ?: continue
                packed[outIdx++] = lat
                packed[outIdx++] = lon
                minLat = min(minLat, lat)
                maxLat = max(maxLat, lat)
                minLon = min(minLon, lon)
                maxLon = max(maxLon, lon)
            }
            if (outIdx >= 6) {
                rings.add(if (outIdx == packed.size) packed else packed.copyOf(outIdx))
            }
        }

        if (rings.isEmpty()) return null

        // Keep only polygons intersecting Ukraine's geographic bounds.
        val overlapsUkraine = maxLat >= UkraineBounds.MIN_LAT - 0.5 &&
            minLat <= UkraineBounds.MAX_LAT + 0.5 &&
            maxLon >= UkraineBounds.MIN_LON - 0.5 &&
            minLon <= UkraineBounds.MAX_LON + 0.5
        if (!overlapsUkraine) return null

        return OccupiedTerritoryPolygon(
            category = category,
            minLat = minLat,
            maxLat = maxLat,
            minLon = minLon,
            maxLon = maxLon,
            rings = rings
        )
    }
}
