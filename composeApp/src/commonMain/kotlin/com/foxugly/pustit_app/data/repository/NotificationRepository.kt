package com.foxugly.pustit_app.data.repository

import com.foxugly.pustit_app.data.api.*

class NotificationRepository(
    private val api: PushItApi,
) {
    suspend fun getNotifications(page: Int = 1): Result<NotificationListResponse> {
        return api.getNotifications(page)
    }

    suspend fun getNotification(id: Int): Result<Notification> {
        return api.getNotification(id)
    }

    suspend fun getStats(): Result<NotificationStats> {
        return api.getNotificationStats()
    }
}
