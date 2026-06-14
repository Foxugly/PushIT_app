package com.foxugly.pushit_app.platform

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * ISO-8601 UTC timestamp (whole seconds) for `days` ago, e.g. "2026-05-15T11:44:58Z".
 * Used as the recent-window lower bound (`sent_since`) for the inbox.
 *
 * Pure-common via kotlin.time / kotlinx-datetime — no per-platform formatter.
 * Truncated to seconds (no fractional part) to match the backend filter contract.
 */
@OptIn(ExperimentalTime::class)
fun isoUtcDaysAgo(days: Int): String =
    Instant.fromEpochSeconds((Clock.System.now() - days.days).epochSeconds).toString()

private fun Int.pad2(): String = toString().padStart(2, '0')

/**
 * "yyyy-MM-dd HH:mm" in the device's local time zone. The backend timestamps are
 * UTC (ISO-8601 with `Z`); we parse and render them locally so the user sees their
 * own wall-clock. Unparseable input falls back to the raw string.
 */
@OptIn(ExperimentalTime::class)
fun formatLocalShort(isoTimestamp: String): String = try {
    val dt = Instant.parse(isoTimestamp).toLocalDateTime(TimeZone.currentSystemDefault())
    "${dt.date} ${dt.hour.pad2()}:${dt.minute.pad2()}"
} catch (_: Exception) {
    isoTimestamp
}

/**
 * "yyyy-MM-dd {at} HH:mm:ss" in the device's local time zone, where [at] is the
 * localized "at" connector. Unparseable input falls back to the raw string.
 */
@OptIn(ExperimentalTime::class)
fun formatLocalFull(isoTimestamp: String, at: String): String = try {
    val dt = Instant.parse(isoTimestamp).toLocalDateTime(TimeZone.currentSystemDefault())
    "${dt.date} $at ${dt.hour.pad2()}:${dt.minute.pad2()}:${dt.second.pad2()}"
} catch (_: Exception) {
    isoTimestamp
}
