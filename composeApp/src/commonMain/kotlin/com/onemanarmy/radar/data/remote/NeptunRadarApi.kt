package com.onemanarmy.radar.data.remote

import com.onemanarmy.radar.domain.logic.DurationFormatter
import com.onemanarmy.radar.domain.model.AirThreat
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
data class NeptunMotionDto(
    val speedKmh: Double? = null,
    val courseDeg: Double? = null
)

@Serializable
data class NeptunThreatDto(
    val id: String = "",
    val type: String? = null,
    val subtype: String? = null,
    val title: String? = null,
    val region: String? = null,
    val district: String? = null,
    val locality: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val heading: Double? = null,
    val velocity: NeptunVelocityDto? = null,
    val motion: NeptunMotionDto? = null,
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
 * Client for NEPTUN OSINT aerial threat feed.
 * Prefers `/api/v1/live` (which preserves target `subtype` such as `jet` for reactive Shaheds
 * and `motion` telemetry) and transparently falls back to `/api/v1/threats` if unavailable.
 * Strictly isolated from official alert polling; failures never crash or block alert monitoring.
 */
class NeptunRadarApi(
    private val client: HttpClient
) {
    companion object {
        const val NEPTUN_BASE_URL = "https://neptun.in.ua"
        const val LIVE_ENDPOINT = "$NEPTUN_BASE_URL/api/v1/live"
        const val THREATS_ENDPOINT = "$NEPTUN_BASE_URL/api/v1/threats"
    }

    suspend fun fetchThreats(): Result<List<AirThreat>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = fetchLiveOrFallback()

            response.threats.mapNotNull { dto ->
                val lat = dto.lat ?: return@mapNotNull null
                val lon = dto.lon ?: return@mapNotNull null
                if (dto.id.isBlank()) return@mapNotNull null
                val resolvedHeading = dto.velocity?.bearingDeg ?: dto.motion?.courseDeg ?: dto.heading
                val resolvedSpeed = dto.velocity?.speedKmh ?: dto.motion?.speedKmh

                AirThreat(
                    id = dto.id,
                    type = ThreatType.resolveFromApi(
                        rawType = dto.type,
                        subtype = dto.subtype,
                        title = dto.title,
                        explanation = dto.explanationShort,
                        speedKmh = resolvedSpeed
                    ),
                    title = dto.title.orEmpty(),
                    region = dto.region.orEmpty(),
                    locality = dto.locality.orEmpty(),
                    latitude = lat,
                    longitude = lon,
                    headingDeg = resolvedHeading,
                    speedKmh = resolvedSpeed,
                    count = (dto.count ?: 1).coerceAtLeast(1),
                    uncertaintyKm = dto.uncertaintyKm?.roundToInt(),
                    positionQuality = dto.positionQuality,
                    explanation = dto.explanationShort.orEmpty(),
                    updatedAtIso = resolveUpdatedAtIso(
                        updatedAt = dto.updatedAt,
                        trail = dto.trail,
                        serverTime = response.serverTime
                    ),
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

    private fun resolveUpdatedAtIso(
        updatedAt: String?,
        trail: List<NeptunTrailPointDto>,
        serverTime: String?
    ): String {
        val direct = updatedAt?.trim()?.takeIf { it.isNotEmpty() }
        if (direct != null && DurationFormatter.parseInstantOrNull(direct) != null) {
            return direct
        }
        val latestTrailInstant = trail
            .mapNotNull { tp -> tp.t?.let(DurationFormatter::parseInstantOrNull) }
            .maxOrNull()
        if (latestTrailInstant != null) {
            return latestTrailInstant.toString()
        }
        return direct
            ?: trail.lastOrNull { !it.t.isNullOrBlank() }?.t?.trim()
            ?: serverTime?.trim()?.takeIf { it.isNotEmpty() }
            ?: ""
    }

    private suspend fun fetchLiveOrFallback(): NeptunThreatsResponseDto {
        val liveResult = runCatching {
            val response = client.get(LIVE_ENDPOINT) {
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.Origin, NEPTUN_BASE_URL)
                header(HttpHeaders.Referrer, "$NEPTUN_BASE_URL/")
            }
            check(response.status.isSuccess()) {
                "NEPTUN live endpoint returned ${response.status}"
            }
            response.body<NeptunThreatsResponseDto>()
        }
        if (liveResult.isSuccess) {
            return liveResult.getOrThrow()
        }

        val fallbackResponse = client.get(THREATS_ENDPOINT) {
            header(HttpHeaders.Accept, "application/json")
        }
        check(fallbackResponse.status.isSuccess()) {
            "NEPTUN threats endpoint returned ${fallbackResponse.status}"
        }
        return fallbackResponse.body()
    }
}
