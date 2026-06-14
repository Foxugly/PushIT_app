package com.foxugly.pushit_app.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.foxugly.pushit_app.diagnostics.AppLogger

/**
 * Holds the application Context so the badge (and any other context-needing
 * platform helper) can run from commonMain. Set once in MainActivity.onCreate.
 */
object AppContextHolder {
    @Volatile
    var app: Context? = null
}

private const val TAG = "PushIT/AppBadge"
private const val BADGE_CHANNEL_ID = "pushit_badge"
private const val BADGE_NOTIFICATION_ID = 0x42_42

/**
 * Best-effort badge on Android: a minimized (IMPORTANCE_MIN) notification whose
 * [NotificationCompat.Builder.setNumber] drives the launcher badge count where
 * supported; cleared when the count reaches zero. Launchers that don't support
 * numeric badges still show their notification dot.
 */
actual fun updateAppBadge(count: Int) {
    val context = AppContextHolder.app ?: return
    val manager = NotificationManagerCompat.from(context)

    if (count <= 0) {
        manager.cancel(BADGE_NOTIFICATION_ID)
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val system = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (system.getNotificationChannel(BADGE_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                BADGE_CHANNEL_ID, "PushIT badge", NotificationManager.IMPORTANCE_MIN,
            ).apply { setShowBadge(true) }
            system.createNotificationChannel(channel)
        }
    }

    val notification = NotificationCompat.Builder(context, BADGE_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("PushIT")
        .setContentText(count.toString())
        .setNumber(count)
        .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setOngoing(false)
        .build()

    try {
        manager.notify(BADGE_NOTIFICATION_ID, notification)
    } catch (e: SecurityException) {
        // POST_NOTIFICATIONS not granted — drop silently; the badge is optional.
        AppLogger.warn(TAG, "Badge post denied", e)
    }
}
