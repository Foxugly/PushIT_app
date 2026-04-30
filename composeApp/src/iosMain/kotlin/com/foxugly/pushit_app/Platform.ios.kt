package com.foxugly.pushit_app

import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val deviceName: String = UIDevice.currentDevice.name
    override val platformType: String = "ios"
}

actual fun getPlatform(): Platform = IOSPlatform()