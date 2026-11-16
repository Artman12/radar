package com.onemanarmy.radar.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.onemanarmy.radar.data.remote.HttpClientFactory
import com.onemanarmy.radar.domain.model.ActiveAlertInfo
import com.onemanarmy.radar.domain.model.AppSettings
import com.onemanarmy.radar.domain.model.OccupiedTerritoriesSnapshot
import com.onemanarmy.radar.domain.model.PointAlertState
import com.onemanarmy.radar.domain.model.RegionItem
import com.onemanarmy.radar.domain.model.SavedPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * DataStore-backed KMP repository for settings, saved locations, cached regions,
 * active alerts snapshot, and deduplication state.
 */
class AppPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = HttpClientFactory.sharedJson
) {
    private object Keys {
        val SETTINGS = stringPreferencesKey("app_settings_json")
        val SAVED_POINTS = stringPreferencesKey("saved_points_json")
        val CACHED_REGIONS = stringPreferencesKey("cached_regions_json")
        val ALERT_STATES = stringPreferencesKey("dedup_alert_states_json")
        val LAST_ACTIVE_ALERTS = stringPreferencesKey("last_active_alerts_json")
        val OCCUPIED_TERRITORIES_CACHE = stringPreferencesKey("occupied_territories_cache_json")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        prefs[Keys.SETTINGS]?.let { raw ->
            runCatching { json.decodeFromString<AppSettings>(raw) }.getOrNull()
        } ?: AppSettings()
    }

    val savedPointsFlow: Flow<List<SavedPoint>> = dataStore.data.map { prefs ->
        prefs[Keys.SAVED_POINTS]?.let { raw ->
            runCatching { json.decodeFromString<List<SavedPoint>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    val cachedRegionsFlow: Flow<List<RegionItem>> = dataStore.data.map { prefs ->
        prefs[Keys.CACHED_REGIONS]?.let { raw ->
            runCatching { json.decodeFromString<List<RegionItem>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    val alertStatesFlow: Flow<Map<String, PointAlertState>> = dataStore.data.map { prefs ->
        prefs[Keys.ALERT_STATES]?.let { raw ->
            runCatching { json.decodeFromString<Map<String, PointAlertState>>(raw) }.getOrNull()
        } ?: emptyMap()
    }

    val lastActiveAlertsFlow: Flow<List<ActiveAlertInfo>> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_ACTIVE_ALERTS]?.let { raw ->
            runCatching { json.decodeFromString<List<ActiveAlertInfo>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    val occupiedTerritoriesFlow: Flow<OccupiedTerritoriesSnapshot?> = dataStore.data.map { prefs ->
        prefs[Keys.OCCUPIED_TERRITORIES_CACHE]?.let { raw ->
            runCatching { json.decodeFromString<OccupiedTerritoriesSnapshot>(raw) }.getOrNull()
        }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.SETTINGS]?.let {
                runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull()
            } ?: AppSettings()
            val updated = transform(current)
            prefs[Keys.SETTINGS] = json.encodeToString(updated)
        }
    }

    suspend fun saveOrUpdatePoint(point: SavedPoint) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.SAVED_POINTS]?.let {
                runCatching { json.decodeFromString<List<SavedPoint>>(it) }.getOrNull()
            } ?: emptyList()
            val mutable = current.toMutableList()
            val index = mutable.indexOfFirst { it.id == point.id }
            if (index >= 0) {
                mutable[index] = point
            } else {
                mutable.add(point)
            }
            prefs[Keys.SAVED_POINTS] = json.encodeToString(mutable)
        }
    }

    suspend fun deletePoint(pointId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.SAVED_POINTS]?.let {
                runCatching { json.decodeFromString<List<SavedPoint>>(it) }.getOrNull()
            } ?: emptyList()
            val filtered = current.filterNot { it.id == pointId }
            prefs[Keys.SAVED_POINTS] = json.encodeToString(filtered)

            // Remove stale deduplication entry for deleted point.
            val currentStates = prefs[Keys.ALERT_STATES]?.let {
                runCatching { json.decodeFromString<Map<String, PointAlertState>>(it) }.getOrNull()
            } ?: emptyMap()
            if (currentStates.containsKey(pointId)) {
                prefs[Keys.ALERT_STATES] = json.encodeToString(currentStates - pointId)
            }
        }
    }

    suspend fun saveRegionsCache(regions: List<RegionItem>) {
        dataStore.edit { prefs ->
            prefs[Keys.CACHED_REGIONS] = json.encodeToString(regions)
        }
    }

    suspend fun saveAlertStates(states: Map<String, PointAlertState>) {
        dataStore.edit { prefs ->
            prefs[Keys.ALERT_STATES] = json.encodeToString(states)
        }
    }

    suspend fun updateAlertStates(
        transform: (Map<String, PointAlertState>) -> Map<String, PointAlertState>
    ) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.ALERT_STATES]?.let {
                runCatching { json.decodeFromString<Map<String, PointAlertState>>(it) }.getOrNull()
            } ?: emptyMap()
            val updated = transform(current)
            prefs[Keys.ALERT_STATES] = json.encodeToString(updated)
        }
    }

    suspend fun saveLastActiveAlerts(alerts: List<ActiveAlertInfo>) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_ACTIVE_ALERTS] = json.encodeToString(alerts)
        }
    }

    suspend fun saveOccupiedTerritoriesCache(snapshot: OccupiedTerritoriesSnapshot) {
        dataStore.edit { prefs ->
            prefs[Keys.OCCUPIED_TERRITORIES_CACHE] = json.encodeToString(snapshot)
        }
    }
}
