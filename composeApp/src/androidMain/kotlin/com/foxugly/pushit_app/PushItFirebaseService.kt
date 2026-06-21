package com.foxugly.pushit_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.net.HttpURLConnection
import java.net.URL
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
        // Data-only payload carries the app's identity so the notification can show
        // the per-app logo (large icon) and "<app>" in the header (subtext).
        val appName = message.data["application_name"]?.takeIf { it.isNotBlank() }
        val logoUrl = message.data["application_logo"]?.takeIf { it.isNotBlank() }
        showNotification(title, body, notificationId, appName, logoUrl)
        // Keep the refresh broadcast inside our own package (it is only consumed
        // by MainActivity's RECEIVER_NOT_EXPORTED receiver).
        sendBroadcast(Intent(ACTION_NOTIFICATION_RECEIVED).setPackage(packageName))
    }

    private fun showNotification(
        title: String,
        body: String,
        notificationId: Int?,
        appName: String?,
        logoUrl: String?,
    ) {
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
            .setSmallIcon(resolveSmallIcon())
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)

        val logo = if (!logoUrl.isNullOrBlank()) downloadBitmap(logoUrl) else null
        if (logo != null && !appName.isNullOrBlank()) {
            // Messenger/WhatsApp look: the app logo is the sender avatar (LEFT), with
            // the branded PushIT small icon as a corner badge.
            // The TITLE is the sender name — MessagingStyle renders it bold on line 1,
            // followed by the timestamp ("• now"). The BODY is the message, on line 2.
            // Conversation (WhatsApp-style) look: the app logo is the LEFT avatar with
            // the PushIT small icon as a corner badge, and the notification stays out of
            // the OS bundle (each app = its own conversation). The TITLE drives line 1:
            // it is both the conversation label (shortcut) and the sender name, so the
            // header reads "<title> • now"; the BODY is the message on the line below.
            val sender = Person.Builder()
                .setName(title.ifBlank { appName })
                .setIcon(IconCompat.createWithBitmap(logo))
                .build()
            val messageText = body.ifBlank { " " }
            // One conversation per app (stable id → same-app pushes update in place;
            // different apps show separately), labelled with the latest title.
            val shortcutId = "pushit_conv_" + appName.filter { it.isLetterOrDigit() }.ifBlank { "app" }
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(Intent.ACTION_VIEW).setPackage(packageName)
            val shortcut = ShortcutInfoCompat.Builder(this, shortcutId)
                .setShortLabel(title.ifBlank { appName })
                .setLongLived(true)
                .setIcon(IconCompat.createWithBitmap(logo))
                .setPerson(sender)
                .setIntent(launchIntent)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)

            builder.setShortcutId(shortcutId)
            builder.setStyle(
                NotificationCompat.MessagingStyle(sender)
                    .addMessage(messageText, System.currentTimeMillis(), sender),
            )
        } else {
            // Fallback (no logo): app name as subtext → header reads "PushIT • <app>".
            if (!appName.isNullOrBlank()) {
                builder.setSubText(appName)
            }
        }

        // Tapping the notification opens the app and (when known) deep-links to
        // the message. The service can't reference MainActivity directly (it
        // lives in the app module), so target the package launch intent.
        deepLinkPendingIntent(notificationId)?.let(builder::setContentIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        AppLogger.info(TAG, "Local notification displayed deepLinkId=${notificationId ?: "none"} app=${appName ?: "-"}")
    }

    /** Download an image for the large icon (best-effort; null on any failure).
     * Runs on the FCM background thread, so a short blocking fetch is fine.
     * The URL comes from the (untrusted) push payload, so it is host-allow-listed
     * first to avoid fetching arbitrary attacker-chosen URLs (SSRF/abuse). */
    private fun downloadBitmap(url: String): Bitmap? {
        if (!isLogoUrlAllowed(url)) return null
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                doInput = true
            }
            try {
                connection.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally {
                connection.disconnect()
            }
        }.getOrElse {
            AppLogger.warn(TAG, "Large-icon download failed: ${it.message}")
            null
        }
    }

    /** Allow the logo fetch only over https to the known prod backend host. Anything
     * else (other hosts/schemes from a spoofed payload) is rejected and we fall back
     * to the default small icon. Rejections are logged at debug level only. */
    private fun isLogoUrlAllowed(rawUrl: String): Boolean {
        val parsed = runCatching { URL(rawUrl) }.getOrNull()
        val host = parsed?.host
        if (parsed != null && parsed.protocol == "https" && host == PROD_LOGO_HOST) return true
        AppLogger.debug(TAG, "Rejected logo URL (host not allow-listed): host=${host ?: "?"}")
        return false
    }

    // Branded monochrome notification icon (white "P" with the fox cut out). It
    // lives in the app module's resources, so resolve it by name — the service
    // can't reference the app module's R. Falls back to a framework icon if absent.
    private fun resolveSmallIcon(): Int {
        val id = resources.getIdentifier("ic_notification", "drawable", packageName)
        return if (id != 0) id else android.R.drawable.ic_dialog_info
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
        // Logo-fetch allow-list: the prod backend host only (see MainActivity's
        // PROD_API_BASE_URL = https://pushit-api.foxugly.com/...).
        private const val PROD_LOGO_HOST = "pushit-api.foxugly.com"
    }
}
