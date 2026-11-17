package com.onemanarmy.radar.domain.logic

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Formats ISO-8601 alert timestamps into live HH:MM:SS duration counters.
 */
object DurationFormatter {

    fun formatDurationSince(
        startedAtIso: String?,
        now: Instant = Clock.System.now()
    ): String {
        if (startedAtIso.isNullOrBlank()) return "00:00:00"
        val startInstant = runCatching { Instant.parse(startedAtIso) }.getOrNull() ?: return "00:00:00"
        val totalSeconds = (now - startInstant).inWholeSeconds.coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "${pad2(hours)}:${pad2(minutes)}:${pad2(seconds)}"
    }

    fun parseInstantOrNull(raw: String?): Instant? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val isoParsed = runCatching { Instant.parse(trimmed) }.getOrNull()
        if (isoParsed != null) return isoParsed
        val numeric = trimmed.toLongOrNull() ?: return null
        return runCatching {
            if (numeric >= 1_000_000_000_000L) {
                Instant.fromEpochMilliseconds(numeric)
            } else {
                Instant.fromEpochSeconds(numeric)
            }
        }.getOrNull()
    }

    const val THREAT_STALE_THRESHOLD_MS = 5 * 60_000L

    fun ageMinutesSince(
        instant: Instant,
        now: Instant = Clock.System.now()
    ): Int = ((now - instant).inWholeSeconds.coerceAtLeast(0L) / 60L).toInt()

    fun isThreatOlderThan(
        updatedAtIso: String?,
        thresholdMs: Long = THREAT_STALE_THRESHOLD_MS,
        now: Instant = Clock.System.now()
    ): Boolean {
        val updated = parseInstantOrNull(updatedAtIso) ?: return false
        return (now - updated).inWholeMilliseconds.coerceAtLeast(0L) > thresholdMs
    }

    fun formatTimeOfDay(hour: Int, minute: Int): String = "${pad2(hour.toLong())}:${pad2(minute.toLong())}"

    private fun pad2(value: Long): String = if (value < 10) "0$value" else value.toString()
}
