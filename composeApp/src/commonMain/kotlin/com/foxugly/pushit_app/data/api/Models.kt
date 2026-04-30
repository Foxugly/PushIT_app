package com.foxugly.pushit_app.data.api

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
    val userkey: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    val language: String? = null,
)

// --- Device ---

@Serializable
data class DeviceIdentifyRequest(
    @SerialName("push_token") val pushToken: String,
    val platform: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class DeviceLinkRequest(
    @SerialName("app_token") val appToken: String,
    @SerialName("push_token") val pushToken: String,
    val platform: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class LinkedApplication(
    val id: Int,
    val name: String,
    val description: String? = null,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("linked_at") val linkedAt: String,
)

@Serializable
data class DeviceIdentifyResponse(
    val status: String,
    @SerialName("device_id") val deviceId: Int,
    @SerialName("device_created") val deviceCreated: Boolean,
    @SerialName("linked_applications") val linkedApplications: List<LinkedApplication> = emptyList(),
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
    @SerialName("application_id") val applicationId: Int? = null,
    @SerialName("application_name") val applicationName: String? = null,
    @SerialName("device_ids") val deviceIds: List<Int>? = null,
    val title: String,
    val message: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("scheduled_for") val scheduledFor: String? = null,
    @SerialName("effective_scheduled_for") val effectiveScheduledFor: String? = null,
    @SerialName("sent_at") val sentAt: String? = null,
)

@Serializable
data class NotificationStats(
    val status: String,
    val count: Int,
)
