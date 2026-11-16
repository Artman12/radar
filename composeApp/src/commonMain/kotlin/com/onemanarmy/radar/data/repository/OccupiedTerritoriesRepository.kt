package com.onemanarmy.radar.data.repository

import com.onemanarmy.radar.data.local.AppPreferencesRepository
import com.onemanarmy.radar.data.remote.DeepStateOccupiedApi
import com.onemanarmy.radar.domain.model.OccupiedTerritoriesSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Caches and refreshes the currently occupied Ukrainian territories layer from DeepStateMap.
 * Uses a 6-hour cache TTL so map rendering stays instant and offline-resilient.
 */
class OccupiedTerritoriesRepository(
    private val api: DeepStateOccupiedApi,
    private val prefsRepo: AppPreferencesRepository
) {
    companion object {
        private const val CACHE_TTL_MS = 6L * 60L * 60L * 1000L
    }

    private val refreshMutex = Mutex()

    val occupiedTerritoriesFlow: Flow<OccupiedTerritoriesSnapshot?> =
        prefsRepo.occupiedTerritoriesFlow

    suspend fun refreshIfNeeded(force: Boolean = false) {
        refreshMutex.withLock {
            val cached = prefsRepo.occupiedTerritoriesFlow.first()
            val nowMs = Clock.System.now().toEpochMilliseconds()
            if (!force &&
                cached != null &&
                cached.polygons.isNotEmpty() &&
                (nowMs - cached.fetchedAtEpochMs) < CACHE_TTL_MS
            ) {
                return
            }

            api.fetchLatestOccupied()
                .onSuccess { snapshot ->
                    if (snapshot.polygons.isNotEmpty()) {
                        prefsRepo.saveOccupiedTerritoriesCache(snapshot)
                    }
                }
        }
    }
}
