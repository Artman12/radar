package com.onemanarmy.radar.domain.model

import kotlinx.serialization.Serializable

/**
 * Reported explosion / blast event mapped to approximate Ukrainian coordinates.
 */
@Serializable
data class ExplosionEvent(
    val id: String,
    val title: String,
    val rawText: String,
    val raionName: String,
    val oblastName: String,
    val localityName: String = "",
    val latitude: Double,
    val longitude: Double,
    val timestampIso: String,
    val timestampEpochMs: Long,
    val isRecent: Boolean = true,
    val isApproximate: Boolean = true
)

/**
 * Explosion event enriched with proximity calculation relative to the user's tracked locations.
 */
data class ExplosionWithProximity(
    val explosion: ExplosionEvent,
    val nearestPointId: String?,
    val nearestPointName: String?,
    val distanceKm: Double?
)
