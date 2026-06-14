package com.foxugly.pushit_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.foxugly.pushit_app.diagnostics.AppLogger
import com.foxugly.pushit_app.platform.FcmTokenProvider
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PushItFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AppLogger.info(TAG, "FirebaseMessagingService.onNewToken")
        FcmTokenProvider.instance.updateToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        AppLogger.info(
            TAG,
            "Message received from=${message.from.orEmpty()} dataKeys=${message.data.keys.joinToString()}",
        )
        val title = message.notification?.title ?: message.data["title"] ?: "PushIT"
        val body = message.notification?.body ?: message.data["message"] ?: ""
        val notificationId = message.data[EXTRA_NOTIFICATION_ID]?.toIntOrNull()
        showNotification(title, body, notificationId)
        // Keep the refresh broadcast inside our own package (it is only consumed
        // by MainActivity's RECEIVER_NOT_EXPORTED receiver).
        sendBroadcast(Intent(ACTION_NOTIFICATION_RECEIVED).setPackage(packageName))
    }

    private fun showNotification(title: String, body: String, notificationId: Int?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // createNotificationChannel is idempotent (no-op if it already exists),
            // so just call it every time — simpler and thread-safe, vs the previous
            // check-then-set flag which was a TOCTOU race under concurrent messages.
            val channel = NotificationChannel(
                CHANNEL_ID, "PushIT Notifications", NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)

        // Tapping the notification opens the app and (when known) deep-links to
        // the message. The service can't reference MainActivity directly (it
        // lives in the app module), so target the package launch intent.
        deepLinkPendingIntent(notificationId)?.let(builder::setContentIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        AppLogger.info(TAG, "Local notification displayed deepLinkId=${notificationId ?: "none"}")
    }

    private fun deepLinkPendingIntent(notificationId: Int?): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (notificationId != null) {
            launchIntent.putExtra(EXTRA_NOTIFICATION_ID, notificationId.toString())
        }
        return PendingIntent.getActivity(
            this,
            notificationId ?: 0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "PushIT/FirebaseService"
        const val CHANNEL_ID = "pushit_notifications"
        const val ACTION_NOTIFICATION_RECEIVED = "com.foxugly.pushit_app.NOTIFICATION_RECEIVED"
        // Intent extra carrying the tapped notification's id (string, to match
        // the FCM data key used for the system-tray launch in the background).
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
