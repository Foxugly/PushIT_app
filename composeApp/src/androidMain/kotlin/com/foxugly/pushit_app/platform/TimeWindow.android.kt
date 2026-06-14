package com.foxugly.pushit_app.platform

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// SimpleDateFormat (not java.time) so it works on minSdk 24 without desugaring.
actual fun isoUtcDaysAgo(days: Int): String {
    val millis = System.currentTimeMillis() - days.toLong() * 86_400_000L
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(millis))
}
