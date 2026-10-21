package com.foxugly.pushit_app.platform

import android.os.Build
import com.foxugly.pushit_app.diagnostics.AppLogger

/** The installed APK's versionCode, read from the PackageManager (the library
 * module has no access to :androidApp's BuildConfig). */
actual fun installedAppVersionCode(): Long? {
    val context = AppContextHolder.app ?: return null
    return runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }.onFailure { AppLogger.warn("PushIT/AppUpdate", "Installed versionCode unreadable", it) }
        .getOrNull()
}
