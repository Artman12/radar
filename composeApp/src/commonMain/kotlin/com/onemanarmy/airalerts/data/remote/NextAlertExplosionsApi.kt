package com.onemanarmy.airalerts.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class NextAlertRaionDto(
    val key: String = "",
    val name: String = "",
    val oblast: String = "",
    val status: String = "",
    @SerialName("has_kab") val hasKab: Boolean = false,
    @SerialName("has_uav") val hasUav: Boolean = false,
    @SerialName("threat_desc") val threatDesc: String? = null
)

@Serializable
data class NextAlertBlastDto(
    val date: String = "",
    val text: String = ""
)

@Serializable
data class NextAlertIntervalDto(
    val id: Long = 0L,
    @SerialName("start_ts") val startTs: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("dur_min") val durMin: Int? = null,
    @SerialName("max_level") val maxLevel: String? = null,
    val blasts: List<NextAlertBlastDto> = emptyList()
)

@Serializable
data class NextAlertDistrictTimelineResponseDto(
    val name: String = "",
    val oblast: String = "",
    val intervals: List<NextAlertIntervalDto> = emptyList()
)

/**
 * Client for NextAlert (`https://next-alert.pp.ua`) public alert & district blast timeline API.
 * Strictly isolated from official alert polling; failures never disrupt core monitoring.
 */
class NextAlertExplosionsApi(
    private val client: HttpClient,
    private val json: Json = HttpClientFactory.sharedJson
) {
    companion object {
        const val NEXTALERT_BASE_URL = "https://next-alert.pp.ua"
        const val RAIONS_ENDPOINT = "$NEXTALERT_BASE_URL/api/v1/raions"
        const val DISTRICT_TIMELINE_ENDPOINT = "$NEXTALERT_BASE_URL/api/v1/alerts/district-timeline"
    }

    suspend fun fetchActiveRaions(): Result<List<NextAlertRaionDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.get(RAIONS_ENDPOINT) {
                parameter("alert_only", "true")
                header(HttpHeaders.Accept, "application/json")
            }
            check(response.status.isSuccess()) {
                "NextAlert raions endpoint returned ${response.status}"
            }
            val root = response.body<JsonElement>()
            when (root) {
                is JsonArray -> json.decodeFromJsonElement<List<NextAlertRaionDto>>(root)
                is JsonObject -> {
                    val array = root["raions"] ?: root["data"] ?: root["items"]
                    if (array is JsonArray) {
                        json.decodeFromJsonElement<List<NextAlertRaionDto>>(array)
                    } else {
                        emptyList()
                    }
                }
                else -> emptyList()
            }
        }
    }

    suspend fun fetchDistrictTimeline(
        raionName: String,
        oblastName: String
    ): Result<NextAlertDistrictTimelineResponseDto> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.get(DISTRICT_TIMELINE_ENDPOINT) {
                parameter("name", raionName)
                if (oblastName.isNotBlank()) {
                    parameter("oblast", oblastName)
                }
                header(HttpHeaders.Accept, "application/json")
            }
            check(response.status.isSuccess()) {
                "NextAlert district-timeline returned ${response.status}"
            }
            response.body<NextAlertDistrictTimelineResponseDto>()
        }
    }
}
