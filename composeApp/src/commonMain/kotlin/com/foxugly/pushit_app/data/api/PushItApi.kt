package com.foxugly.pushit_app.data.api

import com.foxugly.pushit_app.diagnostics.AppLogger
import com.foxugly.pushit_app.data.storage.TokenStorage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class PushItApi(
    private val tokenStorage: TokenStorage,
    baseUrl: String = "http://10.0.2.2:8000/api/v1/",
) {
    private val tag = "PushIT/Api"

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
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    AppLogger.debug(tag, message)
                }
            }
            level = LogLevel.INFO
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
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
        val response = client.post("auth/logout/") { setBody(RefreshRequest(refreshToken)) }
        logResponse("logout", response)
        if (!response.status.isSuccess()) {
            throw response.toApiException("logout")
        }
    }

    suspend fun getMe(): Result<UserProfile> = apiCall {
        client.get("auth/me/")
    }

    // --- Device ---
    suspend fun identifyDevice(request: DeviceIdentifyRequest): Result<DeviceIdentifyResponse> = apiCall {
        client.post("devices/identify/") {
            setBody(request)
        }
    }

    suspend fun linkDevice(request: DeviceLinkRequest): Result<DeviceLinkResponse> = apiCall {
        client.post("devices/link/") {
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

    // --- Helpers ---
    private suspend inline fun <reified T> apiCall(
        crossinline block: suspend () -> HttpResponse,
    ): Result<T> = runCatching {
        val response = block()
        logResponse(T::class.simpleName ?: "unknown", response)
        if (!response.status.isSuccess() && response.status != HttpStatusCode.Unauthorized) {
            throw response.toApiException(T::class.simpleName ?: "unknown")
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            AppLogger.warn(tag, "Unauthorized response received, attempting token refresh")
            val retried = authInterceptor.handleUnauthorized(
                client,
                HttpRequestBuilder().apply { url(response.request.url.toString()) },
                response,
            )
            logResponse("retry ${T::class.simpleName ?: "unknown"}", retried)
            if (!retried.status.isSuccess()) {
                throw retried.toApiException("retry ${T::class.simpleName ?: "unknown"}")
            }
            retried.body<T>()
        } else {
            response.body<T>()
        }
    }.onFailure {
        AppLogger.error(tag, "API call failed: ${it.message}", it)
    }

    private fun logResponse(operation: String, response: HttpResponse) {
        AppLogger.info(
            tag,
            "$operation ${response.request.method.value} ${response.request.url.encodedPath} -> ${response.status.value}",
        )
    }

    private suspend fun HttpResponse.toApiException(operation: String): ApiException {
        val errorBody = runCatching { bodyAsText().take(500) }.getOrDefault("")
        return ApiException(
            statusCode = status.value,
            operation = operation,
            responseBody = errorBody,
        )
    }
}

class ApiException(
    val statusCode: Int,
    operation: String,
    responseBody: String,
) : Exception(
    buildString {
        append("API $operation failed with HTTP $statusCode")
        if (responseBody.isNotBlank()) {
            append(": ")
            append(responseBody)
        }
    }
)
