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
    val password: String,
    // Cloudflare Turnstile token. The backend only enforces it when Turnstile is
    // enabled server-side; the mobile client has no captcha widget, so this stays
    // null today (omitted from the body via explicitNulls=false). See PushItApi.json.
    @SerialName("turnstile_token") val turnstileToken: String? = null,
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
    val userkey: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("email_confirmed") val emailConfirmed: Boolean? = null,
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
    // Always sent by the backend (empty string when unset, never null) — matches
    // the schema's required, non-null `description`.
    val description: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("linked_at") val linkedAt: String,
    val logo: String? = null,
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

@Serializable
data class DeviceUnlinkRequest(
    @SerialName("app_token") val appToken: String,
    @SerialName("push_token") val pushToken: String,
)

@Serializable
data class DeviceUnlinkByApplicationRequest(
    @SerialName("push_token") val pushToken: String,
    @SerialName("application_id") val applicationId: Int,
)

@Serializable
data class DeviceUnlinkResponse(
    val status: String,
    @SerialName("device_id") val deviceId: Int? = null,
    @SerialName("application_id") val applicationId: Int,
    val unlinked: Boolean,
)

// --- Notifications ---

@Serializable
data class Notification(
    val id: Int,
    // Always present on every notification response (NotificationReadSerializer) —
    // required + non-null, matching the schema. application_logo stays nullable.
    @SerialName("application_id") val applicationId: Int,
    @SerialName("application_name") val applicationName: String,
    @SerialName("application_logo") val applicationLogo: String? = null,
    @SerialName("device_ids") val deviceIds: List<Int>,
    val title: String,
    val message: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("scheduled_for") val scheduledFor: String? = null,
    @SerialName("effective_scheduled_for") val effectiveScheduledFor: String? = null,
    @SerialName("sent_at") val sentAt: String? = null,
)

@Serializable
data class NotificationOpenedReceiptRequest(
    @SerialName("push_token") val pushToken: String,
)

@Serializable
data class NotificationOpenedReceiptResponse(
    val status: String,
    @SerialName("opened_at") val openedAt: String? = null,
)

@Serializable
data class NotificationStats(
    val status: String,
    val count: Int,
)
