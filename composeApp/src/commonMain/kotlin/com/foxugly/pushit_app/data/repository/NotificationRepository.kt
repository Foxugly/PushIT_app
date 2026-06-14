package com.foxugly.pushit_app.data.repository

import com.foxugly.pushit_app.data.api.*
import com.foxugly.pushit_app.diagnostics.AppLogger

class NotificationRepository(
    private val api: PushItApi,
) {
    private val tag = "PushIT/NotificationRepository"

    suspend fun getNotifications(): Result<List<Notification>> {
        AppLogger.info(tag, "Loading notifications")
        return api.getNotifications()
            .onSuccess { AppLogger.info(tag, "Loaded notifications count=${it.size}") }
            .onFailure { AppLogger.error(tag, "Failed to load notifications", it) }
    }

    /** Raw bytes of an image URL (app logos). */
    suspend fun getImageBytes(url: String): Result<ByteArray> = api.getImageBytes(url)

    /** Recipient inbox: notifications delivered to this device (by FCM push token).
     * sentSince (ISO 8601) bounds by send date; null = full history. */
    suspend fun getDeviceNotifications(
        pushToken: String,
        sentSince: String? = null,
    ): Result<List<Notification>> {
        AppLogger.info(tag, "Loading device inbox sentSince=${sentSince ?: "all"}")
        return api.getDeviceNotifications(pushToken, sentSince)
            .onSuccess { AppLogger.info(tag, "Loaded device inbox count=${it.size}") }
            .onFailure { AppLogger.error(tag, "Failed to load device inbox", it) }
    }

    suspend fun getNotification(id: Int): Result<Notification> {
        AppLogger.info(tag, "Loading notification id=$id")
        return api.getNotification(id)
            .onSuccess { AppLogger.info(tag, "Loaded notification id=$id") }
            .onFailure { AppLogger.error(tag, "Failed to load notification id=$id", it) }
    }

}
