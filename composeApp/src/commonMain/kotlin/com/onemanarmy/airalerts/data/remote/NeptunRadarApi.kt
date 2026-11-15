package com.onemanarmy.airalerts.data.remote

import com.onemanarmy.airalerts.domain.model.AirThreat
import com.onemanarmy.airalerts.domain.model.ThreatTrailPoint
import com.onemanarmy.airalerts.domain.model.ThreatType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class NeptunVelocityDto(
    val bearingDeg: Double? = null,
    val speedKmh: Double? = null
)

@Serializable
data class NeptunTrailPointDto(
    val lat: Double? = null,
    val lon: Double? = null,
    val t: String? = null
)

@Serializable
data class NeptunThreatDto(
    val id: String = "",
    val type: String? = null,
    val title: String? = null,
    val region: String? = null,
    val district: String? = null,
    val locality: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val heading: Double? = null,
    val velocity: NeptunVelocityDto? = null,
    val count: Int? = null,
    val uncertaintyKm: Double? = null,
    val positionQuality: String? = null,
    val explanationShort: String? = null,
    val updatedAt: String? = null,
    val status: String? = null,
    val trail: List<NeptunTrailPointDto> = emptyList(),
    val presumptiveCourse: Boolean = false
)

@Serializable
data class NeptunThreatsResponseDto(
    val epoch: String? = null,
    val seq: Long? = null,
    @SerialName("serverTime") val serverTime: String? = null,
    val threats: List<NeptunThreatDto> = emptyList()
)

/**
 * Client for NEPTUN OSINT aerial threat feed (neptun.in.ua/api/v1/threats).
 * Strictly isolated from official alert polling; failures never crash or block alert monitoring.
 */
class NeptunRadarApi(
    private val client: HttpClient
) {
    companion object {
        const val NEPTUN_BASE_URL = "https://neptun.in.ua"
        private const val THREATS_ENDPOINT = "$NEPTUN_BASE_URL/api/v1/threats"
    }

    suspend fun fetchThreats(): Result<List<AirThreat>> = withContext(Dispatchers.IO) {
        runCatching {
            val response: NeptunThreatsResponseDto = client.get(THREATS_ENDPOINT) {
                header(HttpHeaders.Accept, "application/json")
            }.body()

            response.threats.mapNotNull { dto ->
                val lat = dto.lat ?: return@mapNotNull null
                val lon = dto.lon ?: return@mapNotNull null
                if (dto.id.isBlank()) return@mapNotNull null
                val resolvedHeading = dto.velocity?.bearingDeg ?: dto.heading

                AirThreat(
                    id = dto.id,
                    type = ThreatType.fromApiValue(dto.type),
                    title = dto.title.orEmpty(),
                    region = dto.region.orEmpty(),
                    locality = dto.locality.orEmpty(),
                    latitude = lat,
                    longitude = lon,
                    headingDeg = resolvedHeading,
                    speedKmh = dto.velocity?.speedKmh,
                    count = (dto.count ?: 1).coerceAtLeast(1),
                    uncertaintyKm = dto.uncertaintyKm?.roundToInt(),
                    positionQuality = dto.positionQuality,
                    explanation = dto.explanationShort.orEmpty(),
                    updatedAtIso = dto.updatedAt.orEmpty(),
                    trail = dto.trail.mapNotNull { tp ->
                        val tLat = tp.lat ?: return@mapNotNull null
                        val tLon = tp.lon ?: return@mapNotNull null
                        ThreatTrailPoint(tLat, tLon, tp.t)
                    },
                    presumptiveCourse = dto.presumptiveCourse
                )
            }
        }
    }
}
