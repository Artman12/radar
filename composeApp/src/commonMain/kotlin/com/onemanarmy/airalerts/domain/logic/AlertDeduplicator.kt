package com.onemanarmy.airalerts.domain.logic

import com.onemanarmy.airalerts.domain.model.ActiveAlertInfo
import com.onemanarmy.airalerts.domain.model.AlertType
import com.onemanarmy.airalerts.domain.model.AppSettings
import com.onemanarmy.airalerts.domain.model.NightModeBehavior
import com.onemanarmy.airalerts.domain.model.PointAlertState
import com.onemanarmy.airalerts.domain.model.SavedPoint

/**
 * Output of deduplication evaluation for a tracked point.
 */
sealed interface NotificationDecision {
    data object None : NotificationDecision

    data class AlertStarted(
        val point: SavedPoint,
        val primaryType: AlertType,
        val regionName: String,
        val silent: Boolean,
        val softPulseOnly: Boolean
    ) : NotificationDecision

    data class AllClear(
        val point: SavedPoint,
        val regionName: String,
        val silent: Boolean,
        val softPulseOnly: Boolean
    ) : NotificationDecision
}

data class DeduplicationResult(
    val decision: NotificationDecision,
    val updatedState: PointAlertState
)

/**
 * Prevents duplicate notifications by comparing the current active alerts against persisted point state
 * and applying Night Mode delivery rules.
 */
object AlertDeduplicator {

    fun evaluateTransition(
        point: SavedPoint,
        activeAlerts: List<ActiveAlertInfo>,
        previousState: PointAlertState?,
        settings: AppSettings,
        isQuietHours: Boolean,
        nowEpochMs: Long
    ): DeduplicationResult {
        val isActiveNow = activeAlerts.isNotEmpty()
        val signature = if (isActiveNow) {
            activeAlerts
                .sortedWith(compareBy({ it.regionId }, { it.type.name }, { it.startedAtIso.orEmpty() }))
                .joinToString("|") { "${it.regionId}:${it.type.name}:${it.startedAtIso.orEmpty()}" }
        } else {
            ""
        }

        val updatedState = PointAlertState(
            pointId = point.id,
            isAlertActive = isActiveNow,
            alertSignature = signature,
            lastTransitionEpochMs = if (previousState?.isAlertActive != isActiveNow || previousState.alertSignature != signature) {
                nowEpochMs
            } else {
                previousState.lastTransitionEpochMs
            }
        )

        if (!point.notifyEnabled) {
            return DeduplicationResult(NotificationDecision.None, updatedState)
        }

        // First baseline observation: record state without spamming on initial sync unless an alert was not previously recorded?
        // Actually, if previousState == null and an alert is already active on first run, we notify once on transition from implicit false -> true.
        val wasActive = previousState?.isAlertActive ?: false
        val previousSignature = previousState?.alertSignature.orEmpty()

        val silentMode = isQuietHours && settings.nightModeBehavior == NightModeBehavior.FULL_SILENCE
        val softPulseMode = isQuietHours && settings.nightModeBehavior == NightModeBehavior.ULTRA_SOFT_PULSE_ONLY

        val decision = when {
            !wasActive && isActiveNow -> {
                val primaryType = activeAlerts.firstOrNull()?.type ?: AlertType.AIR
                NotificationDecision.AlertStarted(
                    point = point,
                    primaryType = primaryType,
                    regionName = point.regionName,
                    silent = silentMode,
                    softPulseOnly = softPulseMode
                )
            }

            wasActive && isActiveNow && signature != previousSignature -> {
                // Only notify if a new distinct alert type/region started while already active.
                val prevTokens = previousSignature.split("|").toSet()
                val newTokens = signature.split("|").toSet()
                val hasNewThreat = (newTokens - prevTokens).isNotEmpty()
                if (hasNewThreat) {
                    NotificationDecision.AlertStarted(
                        point = point,
                        primaryType = activeAlerts.firstOrNull()?.type ?: AlertType.AIR,
                        regionName = point.regionName,
                        silent = silentMode,
                        softPulseOnly = softPulseMode
                    )
                } else {
                    NotificationDecision.None
                }
            }

            wasActive && !isActiveNow -> {
                if (isQuietHours && settings.nightModeBehavior == NightModeBehavior.FILTER_ALL_CLEAR) {
                    NotificationDecision.None
                } else {
                    NotificationDecision.AllClear(
                        point = point,
                        regionName = point.regionName,
                        silent = silentMode,
                        softPulseOnly = softPulseMode
                    )
                }
            }

            else -> NotificationDecision.None
        }

        return DeduplicationResult(decision = decision, updatedState = updatedState)
    }
}
