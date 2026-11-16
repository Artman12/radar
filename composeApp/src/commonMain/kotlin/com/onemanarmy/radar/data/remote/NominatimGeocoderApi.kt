package com.onemanarmy.radar.data.remote

import com.onemanarmy.radar.platform.GeocodedAddress
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NominatimAddressDto(
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val municipality: String? = null,
    val county: String? = null,
    val district: String? = null,
    val state: String? = null
)

@Serializable
data class NominatimPlaceDto(
    @SerialName("display_name") val displayName: String = "",
    val lat: String = "",
    val lon: String = "",
    val address: NominatimAddressDto? = null
)

/**
 * OpenStreetMap Nominatim fallback geocoder for address search and reverse geocoding in Ukraine.
 */
class NominatimGeocoderApi(
    private val client: HttpClient
) {
    companion object {
        private const val SEARCH_URL = "https://nominatim.openstreetmap.org/search"
        private const val REVERSE_URL = "https://nominatim.openstreetmap.org/reverse"
        private const val USER_AGENT = "Radar-KMP-Android/1.0"
    }

    suspend fun searchInUkraine(query: String): List<GeocodedAddress> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val results: List<NominatimPlaceDto> = client.get(SEARCH_URL) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                parameter("q", query)
                parameter("format", "jsonv2")
                parameter("addressdetails", "1")
                parameter("countrycodes", "ua")
                parameter("accept-language", "uk")
                parameter("limit", "8")
            }.body()

            results.mapNotNull { item ->
                val lat = item.lat.toDoubleOrNull() ?: return@mapNotNull null
                val lon = item.lon.toDoubleOrNull() ?: return@mapNotNull null
                val addr = item.address
                GeocodedAddress(
                    displayName = item.displayName,
                    locality = addr?.city ?: addr?.town ?: addr?.village ?: addr?.municipality,
                    district = addr?.district ?: addr?.county,
                    adminArea = addr?.state,
                    latitude = lat,
                    longitude = lon
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodedAddress? = withContext(Dispatchers.IO) {
        runCatching {
            val item: NominatimPlaceDto = client.get(REVERSE_URL) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                parameter("lat", latitude.toString())
                parameter("lon", longitude.toString())
                parameter("format", "jsonv2")
                parameter("addressdetails", "1")
                parameter("accept-language", "uk")
            }.body()

            val addr = item.address
            GeocodedAddress(
                displayName = item.displayName,
                locality = addr?.city ?: addr?.town ?: addr?.village ?: addr?.municipality,
                district = addr?.district ?: addr?.county,
                adminArea = addr?.state,
                latitude = latitude,
                longitude = longitude
            )
        }.getOrNull()
    }
}
