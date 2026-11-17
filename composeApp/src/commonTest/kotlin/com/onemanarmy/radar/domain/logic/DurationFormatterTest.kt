package com.onemanarmy.radar.domain.logic

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DurationFormatterTest {

    @Test
    fun parseInstantOrNull_handlesIsoAndEpochAndInvalid() {
        assertEquals(
            Instant.parse("2026-11-16T22:00:00Z"),
            DurationFormatter.parseInstantOrNull("2026-11-16T22:00:00Z")
        )
        val epochSec = 1794866400L
        assertEquals(
            Instant.fromEpochSeconds(epochSec),
            DurationFormatter.parseInstantOrNull(epochSec.toString())
        )
        assertEquals(
            Instant.fromEpochMilliseconds(epochSec * 1000L),
            DurationFormatter.parseInstantOrNull((epochSec * 1000L).toString())
        )
        assertNull(DurationFormatter.parseInstantOrNull(null))
        assertNull(DurationFormatter.parseInstantOrNull("   "))
        assertNull(DurationFormatter.parseInstantOrNull("not-a-timestamp"))
    }

    @Test
    fun isThreatOlderThan_distinguishesWithinAndBeyondFiveMinutes() {
        val now = Instant.parse("2026-11-17T07:55:00Z")
        val twoMinutesAgo = "2026-11-17T07:53:00Z"
        val exactlyFiveMinutesAgo = "2026-11-17T07:50:00Z"
        val fiveMinutesOneSecondAgo = "2026-11-17T07:49:59Z"

        assertFalse(DurationFormatter.isThreatOlderThan(twoMinutesAgo, now = now))
        assertFalse(DurationFormatter.isThreatOlderThan(exactlyFiveMinutesAgo, now = now))
        assertTrue(DurationFormatter.isThreatOlderThan(fiveMinutesOneSecondAgo, now = now))
        assertFalse(DurationFormatter.isThreatOlderThan("", now = now))
        assertFalse(DurationFormatter.isThreatOlderThan(null, now = now))

        val parsed = DurationFormatter.parseInstantOrNull(fiveMinutesOneSecondAgo)
        assertNotNull(parsed)
        assertEquals(5, DurationFormatter.ageMinutesSince(parsed, now))
    }
}
