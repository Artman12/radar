package com.onemanarmy.radar.data.remote

import com.onemanarmy.radar.domain.logic.RegionMatcher
import com.onemanarmy.radar.domain.model.ActiveAlertInfo
import com.onemanarmy.radar.domain.model.AlertLevel
import com.onemanarmy.radar.domain.model.AlertType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class NeptunAlertAreaDto(
    val key: String = "",
    val name: String = "",
    val oblast: String = "",
    val since: String? = null,
    val level: String? = null
)

@Serializable
data class NeptunAlertsResponseDto(
    val version: Long = 0L,
    val updatedAt: String? = null,
    val raions: List<NeptunAlertAreaDto> = emptyList(),
    val oblasts: List<NeptunAlertAreaDto> = emptyList()
)

data class NeptunAlertsSnapshot(
    val version: Long,
    val updatedAt: String?,
    val alerts: List<ActiveAlertInfo>
)

/**
 * Client for NEPTUN keyless civil-defense alerts feed (https://neptun.in.ua/api/v1/alerts).
 */
class NeptunAlertsApi(
    private val client: HttpClient
) {
    companion object {
        private const val ALERTS_ENDPOINT = "${NeptunRadarApi.NEPTUN_BASE_URL}/api/v1/alerts"
    }

    suspend fun fetchAlertsSnapshot(): Result<NeptunAlertsSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val dto: NeptunAlertsResponseDto = client.get(ALERTS_ENDPOINT) {
                header(HttpHeaders.Accept, "application/json")
            }.body()

            val mappedOblasts = dto.oblasts.map { area ->
                val oblastMatch = RegionMatcher.findOblastByName(area.name)
                    ?: RegionMatcher.findOblastByName(area.oblast)
                    ?: RegionMatcher.findOblastByName(area.key)
                ActiveAlertInfo(
                    regionId = oblastMatch?.regionId ?: "neptun_oblast:${area.key.ifBlank { area.name }}",
                    regionName = area.name.ifBlank { area.oblast },
                    regionEngName = oblastMatch?.engName.orEmpty(),
                    type = AlertType.AIR,
                    level = AlertLevel.fromApiValue(area.level),
                    startedAtIso = area.since,
                    oblastName = oblastMatch?.ukName ?: area.oblast.ifBlank { area.name },
                    isOblastWide = true
                )
            }

            val mappedRaions = dto.raions.map { area ->
                val oblastMatch = RegionMatcher.findOblastByName(area.oblast)
                val key = area.key.ifBlank { area.name.lowercase() }
                ActiveAlertInfo(
                    regionId = "neptun_raion:$key",
                    regionName = area.name.ifBlank { area.key },
                    regionEngName = oblastMatch?.engName.orEmpty(),
                    type = AlertType.AIR,
                    level = AlertLevel.fromApiValue(area.level),
                    startedAtIso = area.since,
                    oblastName = oblastMatch?.ukName ?: area.oblast,
                    isOblastWide = false
                )
            }

            NeptunAlertsSnapshot(
                version = dto.version,
                updatedAt = dto.updatedAt,
                alerts = mappedOblasts + mappedRaions
            )
        }
    }
}
