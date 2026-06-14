package com.foxugly.pushit_app.data.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun loginResponseDeserialization() {
        val raw = """
            {
                "access": "eyJ...",
                "refresh": "eyR...",
                "user": {"id": 1, "email": "test@example.com", "userkey": "u-abc", "is_active": true, "language": "fr"}
            }
        """.trimIndent()
        val result = json.decodeFromString<LoginResponse>(raw)
        assertEquals("eyJ...", result.access)
        assertEquals("eyR...", result.refresh)
        assertEquals(1, result.user.id)
        assertEquals("test@example.com", result.user.email)
        assertEquals("u-abc", result.user.userkey)
    }

    @Test
    fun userProfileDeserializesWithoutUsername() {
        // Regression guard: the server dropped `username` from /me/ and the login
        // user payload. UserProfile must decode without it (it used to be a
        // required non-null field → SerializationException on every real call).
        val raw = """{"id": 7, "email": "a@b.test", "userkey": "k", "is_active": true, "language": "en"}"""
        val result = json.decodeFromString<UserProfile>(raw)
        assertEquals(7, result.id)
        assertEquals("a@b.test", result.email)
        assertEquals("k", result.userkey)
    }

    @Test
    fun registerRequestOmitsNullTurnstileToken() {
        // With explicitNulls=false the absent captcha token must NOT be serialized
        // (DRF rejects an explicit null on the non-null turnstile_token field).
        val encoder = Json { encodeDefaults = true; explicitNulls = false }
        val body = encoder.encodeToString(RegisterRequest(email = "a@b.test", password = "secret123"))
        assertTrue(!body.contains("turnstile_token"), "null turnstile_token must be omitted: $body")
        assertTrue(!body.contains("username"), "username is no longer part of the contract: $body")
    }

    @Test
    fun notificationListDeserialization() {
        // /notifications/ returns a bare array (un-paginated).
        val raw = """
            [
                {
                    "id": 42,
                    "application_id": 3,
                    "application_name": "Acme",
                    "device_ids": [1, 2],
                    "title": "Deploy complete",
                    "message": "Version 2.1 deployed successfully",
                    "status": "delivered",
                    "created_at": "2026-04-14T10:00:00Z",
                    "sent_at": "2026-04-14T10:00:01Z"
                }
            ]
        """.trimIndent()
        val result = json.decodeFromString<List<Notification>>(raw)
        assertEquals(1, result.size)
        assertEquals(42, result[0].id)
        assertEquals("Deploy complete", result[0].title)
        assertEquals("delivered", result[0].status)
    }

    @Test
    fun deviceIdentifyRequestSerialization() {
        val request = DeviceIdentifyRequest(
            pushToken = "fcm_token_123",
            platform = "android",
            deviceName = "Pixel 8"
        )
        val serialized = json.encodeToString(DeviceIdentifyRequest.serializer(), request)
        assertTrue(serialized.contains("\"push_token\":\"fcm_token_123\""))
        assertTrue(serialized.contains("\"platform\":\"android\""))
        assertTrue(serialized.contains("\"device_name\":\"Pixel 8\""))
    }

    @Test
    fun deviceLinkRequestSerialization() {
        val request = DeviceLinkRequest(
            appToken = "apt_1234567890abcdef",
            pushToken = "fcm_token_123",
            platform = "android",
            deviceName = "Pixel 8"
        )
        val serialized = json.encodeToString(DeviceLinkRequest.serializer(), request)
        assertTrue(serialized.contains("\"app_token\":\"apt_1234567890abcdef\""))
        assertTrue(serialized.contains("\"push_token\":\"fcm_token_123\""))
        assertTrue(serialized.contains("\"platform\":\"android\""))
        assertTrue(serialized.contains("\"device_name\":\"Pixel 8\""))
    }

    @Test
    fun deviceIdentifyResponseDeserialization() {
        val raw = """
            {
                "status": "ok",
                "device_id": 7,
                "device_created": false,
                "linked_applications": [
                    {
                        "id": 3,
                        "name": "Production alerts",
                        "description": "Backend alerts",
                        "is_active": true,
                        "linked_at": "2026-04-30T10:15:00Z"
                    }
                ]
            }
        """.trimIndent()
        val result = json.decodeFromString<DeviceIdentifyResponse>(raw)
        assertEquals("ok", result.status)
        assertEquals(7, result.deviceId)
        assertEquals(false, result.deviceCreated)
        assertEquals(1, result.linkedApplications.size)
        assertEquals("Production alerts", result.linkedApplications[0].name)
    }

    @Test
    fun deviceLinkResponseDeserialization() {
        val raw = """
            {
                "status": "ok",
                "device_id": 7,
                "device_created": true,
                "link_created": true,
                "application_id": 3
            }
        """.trimIndent()
        val result = json.decodeFromString<DeviceLinkResponse>(raw)
        assertEquals("ok", result.status)
        assertEquals(7, result.deviceId)
        assertEquals(true, result.deviceCreated)
        assertEquals(3, result.applicationId)
    }

    @Test
    fun loginResponseIgnoresUnknownFields() {
        val raw = """
            {
                "access": "a",
                "refresh": "r",
                "user": {"id": 1, "email": "e@e.com", "username": "u", "unknown_field": true}
            }
        """.trimIndent()
        val result = json.decodeFromString<LoginResponse>(raw)
        assertEquals("a", result.access)
    }

    @Test
    fun apiErrorFormattingExtractsUsefulMessages() {
        val raw = """
            {
                "errors": {
                    "email": ["This field is required."],
                    "password": ["Too short."]
                },
                "code": "validation_error",
                "detail": "Invalid input."
            }
        """.trimIndent()

        val result = formatApiErrorBody(raw)

        assertTrue(result.contains("Invalid input."))
        assertTrue(result.contains("validation_error"))
        assertTrue(result.contains("email: This field is required."))
        assertTrue(result.contains("password: Too short."))
    }
}
