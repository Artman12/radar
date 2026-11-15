package com.onemanarmy.airalerts.data.remote

import com.onemanarmy.airalerts.config.BuildKonfig
import com.onemanarmy.airalerts.domain.model.ActiveAlertInfo
import com.onemanarmy.airalerts.domain.model.AlertLevel
import com.onemanarmy.airalerts.domain.model.AlertType
import com.onemanarmy.airalerts.domain.model.RegionItem
import com.onemanarmy.airalerts.domain.model.RegionType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class AlertStatusDto(
    val lastActionIndex: Long = 0L
)

@Serializable
data class AlertLevelDto(
    val alertLevel: String? = null,
    val reason: String? = null,
    val createdAt: String? = null
)

@Serializable
data class ActiveAlertDto(
    val regionId: String? = null,
    val regionType: String? = null,
    val type: String? = null,
    val lastUpdate: String? = null,
    val activeAlertLevels: List<AlertLevelDto> = emptyList()
)

@Serializable
data class AlertRegionDto(
    val regionId: String = "",
    val regionType: String? = null,
    val regionName: String = "",
    val regionEngName: String? = null,
    val lastUpdate: String? = null,
    val activeAlerts: List<ActiveAlertDto> = emptyList()
)

@Serializable
data class RegionNodeDto(
    val regionId: String = "",
    val regionName: String = "",
    val regionEngName: String? = null,
    val regionType: String? = null,
    val regionChildIds: List<RegionNodeDto> = emptyList()
)

@Serializable
data class RegionsResponseDto(
    val states: List<RegionNodeDto> = emptyList()
)

/**
 * Client for UkraineAlarm API v3 (with automatic keyless mirror fallback when no local.properties key is configured).
 */
class UkraineAlarmApi(
    private val client: HttpClient,
    private val apiKey: String = BuildKonfig.UKRAINE_ALARM_API_KEY
) {
    companion object {
        private const val OFFICIAL_BASE_URL = "https://api.ukrainealarm.com"
        private const val FALLBACK_MIRROR_URL = "https://siren.pp.ua"
    }

    val isUsingOfficialKey: Boolean
        get() = apiKey.isNotBlank()

    suspend fun fetchAlertStatus(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val dto: AlertStatusDto = getWithFallback("/api/v3/alerts/status")
            dto.lastActionIndex
        }
    }

    suspend fun fetchActiveAlerts(): Result<List<ActiveAlertInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val regions: List<AlertRegionDto> = getWithFallback("/api/v3/alerts")
            regions.flatMap { region ->
                if (region.activeAlerts.isEmpty()) {
                    emptyList()
                } else {
                    region.activeAlerts.map { alert ->
                        val firstLevel = alert.activeAlertLevels.firstOrNull()
                        ActiveAlertInfo(
                            regionId = alert.regionId ?: region.regionId,
                            regionName = region.regionName,
                            regionEngName = region.regionEngName.orEmpty(),
                            type = AlertType.fromApiValue(alert.type),
                            level = AlertLevel.fromApiValue(firstLevel?.alertLevel),
                            startedAtIso = firstLevel?.createdAt ?: alert.lastUpdate ?: region.lastUpdate
                        )
                    }
                }
            }
        }
    }

    suspend fun fetchRegions(): Result<List<RegionItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response: RegionsResponseDto = getWithFallback("/api/v3/regions")
            val flattened = mutableListOf<RegionItem>()
            fun flatten(node: RegionNodeDto, parentId: String?) {
                if (node.regionId.isNotBlank()) {
                    flattened += RegionItem(
                        regionId = node.regionId,
                        name = node.regionName,
                        engName = node.regionEngName.orEmpty(),
                        type = RegionType.fromApiValue(node.regionType),
                        parentId = parentId
                    )
                }
                node.regionChildIds.forEach { child ->
                    flatten(child, node.regionId.takeIf { it.isNotBlank() })
                }
            }
            response.states.forEach { state -> flatten(state, null) }
            flattened
        }
    }

    private suspend inline fun <reified T> getWithFallback(path: String): T {
        if (apiKey.isNotBlank()) {
            val officialResponse = runCatching {
                client.get("$OFFICIAL_BASE_URL$path") {
                    header(HttpHeaders.Authorization, apiKey)
                    header(HttpHeaders.Accept, "application/json")
                }
            }.getOrNull()

            if (officialResponse != null && officialResponse.status == HttpStatusCode.OK) {
                return officialResponse.body()
            }
        }

        val mirrorResponse = client.get("$FALLBACK_MIRROR_URL$path") {
            header(HttpHeaders.Accept, "application/json")
        }
        return mirrorResponse.body()
    }
}
