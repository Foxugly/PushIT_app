package com.foxugly.pustit_app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Auth ---

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val access: String,
    val refresh: String,
    val user: UserProfile,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
)

@Serializable
data class RefreshRequest(
    val refresh: String,
)

@Serializable
data class RefreshResponse(
    val access: String,
)

@Serializable
data class UserProfile(
    val id: Int,
    val email: String,
    val username: String,
)

// --- Device ---

@Serializable
data class DeviceLinkRequest(
    @SerialName("push_token") val pushToken: String,
    val platform: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class DeviceLinkResponse(
    val status: String,
    @SerialName("device_id") val deviceId: Int,
    @SerialName("device_created") val deviceCreated: Boolean,
    @SerialName("link_created") val linkCreated: Boolean,
    @SerialName("application_id") val applicationId: Int,
)

// --- Notifications ---

@Serializable
data class NotificationListResponse(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<Notification>,
)

@Serializable
data class Notification(
    val id: Int,
    val title: String,
    val message: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("sent_at") val sentAt: String?,
)

@Serializable
data class NotificationStats(
    val pending: Int,
    val sent: Int,
    val delivered: Int,
    val failed: Int,
)
