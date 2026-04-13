package com.foxugly.pustit_app.data.api

import com.foxugly.pustit_app.data.storage.TokenStorage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class PushItApi(
    private val tokenStorage: TokenStorage,
    baseUrl: String = "http://10.0.2.2:8000/api/v1/",
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val authInterceptor = AuthInterceptor(tokenStorage, json)

    val client = HttpClient {
        install(ContentNegotiation) {
            json(this@PushItApi.json)
        }
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
        install(authInterceptor.plugin)
    }

    var onAuthFailure: (() -> Unit)?
        get() = authInterceptor.onAuthFailure
        set(value) { authInterceptor.onAuthFailure = value }

    // --- Auth ---
    suspend fun login(request: LoginRequest): Result<LoginResponse> = apiCall {
        client.post("auth/login/") { setBody(request) }
    }

    suspend fun register(request: RegisterRequest): Result<UserProfile> = apiCall {
        client.post("auth/register/") { setBody(request) }
    }

    suspend fun refresh(request: RefreshRequest): Result<RefreshResponse> = apiCall {
        client.post("auth/refresh/") { setBody(request) }
    }

    suspend fun logout(refreshToken: String): Result<Unit> = runCatching {
        client.post("auth/logout/") { setBody(RefreshRequest(refreshToken)) }
    }

    suspend fun getMe(): Result<UserProfile> = apiCall {
        client.get("auth/me/")
    }

    // --- Device ---
    suspend fun linkDevice(appToken: String, request: DeviceLinkRequest): Result<DeviceLinkResponse> = apiCall {
        client.post("devices/link/") {
            header("X-App-Token", appToken)
            setBody(request)
        }
    }

    // --- Notifications ---
    suspend fun getNotifications(page: Int = 1): Result<NotificationListResponse> = apiCall {
        client.get("notifications/") { parameter("page", page) }
    }

    suspend fun getNotification(id: Int): Result<Notification> = apiCall {
        client.get("notifications/$id/")
    }

    suspend fun getNotificationStats(): Result<NotificationStats> = apiCall {
        client.get("notifications/stats/")
    }

    // --- Helpers ---
    private suspend inline fun <reified T> apiCall(
        crossinline block: suspend () -> HttpResponse,
    ): Result<T> = runCatching {
        val response = block()
        if (response.status == HttpStatusCode.Unauthorized) {
            val retried = authInterceptor.handleUnauthorized(
                client,
                HttpRequestBuilder().apply { url(response.request.url.toString()) },
                response,
            )
            retried.body<T>()
        } else {
            response.body<T>()
        }
    }
}
