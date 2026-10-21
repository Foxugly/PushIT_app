package com.foxugly.pushit_app.platform

/** iOS updates come through the App Store / TestFlight: no APK check. */
actual fun installedAppVersionCode(): Long? = null
