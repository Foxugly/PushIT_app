package com.foxugly.pushit_app.platform

import platform.Foundation.NSDate
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.dateByAddingTimeInterval

actual fun isoUtcDaysAgo(days: Int): String {
    val date = NSDate().dateByAddingTimeInterval(-days.toDouble() * 86_400.0)
    return NSISO8601DateFormatter().stringFromDate(date)
}
