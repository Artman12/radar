package com.onemanarmy.radar.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.onemanarmy.radar.data.remote.NominatimGeocoderApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path.Companion.toPath
import java.util.Locale
import kotlin.coroutines.resume

class AndroidDataStoreFactory(
    private val context: Context
) : PlatformDataStoreFactory {
    private val instance: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                context.filesDir.resolve("airalerts_preferences.preferences_pb").absolutePath.toPath()
            }
        )
    }

    override fun createPreferencesDataStore(): DataStore<Preferences> = instance
}

class AndroidLocationClient(
    private val context: Context
) : PlatformLocationClient {
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentCoordinates(): Coordinates? {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return null

        // 1. Check cached Fused Location first for near-instant startup (< 15 ms).
        val cachedFused = getLastFusedLocation()
        val nowMs = System.currentTimeMillis()
        if (cachedFused != null && nowMs - cachedFused.time <= 180_000L) {
            return Coordinates(cachedFused.latitude, cachedFused.longitude)
        }

        // 2. Request a fast Wi-Fi/Cell/GPS fix (balanced power resolves quickly indoors).
        val freshLocation = withTimeoutOrNull(2_500L) {
            suspendCancellableCoroutine { cont ->
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }
                fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        if (cont.isActive) {
                            cont.resume(loc?.let { Coordinates(it.latitude, it.longitude) })
                        }
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
                    .addOnCanceledListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }
        }

        if (freshLocation != null) return freshLocation

        // 3. Fall back to older cached Fused Location or system LocationManager lastKnownLocation.
        if (cachedFused != null) {
            return Coordinates(cachedFused.latitude, cachedFused.longitude)
        }
        return getFallbackLastKnownLocation(hasFine)
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastFusedLocation(): Location? {
        return withTimeoutOrNull(600L) {
            suspendCancellableCoroutine { cont ->
                fusedClient.lastLocation
                    .addOnSuccessListener { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
                    .addOnCanceledListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getFallbackLastKnownLocation(hasFine: Boolean): Coordinates? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            if (hasFine) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        val best = providers.mapNotNull { provider ->
            runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
        return best?.let { Coordinates(it.latitude, it.longitude) }
    }
}

class AndroidGeocoder(
    private val context: Context,
    private val nominatimApi: NominatimGeocoderApi
) : PlatformGeocoder {

    @Volatile
    private var bypassSystemGeocoderUntilMs: Long = 0L

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodedAddress? {
        val now = System.currentTimeMillis()
        val systemResult = if (now >= bypassSystemGeocoderUntilMs && Geocoder.isPresent()) {
            withTimeoutOrNull(1_000L) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val geocoder = Geocoder(context, Locale.forLanguageTag("uk-UA"))
                        val addresses = getFromLocationCompat(geocoder, latitude, longitude, 1)
                        addresses.firstOrNull()?.toGeocodedAddress(latitude, longitude)
                    }.getOrNull()
                }
            }.also { res ->
                if (res == null) {
                    bypassSystemGeocoderUntilMs = System.currentTimeMillis() + 60_000L
                }
            }
        } else {
            null
        }
        return systemResult ?: nominatimApi.reverseGeocode(latitude, longitude)
    }

    override suspend fun searchAddress(query: String): List<GeocodedAddress> {
        if (query.isBlank()) return emptyList()
        val nominatimResults = nominatimApi.searchInUkraine(query)
        if (nominatimResults.isNotEmpty()) return nominatimResults

        return withTimeoutOrNull(3_000L) {
            withContext(Dispatchers.IO) {
                if (!Geocoder.isPresent()) return@withContext emptyList()
                runCatching {
                    val geocoder = Geocoder(context, Locale.forLanguageTag("uk-UA"))
                    val addresses = getFromLocationNameCompat(geocoder, "$query, Україна", 6)
                    addresses.map { it.toGeocodedAddress(it.latitude, it.longitude) }
                }.getOrDefault(emptyList())
            }
        }.orEmpty()
    }

    @Suppress("DEPRECATION")
    private suspend fun getFromLocationCompat(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
        maxResults: Int
    ): List<Address> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GeocoderApi33.getFromLocation(geocoder, latitude, longitude, maxResults)
        } else {
            geocoder.getFromLocation(latitude, longitude, maxResults).orEmpty()
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun getFromLocationNameCompat(
        geocoder: Geocoder,
        locationName: String,
        maxResults: Int
    ): List<Address> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GeocoderApi33.getFromLocationName(geocoder, locationName, maxResults)
        } else {
            geocoder.getFromLocationName(locationName, maxResults).orEmpty()
        }
    }

    private fun Address.toGeocodedAddress(fallbackLat: Double, fallbackLon: Double): GeocodedAddress {
        val parts = listOfNotNull(locality, subAdminArea, adminArea).distinct()
        return GeocodedAddress(
            displayName = parts.joinToString(", ").ifBlank { getAddressLine(0).orEmpty() },
            locality = locality ?: subLocality,
            district = subAdminArea,
            adminArea = adminArea,
            latitude = if (hasLatitude()) latitude else fallbackLat,
            longitude = if (hasLongitude()) longitude else fallbackLon
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object GeocoderApi33 {
    suspend fun getFromLocation(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
        maxResults: Int
    ): List<Address> = suspendCancellableCoroutine { cont ->
        geocoder.getFromLocation(
            latitude,
            longitude,
            maxResults,
            object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (cont.isActive) cont.resume(addresses)
                }

                override fun onError(errorMessage: String?) {
                    if (cont.isActive) cont.resume(emptyList())
                }
            }
        )
    }

    suspend fun getFromLocationName(
        geocoder: Geocoder,
        locationName: String,
        maxResults: Int
    ): List<Address> = suspendCancellableCoroutine { cont ->
        geocoder.getFromLocationName(
            locationName,
            maxResults,
            object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (cont.isActive) cont.resume(addresses)
                }

                override fun onError(errorMessage: String?) {
                    if (cont.isActive) cont.resume(emptyList())
                }
            }
        )
    }
}
