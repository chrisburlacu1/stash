package com.example.stash.models

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/** Boundary coverage for [relativeSavedLabel]: pure function, so every branch is a plain assert. */
class RelativeTimeTest {

    private val now = 1_700_000_000_000L // arbitrary fixed epoch millis

    private fun ago(amount: Long, unit: TimeUnit): Long = now - unit.toMillis(amount)

    @Test
    fun `under a minute is now`() {
        assertEquals("now", relativeSavedLabel(now, now))
        assertEquals("now", relativeSavedLabel(ago(30, TimeUnit.SECONDS), now))
        assertEquals("now", relativeSavedLabel(ago(59, TimeUnit.SECONDS), now))
    }

    @Test
    fun `minutes boundary`() {
        assertEquals("1m", relativeSavedLabel(ago(1, TimeUnit.MINUTES), now))
        assertEquals("59m", relativeSavedLabel(ago(59, TimeUnit.MINUTES), now))
    }

    @Test
    fun `hours boundary`() {
        assertEquals("1h", relativeSavedLabel(ago(60, TimeUnit.MINUTES), now))
        assertEquals("1h", relativeSavedLabel(ago(1, TimeUnit.HOURS), now))
        assertEquals("23h", relativeSavedLabel(ago(23, TimeUnit.HOURS), now))
    }

    @Test
    fun `days boundary`() {
        assertEquals("1d", relativeSavedLabel(ago(24, TimeUnit.HOURS), now))
        assertEquals("1d", relativeSavedLabel(ago(1, TimeUnit.DAYS), now))
        assertEquals("6d", relativeSavedLabel(ago(6, TimeUnit.DAYS), now))
    }

    @Test
    fun `weeks boundary`() {
        assertEquals("1w", relativeSavedLabel(ago(7, TimeUnit.DAYS), now))
        assertEquals("2w", relativeSavedLabel(ago(14, TimeUnit.DAYS), now))
        assertEquals("4w", relativeSavedLabel(ago(29, TimeUnit.DAYS), now))
    }

    @Test
    fun `months boundary`() {
        assertEquals("1mo", relativeSavedLabel(ago(30, TimeUnit.DAYS), now))
        assertEquals("2mo", relativeSavedLabel(ago(60, TimeUnit.DAYS), now))
        assertEquals("12mo", relativeSavedLabel(ago(364, TimeUnit.DAYS), now))
    }

    @Test
    fun `years boundary`() {
        assertEquals("1y", relativeSavedLabel(ago(365, TimeUnit.DAYS), now))
        assertEquals("2y", relativeSavedLabel(ago(365 * 2, TimeUnit.DAYS), now))
        assertEquals("10y", relativeSavedLabel(ago(365 * 10, TimeUnit.DAYS), now))
    }

    @Test
    fun `negative elapsed time is clamped to zero via coerceAtLeast`() {
        // savedAtEpochMillis in the future (clock skew, or a bad timestamp) must not produce a
        // negative label; the function's coerceAtLeast(0L) guards exactly this.
        val future = now + TimeUnit.HOURS.toMillis(5)
        assertEquals("now", relativeSavedLabel(future, now))
    }
}
