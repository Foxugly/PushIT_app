package com.foxugly.pushit_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.foxugly.pushit_app.diagnostics.AppLogger
import com.foxugly.pushit_app.platform.FcmTokenProvider
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.concurrent.Volatile

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
        showNotification(title, body)
        // Keep the refresh broadcast inside our own package (it is only consumed
        // by MainActivity's RECEIVER_NOT_EXPORTED receiver).
        sendBroadcast(Intent(ACTION_NOTIFICATION_RECEIVED).setPackage(packageName))
    }

    private fun showNotification(title: String, body: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !channelCreated) {
            val channel = NotificationChannel(
                CHANNEL_ID, "PushIT Notifications", NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
            channelCreated = true
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        AppLogger.info(TAG, "Local notification displayed")
    }

    companion object {
        private const val TAG = "PushIT/FirebaseService"
        const val CHANNEL_ID = "pushit_notifications"
        const val ACTION_NOTIFICATION_RECEIVED = "com.foxugly.pushit_app.NOTIFICATION_RECEIVED"

        // The channel is process-global; create it at most once instead of on
        // every received message.
        @Volatile
        private var channelCreated = false
    }
}
