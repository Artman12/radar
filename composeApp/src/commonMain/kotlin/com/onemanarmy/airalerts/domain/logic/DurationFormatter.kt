package com.onemanarmy.airalerts.domain.logic

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

    fun formatTimeOfDay(hour: Int, minute: Int): String = "${pad2(hour.toLong())}:${pad2(minute.toLong())}"

    private fun pad2(value: Long): String = if (value < 10) "0$value" else value.toString()
}
