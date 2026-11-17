package com.onemanarmy.radar.domain.logic

import com.onemanarmy.radar.domain.model.ActiveAlertInfo
import com.onemanarmy.radar.domain.model.AlertLevel
import com.onemanarmy.radar.domain.model.AlertType
import com.onemanarmy.radar.domain.model.AppSettings
import com.onemanarmy.radar.domain.model.NightModeBehavior
import com.onemanarmy.radar.domain.model.PointAlertState
import com.onemanarmy.radar.domain.model.SavedPoint

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
        val softPulseOnly: Boolean,
        val alertLevel: AlertLevel = AlertLevel.RED
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
        val currentHighestLevel = when {
            activeAlerts.any { it.level == AlertLevel.RED } -> AlertLevel.RED
            activeAlerts.any { it.level == AlertLevel.YELLOW } -> AlertLevel.YELLOW
            else -> AlertLevel.NONE
        }
        val signature = if (isActiveNow) {
            activeAlerts
                .sortedWith(compareBy({ it.regionId }, { it.type.name }, { it.level.name }, { it.startedAtIso.orEmpty() }))
                .joinToString("|") { "${it.regionId}:${it.type.name}:${it.level.name}:${it.startedAtIso.orEmpty()}" }
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
            },
            isThreatInRadius = previousState?.isThreatInRadius ?: false,
            highestLevel = currentHighestLevel
        )

        val isSuppressedByNightMode = isQuietHours && settings.nightModeBehavior == NightModeBehavior.DISABLED
        if (!point.notifyEnabled || !settings.hromadaAlertNotificationsEnabled || isSuppressedByNightMode) {
            return DeduplicationResult(NotificationDecision.None, updatedState)
        }

        val wasActive = previousState?.isAlertActive ?: false
        val previousSignature = previousState?.alertSignature.orEmpty()

        val applyQuietHours = isQuietHours && settings.nightModeBehavior != NightModeBehavior.NORMAL
        val silentMode = applyQuietHours && settings.nightModeBehavior == NightModeBehavior.FULL_SILENCE
        val softPulseMode = applyQuietHours && settings.nightModeBehavior == NightModeBehavior.ULTRA_SOFT_PULSE_ONLY
        val targetRegionLabel = point.hromadaName.ifBlank { point.regionName }

        val decision = when {
            !wasActive && isActiveNow -> {
                val primaryType = activeAlerts.firstOrNull { it.level == currentHighestLevel }?.type
                    ?: activeAlerts.firstOrNull()?.type
                    ?: AlertType.AIR
                NotificationDecision.AlertStarted(
                    point = point,
                    primaryType = primaryType,
                    regionName = targetRegionLabel,
                    silent = silentMode,
                    softPulseOnly = softPulseMode,
                    alertLevel = currentHighestLevel
                )
            }

            wasActive && isActiveNow && signature != previousSignature -> {
                val escalatedYellowToRed = previousState.highestLevel == AlertLevel.YELLOW &&
                    currentHighestLevel == AlertLevel.RED
                if (escalatedYellowToRed) {
                    val primaryType = activeAlerts.firstOrNull { it.level == AlertLevel.RED }?.type
                        ?: activeAlerts.firstOrNull()?.type
                        ?: AlertType.AIR
                    NotificationDecision.AlertStarted(
                        point = point,
                        primaryType = primaryType,
                        regionName = targetRegionLabel,
                        silent = silentMode,
                        softPulseOnly = softPulseMode,
                        alertLevel = AlertLevel.RED
                    )
                } else {
                    // Only notify if a genuinely new alert type started while already active
                    // (prevents duplicate alerts when an active raion alert upgrades to oblast-wide at the same severity).
                    val prevTypes = previousSignature.split("|")
                        .mapNotNull { token ->
                            token.split(":").firstNotNullOfOrNull { part ->
                                AlertType.entries.firstOrNull { it.name == part }
                            }
                        }
                        .toSet()
                    val newAlert = activeAlerts.firstOrNull { it.type !in prevTypes }
                    if (newAlert != null) {
                        NotificationDecision.AlertStarted(
                            point = point,
                            primaryType = newAlert.type,
                            regionName = targetRegionLabel,
                            silent = silentMode,
                            softPulseOnly = softPulseMode,
                            alertLevel = currentHighestLevel
                        )
                    } else {
                        NotificationDecision.None
                    }
                }
            }

            wasActive && !isActiveNow -> {
                if (applyQuietHours && settings.nightModeBehavior == NightModeBehavior.FILTER_ALL_CLEAR) {
                    NotificationDecision.None
                } else {
                    NotificationDecision.AllClear(
                        point = point,
                        regionName = targetRegionLabel,
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
