package com.foxugly.pushit_app.platform

/**
 * Best-effort app-icon badge for the unread count.
 *
 * iOS has a first-class badge API (reliable). Android has no standard badge
 * setter — launchers derive a dot/count from posted notifications — so the
 * Android implementation is best-effort and its appearance varies by launcher.
 */
expect fun updateAppBadge(count: Int)
