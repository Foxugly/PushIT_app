package com.foxugly.pushit_app.platform

/**
 * ISO-8601 UTC timestamp for `days` ago, e.g. "2026-05-15T11:44:58Z".
 * Used as the recent-window lower bound (`start_datetime`) for the inbox.
 */
expect fun isoUtcDaysAgo(days: Int): String
