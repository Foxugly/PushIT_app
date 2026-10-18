package com.foxugly.pushit_app

interface Platform {
    val name: String
    val deviceName: String
    val platformType: String  // "android" or "ios"
}

expect fun getPlatform(): Platform

/** The device's preferred UI language as an ISO code ("fr", "nl-BE"…), or null. */
expect fun deviceLanguageCode(): String?
