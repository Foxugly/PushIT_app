package com.foxugly.pushit_app.data.repository

import com.foxugly.pushit_app.data.api.*
import com.foxugly.pushit_app.diagnostics.AppLogger

class NotificationRepository(
    private val api: PushItApi,
) {
    private val tag = "PushIT/NotificationRepository"

    suspend fun getNotifications(page: Int = 1): Result<NotificationListResponse> {
        AppLogger.info(tag, "Loading notifications page=$page")
        return api.getNotifications(page)
            .onSuccess { AppLogger.info(tag, "Loaded notifications page=$page count=${it.results.size} hasNext=${it.next != null}") }
            .onFailure { AppLogger.error(tag, "Failed to load notifications page=$page", it) }
    }

    suspend fun getNotification(id: Int): Result<Notification> {
        AppLogger.info(tag, "Loading notification id=$id")
        return api.getNotification(id)
            .onSuccess { AppLogger.info(tag, "Loaded notification id=$id") }
            .onFailure { AppLogger.error(tag, "Failed to load notification id=$id", it) }
    }

}
