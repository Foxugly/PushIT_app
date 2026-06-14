package com.foxugly.pushit_app.ui.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class RelativeTimeTest {

    private val en = stringsFor(AppLanguage.EN)

    private fun ago(d: kotlin.time.Duration) = (Clock.System.now() - d).toString()

    @Test
    fun justNowForCurrentTimestamp() {
        assertEquals("just now", en.relativeTime(Clock.System.now().toString()))
    }

    @Test
    fun minutesAgoBucket() {
        assertEquals("5 min ago", en.relativeTime(ago(5.minutes)))
    }

    @Test
    fun hoursAgoBucket() {
        assertEquals("3 h ago", en.relativeTime(ago(3.hours)))
    }

    @Test
    fun futureTimestampIsJustNow() {
        // Clock skew: a slightly-future timestamp must not produce a negative count.
        assertEquals("just now", en.relativeTime((Clock.System.now() + 2.minutes).toString()))
    }

    @Test
    fun fallsBackToAbsoluteBeyondAWeek() {
        // A midday UTC instant stays on the same calendar date in any sane local
        // zone, so the absolute fallback is asserted without tz flakiness.
        val out = en.relativeTime("2020-06-15T12:00:00Z")
        assertTrue(out.startsWith("2020-06-15"), out)
    }

    @Test
    fun fallsBackForUnparseableInput() {
        assertEquals("not-a-date", en.relativeTime("not-a-date"))
    }

    @Test
    fun localizedTemplatesPerLanguage() {
        // The "{n}" placeholder is substituted, and each locale keeps its own copy.
        assertEquals("il y a 5 min", stringsFor(AppLanguage.FR).relativeTime(ago(5.minutes)))
        assertEquals("5 min geleden", stringsFor(AppLanguage.NL).relativeTime(ago(5.minutes)))
    }
}
