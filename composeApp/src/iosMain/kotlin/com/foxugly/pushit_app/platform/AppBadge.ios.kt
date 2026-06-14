package com.foxugly.pushit_app.platform

import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** iOS has a reliable badge API; set it on the main thread. */
actual fun updateAppBadge(count: Int) {
    dispatch_async(dispatch_get_main_queue()) {
        UIApplication.sharedApplication.applicationIconBadgeNumber = count.toLong()
    }
}
