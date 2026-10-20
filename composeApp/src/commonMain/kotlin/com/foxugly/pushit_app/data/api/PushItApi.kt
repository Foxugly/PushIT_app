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

    private val authInterceptor = AuthInterceptor(tokenStorage, json, Url(baseUrl))

    // Raw response bodies only go into exceptions (and so into logcat) in debug
    // builds: a mis-shaped auth response could otherwise leak JWTs to the log.
    private val includeRawBodies = enableLogging

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
    // A 401 here means wrong credentials, not an expired session: no refresh/replay.
    suspend fun login(request: LoginRequest): Result<LoginResponse> = apiCall(refreshOnUnauthorized = false) {
        client.post("auth/login/") { setBody(request) }
    }

    /**
     * Ask the server to e-mail a fresh confirmation link (login answered 403
     * `email_not_verified`). Anonymous; always 200 whether or not the address
     * matched (anti-enumeration), 429 `throttled` when rate-limited.
     */
    suspend fun resendConfirmationEmail(email: String): Result<Unit> = runCatching {
        val response = client.post("auth/email/resend/") { setBody(EmailResendRequest(email)) }
        logResponse("resendConfirmationEmail", response)
        if (!response.status.isSuccess()) {
            throw response.toApiException("resendConfirmationEmail")
        }
    }.mapTransportErrors()

    // A 401 here is the refresh token itself being refused: the caller decides
    // (AuthRepository.tryRefresh clears the session). Refreshing again from inside
    // apiCall would POST auth/refresh/ a second time with the same dead token.
    suspend fun refresh(request: RefreshRequest): Result<RefreshResponse> = apiCall(refreshOnUnauthorized = false) {
        client.post("auth/refresh/") { setBody(request) }
    }

    /**
     * Blacklist the stored refresh token server-side. `auth/logout/` needs BOTH a
     * valid access token (IsAuthenticated) and the refresh token in the body, so
     * with an expired access token we refresh first and replay — otherwise the
     * refresh token would stay valid on the server after the user signed out.
     * The refresh rotates the refresh token, so each attempt re-reads the CURRENT
     * one from storage. If the refresh token itself is refused, the session is
     * already dead server-side: nothing left to revoke, so that counts as success
     * (and does not fire [onAuthFailure] — we are signing out anyway).
     */
    suspend fun logout(): Result<Unit> = runCatching {
        val staleAccessToken = tokenStorage.getAccessToken()
        val firstRefresh = tokenStorage.getRefreshToken() ?: return@runCatching
        var response = client.post("auth/logout/") { setBody(RefreshRequest(firstRefresh)) }
        logResponse("logout", response)
        if (response.status == HttpStatusCode.Unauthorized) {
            AppLogger.info(tag, "Logout got 401, refreshing the access token before revoking")
            if (!authInterceptor.refreshIfNeeded(client, staleAccessToken, notifyAuthFailure = false)) {
                return@runCatching
            }
            val rotated = tokenStorage.getRefreshToken() ?: return@runCatching
            response = client.post("auth/logout/") { setBody(RefreshRequest(rotated)) }
            logResponse("retry logout", response)
        }
        if (!response.status.isSuccess()) {
            throw response.toApiException("logout")
        }
    }.mapTransportErrors()

    suspend fun getMe(): Result<UserProfile> = apiCall {
        client.get("auth/me/")
    }

    /**
     * Permanently erase the signed-in account (GDPR art. 17 / store policies).
     * 204 on success. 400 `current_password_invalid` (wrong password) or
     * `validation_error` (missing) — see [ApiException.errorCode]. The server also
     * deletes the user's devices and the applications they own, with their data.
     */
    suspend fun deleteAccount(currentPassword: String): Result<Unit> = apiCall {
        client.delete("auth/me/") { setBody(AccountDeleteRequest(currentPassword)) }
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

    // Per-app unlink by application id (no app token needed) — for the inbox.
    suspend fun unlinkDeviceApp(request: DeviceUnlinkByApplicationRequest): Result<DeviceUnlinkResponse> = apiCall {
        client.post("devices/unlink-app/") {
            setBody(request)
        }
    }

    // --- Notifications ---
    // /notifications/ (JWT) returns a bare array — the web SPA and this client
    // both consume it un-paginated (the endpoint sets pagination_class=None).
    suspend fun getNotifications(): Result<List<Notification>> = apiCall {
        client.get("notifications/")
    }

    // Recipient inbox: notifications delivered to THIS device (resolved by its
    // FCM push token), across every app it's linked to. Bare array.
    // sentSince (ISO 8601) bounds the result by send date; null = full history.
    suspend fun getDeviceNotifications(
        pushToken: String,
        sentSince: String? = null,
    ): Result<List<Notification>> = apiCall {
        client.get("notifications/device/") {
            parameter("push_token", pushToken)
            if (sentSince != null) parameter("sent_since", sentSince)
        }
    }

    suspend fun getNotification(id: Int): Result<Notification> = apiCall {
        client.get("notifications/$id/")
    }

    // Recipient receipt: the user opened this notification in the app. Stamps
    // opened_at (and delivered_at) server-side for this device. Idempotent.
    suspend fun confirmNotificationOpened(
        id: Int,
        pushToken: String,
    ): Result<NotificationOpenedReceiptResponse> = apiCall {
        client.post("notifications/$id/opened/") {
            setBody(NotificationOpenedReceiptRequest(pushToken))
        }
    }

    // Raw bytes of an (absolute) image URL — used to load app logos. Not JSON, so
    // it can't go through apiCall's decode path, but it still must honour the same
    // 401 → refresh → replay contract as every authenticated request (logos are
    // served behind auth). We run the request, and on a 401 refresh once and retry
    // the GET with the fresh token (re-attached by the AuthInterceptor) before
    // giving up. A non-recoverable failure surfaces as a failed Result.
    suspend fun getImageBytes(url: String): Result<ByteArray> = runCatching {
        val staleAccessToken = tokenStorage.getAccessToken()
        var response = client.get(url)
        if (response.status == HttpStatusCode.Unauthorized) {
            AppLogger.warn(tag, "Image GET got 401, attempting token refresh")
            if (!authInterceptor.refreshIfNeeded(client, staleAccessToken)) {
                throw SessionExpiredException(response.toApiException("getImageBytes"))
            }
            response = client.get(url)
        }
        if (!response.status.isSuccess()) {
            throw response.toApiException("getImageBytes")
        }
        response.readRawBytes()
    }

    // --- Helpers ---
    // IMPORTANT: every authenticated request must go through [apiCall] (or, for the
    // few non-JSON cases like getImageBytes, replicate its 401 → refreshIfNeeded →
    // replay contract). Bypassing it means a recoverable expired session is treated
    // as a hard failure instead of transparently refreshing.
    // [refreshOnUnauthorized] = false for the anonymous auth endpoints (login,
    // refresh) whose 401 is an answer, not an expired access token.
    private suspend inline fun <reified T> apiCall(
        refreshOnUnauthorized: Boolean = true,
        crossinline block: suspend () -> HttpResponse,
    ): Result<T> = runCatching<T> {
        val name = T::class.simpleName ?: "unknown"
        // Token on the request we're about to make — passed to the refresh guard
        // so concurrent 401s don't each re-POST auth/refresh/.
        val staleAccessToken = tokenStorage.getAccessToken()
        val response = block()
        logResponse(name, response)
        if (refreshOnUnauthorized && response.status == HttpStatusCode.Unauthorized) {
            // An enrolment code refused on devices/link/ is NOT a session problem:
            // refreshing (and rotating) the JWT pair would not help and the replay
            // would fail the same way. The server sends 403 for these now, but older
            // servers answered 401 with the same `app_token_*` codes.
            val unauthorized = response.toApiException("auth $name")
            if (unauthorized.isEnrolmentCodeRejection()) throw unauthorized
            AppLogger.warn(tag, "Unauthorized response received, attempting token refresh")
            if (!authInterceptor.refreshIfNeeded(client, staleAccessToken)) {
                // The refresh token was refused (or there is none): the session is
                // over, onAuthFailure already sent the user to Login. Surface a
                // dedicated exception so screens stay silent instead of showing the
                // server's raw `token_not_valid` body.
                throw SessionExpiredException(unauthorized)
            }
            // Replay the ORIGINAL request: same verb + body, fresh token attached
            // by the AuthInterceptor's onRequest. (The old code rebuilt a bare GET
            // with no body, breaking every POST that hit a 401.)
            val retried = block()
            logResponse("retry $name", retried)
            if (!retried.status.isSuccess()) {
                throw retried.toApiException("retry $name")
            }
            retried.decodeBody<T>(name, json, includeRawBodies)
        } else {
            if (!response.status.isSuccess()) {
                throw response.toApiException(name)
            }
            response.decodeBody<T>(name, json, includeRawBodies)
        }
    }.mapTransportErrors().onFailure {
        AppLogger.error(tag, "API call failed: ${it.message}", it)
    }

    // Surface transport failures as NetworkException so the UI can tell
    // "offline / timed out" apart from an HTTP or decoding error. Re-throwing
    // keeps the Result a failure (recoverCatching wraps the thrown value).
    private fun <T> Result<T>.mapTransportErrors(): Result<T> = recoverCatching { throwable ->
        throw when (throwable) {
            is HttpRequestTimeoutException,
            is ConnectTimeoutException,
            is SocketTimeoutException -> NetworkException(NetworkErrorKind.TIMEOUT, "The request timed out.", throwable)
            is IOException -> NetworkException(NetworkErrorKind.OFFLINE, "Could not reach the server.", throwable)
            else -> throwable
        }
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
            errorCode = extractApiErrorCode(errorBody),
        )
    }
}

private suspend inline fun <reified T> HttpResponse.decodeBody(
    operation: String,
    json: Json,
    includeRawBody: Boolean,
): T {
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
            responseBody = if (includeRawBody) rawBody.take(500) else "",
            cause = cause,
        )
    }
}

/** The machine-readable `code` of a fleet error body (`{"code": "...", "detail": ...}`), if any. */
internal fun extractApiErrorCode(rawBody: String): String? {
    if (rawBody.isBlank()) return null
    val parsed = runCatching { Json.parseToJsonElement(rawBody) }.getOrNull() as? JsonObject ?: return null
    return (parsed["code"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
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
    /** The server's machine-readable error code (e.g. `current_password_invalid`), if any. */
    val errorCode: String? = null,
) : Exception(
    buildString {
        append("API $operation failed with HTTP $statusCode")
        if (responseBody.isNotBlank()) {
            append(": ")
            append(responseBody)
        }
    }
)

/**
 * True when the server refused an enrolment code on `devices/link/` (or
 * `devices/unlink/`): an `app_token_*` error code with HTTP 403 (current
 * server) or 401 (servers before the 403 switch). Never a JWT/session failure,
 * so it must not trigger a token refresh, and a stored code that gets it is dead.
 */
fun Throwable.isEnrolmentCodeRejection(): Boolean =
    this is ApiException &&
        (statusCode == 401 || statusCode == 403) &&
        errorCode?.startsWith("app_token_") == true

/** A transport-level failure (offline, DNS, timeout) — distinct from an HTTP
 * status error ([ApiException]) so the UI can show "check your connection". */
/** Transport failure kind, so the UI can show a localized message per case. */
enum class NetworkErrorKind { OFFLINE, TIMEOUT }

/**
 * An authenticated call got a 401 and the refresh token was then refused (401/403)
 * or missing: tokens are cleared and [PushItApi.onAuthFailure] has fired. Not an
 * error to show — the UI goes back to Login (at most a discreet "session expired"
 * note). Network errors and 5xx on refresh never produce this: they keep the session.
 */
class SessionExpiredException(cause: ApiException) :
    Exception("Session expired, sign in again.", cause)

class NetworkException(
    val kind: NetworkErrorKind,
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
