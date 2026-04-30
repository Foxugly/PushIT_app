package com.foxugly.pushit_app

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}"
    override val platformType: String = "android"
}

actual fun getPlatform(): Platform = AndroidPlatform()