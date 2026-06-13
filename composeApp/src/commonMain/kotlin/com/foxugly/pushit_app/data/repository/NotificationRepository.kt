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

    suspend fun getNotification(id: Int): Result<Notification> {
        AppLogger.info(tag, "Loading notification id=$id")
        return api.getNotification(id)
            .onSuccess { AppLogger.info(tag, "Loaded notification id=$id") }
            .onFailure { AppLogger.error(tag, "Failed to load notification id=$id", it) }
    }

}
