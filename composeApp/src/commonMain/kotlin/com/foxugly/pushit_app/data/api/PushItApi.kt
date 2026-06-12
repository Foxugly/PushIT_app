package com.foxugly.pushit_app.data.api

import com.foxugly.pushit_app.diagnostics.AppLogger
import com.foxugly.pushit_app.data.storage.TokenStore
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.errors.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class PushItApi(
    private val tokenStorage: TokenStore,
    baseUrl: String = "https://pushit-api.foxugly.com/api/v1/",
    enableLogging: Boolean = false,
    // Tests inject a MockEngine here; production passes null (default engine).
    engine: HttpClientEngine? = null,
) : AutoCloseable {
    private val tag = "PushIT/Api"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        // Omit null properties on encode (e.g. an absent turnstile_token) so we
        // never send `"field": null` to DRF, which rejects null on non-null fields.
        explicitNulls = false
    }

    private val authInterceptor = AuthInterceptor(tokenStorage, json)

    private val clientConfig: HttpClientConfig<*>.() -> Unit = {
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
            // Off in release: even at INFO Ktor only logs request/response lines,
            // but production builds should not emit endpoint traffic to the log.
            level = if (enableLogging) LogLevel.INFO else LogLevel.NONE
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }
        install(authInterceptor.plugin)
    }

    val client = if (engine != null) HttpClient(engine, clientConfig) else HttpClient(clientConfig)

    override fun close() {
        client.close()
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

    suspend fun unlinkDevice(request: DeviceUnlinkRequest): Result<DeviceUnlinkResponse> = apiCall {
        client.post("devices/unlink/") {
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
    ): Result<T> = runCatching<T> {
        val name = T::class.simpleName ?: "unknown"
        // Token on the request we're about to make — passed to the refresh guard
        // so concurrent 401s don't each re-POST auth/refresh/.
        val staleAccessToken = tokenStorage.getAccessToken()
        val response = block()
        logResponse(name, response)
        if (response.status == HttpStatusCode.Unauthorized) {
            AppLogger.warn(tag, "Unauthorized response received, attempting token refresh")
            if (!authInterceptor.refreshIfNeeded(client, staleAccessToken)) {
                throw response.toApiException("auth $name")
            }
            // Replay the ORIGINAL request: same verb + body, fresh token attached
            // by the AuthInterceptor's onRequest. (The old code rebuilt a bare GET
            // with no body, breaking every POST that hit a 401.)
            val retried = block()
            logResponse("retry $name", retried)
            if (!retried.status.isSuccess()) {
                throw retried.toApiException("retry $name")
            }
            retried.decodeBody<T>(name, json)
        } else {
            if (!response.status.isSuccess()) {
                throw response.toApiException(name)
            }
            response.decodeBody<T>(name, json)
        }
    }.recoverCatching { throwable ->
        // Surface transport failures as NetworkException so the UI can tell
        // "offline / timed out" apart from an HTTP or decoding error. Re-throwing
        // keeps the Result a failure (recoverCatching wraps the thrown value).
        throw when (throwable) {
            is HttpRequestTimeoutException,
            is ConnectTimeoutException,
            is SocketTimeoutException -> NetworkException("The request timed out.", throwable)
            is IOException -> NetworkException("Could not reach the server.", throwable)
            else -> throwable
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
        val errorBody = runCatching { bodyAsText() }.getOrDefault("")
        return ApiException(
            statusCode = status.value,
            operation = operation,
            responseBody = formatApiErrorBody(errorBody),
        )
    }
}

private suspend inline fun <reified T> HttpResponse.decodeBody(operation: String, json: Json): T {
    val rawBody = bodyAsText()
    // A 204 / empty body for a Unit-returning call is success, not a decode error.
    if (rawBody.isBlank() && T::class == Unit::class) {
        @Suppress("UNCHECKED_CAST")
        return Unit as T
    }
    return try {
        json.decodeFromString<T>(rawBody)
    } catch (cause: SerializationException) {
        throw ResponseDecodingException(
            operation = operation,
            statusCode = status.value,
            responseBody = rawBody.take(500),
            cause = cause,
        )
    }
}

internal fun formatApiErrorBody(rawBody: String): String {
    if (rawBody.isBlank()) return ""

    val parsed = runCatching { Json.parseToJsonElement(rawBody) }.getOrNull()
    if (parsed == null) return rawBody.take(500)

    val messages = linkedSetOf<String>()
    collectApiMessages(parsed, messages)
    return if (messages.isEmpty()) rawBody.take(500) else messages.joinToString(" | ").take(500)
}

private fun collectApiMessages(element: JsonElement, messages: MutableSet<String>) {
    when (element) {
        is JsonObject -> {
            element["detail"]?.let { collectApiMessages(it, messages) }
            element["code"]?.let { collectApiMessages(it, messages) }
            element["errors"]?.let { collectApiMessages(it, messages) }
            element.forEach { (key, value) ->
                if (key !in setOf("detail", "code", "errors")) {
                    collectNamedMessages(key, value, messages)
                }
            }
        }
        is JsonArray -> element.forEach { collectApiMessages(it, messages) }
        is JsonPrimitive -> element.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?.let(messages::add)
    }
}

private fun collectNamedMessages(name: String, element: JsonElement, messages: MutableSet<String>) {
    when (element) {
        is JsonPrimitive -> element.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?.let { messages.add("$name: $it") }
        is JsonArray -> {
            val values = element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
            if (values.isNotEmpty()) {
                messages.add("$name: ${values.joinToString(", ")}")
            } else {
                element.forEach { collectApiMessages(it, messages) }
            }
        }
        is JsonObject -> element.forEach { (childName, childValue) ->
            collectNamedMessages("$name.$childName", childValue, messages)
        }
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

/** A transport-level failure (offline, DNS, timeout) — distinct from an HTTP
 * status error ([ApiException]) so the UI can show "check your connection". */
class NetworkException(
    message: String,
    cause: Throwable,
) : Exception(message, cause)

class ResponseDecodingException(
    val statusCode: Int,
    operation: String,
    responseBody: String,
    cause: Throwable,
) : Exception(
    buildString {
        append("API ")
        append(operation)
        append(" returned an unexpected response")
        if (responseBody.isNotBlank()) {
            append(": ")
            append(responseBody)
        }
    },
    cause,
)
