package com.onemanarmy.radar.data.remote

import com.onemanarmy.radar.domain.model.AirThreat
import com.onemanarmy.radar.domain.model.ThreatDataSource
import com.onemanarmy.radar.domain.model.ThreatTrailPoint
import com.onemanarmy.radar.domain.model.ThreatType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

@Serializable
data class MapaUaAttackDto(
    val id: Long? = null,
    val status: String? = null,
    val title: String? = null
)

@Serializable
data class MapaUaObjectDto(
    val id: JsonPrimitive? = null,
    val kind: String? = null,
    val subkind: String? = null,
    val amount: Int? = null,
    val title: String? = null,
    @SerialName("group_title") val groupTitle: String? = null,
    val status: String? = null,
    val heading: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("predicted_lat") val predictedLat: Double? = null,
    @SerialName("predicted_lon") val predictedLon: Double? = null,
    @SerialName("speed_kmh") val speedKmh: Double? = null,
    @SerialName("from_zone") val fromZone: String? = null,
    @SerialName("to_city") val toCity: String? = null,
    @SerialName("first_seen") val firstSeen: Long? = null,
    @SerialName("last_seen") val lastSeen: Long? = null,
    val trail: List<List<JsonPrimitive>> = emptyList()
)

@Serializable
data class MapaUaCurrentResponseDto(
    val attack: MapaUaAttackDto? = null,
    val objects: List<MapaUaObjectDto> = emptyList(),
    @SerialName("raw_count") val rawCount: Int? = null,
    val ts: Long? = null
)

/**
 * Client for MAPA.UA OSINT aerial threat feed (`GET https://mapa.ua/api/v1/current`).
 * Filters the current attack snapshot down to recent objects with `status == "active"` (excluding
 * stale targets whose `last_seen` / latest trail timestamp exceeds the target-type recency window)
 * and converts `[lon, lat, unix_ts]` trail arrays into [AirThreat] domain models.
 */
class MapaUaRadarApi(
    private val client: HttpClient,
    private val nowEpochSeconds: () -> Long = { Clock.System.now().epochSeconds }
) {
    companion object {
        const val MAPA_UA_BASE_URL = "https://mapa.ua"
        const val CURRENT_ENDPOINT = "$MAPA_UA_BASE_URL/api/v1/current"

        const val MAX_AGE_FAST_THREAT_SECONDS = 10 * 60L
        const val MAX_AGE_MEDIUM_THREAT_SECONDS = 12 * 60L
        const val MAX_AGE_SLOW_THREAT_SECONDS = 15 * 60L

        internal fun maxActiveAgeSeconds(type: ThreatType): Long = when (type) {
            ThreatType.BALLISTIC,
            ThreatType.KAB -> MAX_AGE_FAST_THREAT_SECONDS
            ThreatType.CRUISE_MISSILE,
            ThreatType.JET_SHAHED,
            ThreatType.AVIATION -> MAX_AGE_MEDIUM_THREAT_SECONDS
            ThreatType.UAV,
            ThreatType.FPV,
            ThreatType.RECON,
            ThreatType.UNKNOWN -> MAX_AGE_SLOW_THREAT_SECONDS
        }
    }

    suspend fun fetchThreats(): Result<List<AirThreat>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.get(CURRENT_ENDPOINT) {
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.Origin, MAPA_UA_BASE_URL)
                header(HttpHeaders.Referrer, "$MAPA_UA_BASE_URL/")
            }
            check(response.status.isSuccess()) {
                "MAPA.UA current endpoint returned ${response.status}"
            }
            val body = response.body<MapaUaCurrentResponseDto>()
            val referenceNowSec = maxOf(body.ts ?: 0L, nowEpochSeconds())

            body.objects.mapNotNull { dto ->
                if (!dto.status.equals("active", ignoreCase = true)) return@mapNotNull null
                val rawId = dto.id?.content?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val lat = dto.lat ?: dto.predictedLat ?: return@mapNotNull null
                val lon = dto.lon ?: dto.predictedLon ?: return@mapNotNull null
                val resolvedTitle = (dto.title ?: dto.groupTitle).orEmpty()
                val resolvedType = ThreatType.resolveFromApi(
                    rawType = dto.kind,
                    subtype = dto.subkind,
                    title = resolvedTitle,
                    speedKmh = dto.speedKmh
                )
                val latestTrailSec = dto.trail.mapNotNull { it.getOrNull(2)?.longOrNull }.maxOrNull()
                val effectiveLastSeenSec = listOfNotNull(dto.lastSeen, latestTrailSec, dto.firstSeen).maxOrNull()
                if (effectiveLastSeenSec != null) {
                    val ageSec = (referenceNowSec - effectiveLastSeenSec).coerceAtLeast(0L)
                    if (ageSec > maxActiveAgeSeconds(resolvedType)) {
                        return@mapNotNull null
                    }
                }
                val updatedAtIso = (effectiveLastSeenSec ?: body.ts)?.let { sec ->
                    runCatching { Instant.fromEpochSeconds(sec).toString() }.getOrNull()
                }.orEmpty()

                AirThreat(
                    id = "mapa_$rawId",
                    type = resolvedType,
                    title = resolvedTitle,
                    region = "",
                    locality = "",
                    latitude = lat,
                    longitude = lon,
                    headingDeg = dto.heading,
                    speedKmh = dto.speedKmh,
                    count = (dto.amount ?: 1).coerceAtLeast(1),
                    uncertaintyKm = null,
                    positionQuality = null,
                    explanation = "",
                    updatedAtIso = updatedAtIso,
                    trail = dto.trail.mapNotNull { point ->
                        val tLon = point.getOrNull(0)?.doubleOrNull ?: return@mapNotNull null
                        val tLat = point.getOrNull(1)?.doubleOrNull ?: return@mapNotNull null
                        val tIso = point.getOrNull(2)?.longOrNull?.let { sec ->
                            runCatching { Instant.fromEpochSeconds(sec).toString() }.getOrNull()
                        }
                        ThreatTrailPoint(
                            latitude = tLat,
                            longitude = tLon,
                            timestampIso = tIso
                        )
                    },
                    presumptiveCourse = false,
                    source = ThreatDataSource.MAPA_UA
                )
            }
        }
    }
}
