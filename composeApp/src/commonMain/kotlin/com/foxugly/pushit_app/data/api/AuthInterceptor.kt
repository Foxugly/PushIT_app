package com.foxugly.pushit_app.data.api

import com.foxugly.pushit_app.data.storage.TokenStore
import com.foxugly.pushit_app.diagnostics.AppLogger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class AuthInterceptor(
    private val tokenStorage: TokenStore,
    private val json: Json,
    var onAuthFailure: (() -> Unit)? = null,
) {
    private val tag = "PushIT/AuthInterceptor"
    private val refreshMutex = Mutex()

    val plugin = createClientPlugin("AuthInterceptor") {
        onRequest { request, _ ->
            val token = tokenStorage.getAccessToken()
            if (token != null &&
                !request.url.encodedPath.contains("/auth/login") &&
                !request.url.encodedPath.contains("/auth/register") &&
                !request.url.encodedPath.contains("/auth/refresh")
            ) {
                request.headers.append(HttpHeaders.Authorization, "Bearer $token")
                AppLogger.debug(tag, "Authorization header attached to ${request.url.encodedPath}")
            }
        }
    }

    /**
     * Refresh the access token after a 401, then signal the caller to REPLAY its
     * original request (the caller re-runs its own request lambda, so the verb,
     * body and headers are preserved — the interceptor re-attaches the fresh
     * token via [plugin]). Returns true if a usable access token is now in place.
     *
     * [staleAccessToken] is the token that was on the failed request. Under the
     * mutex we first check whether a concurrent caller already refreshed (current
     * token differs from the stale one): if so we skip a redundant — and with
     * refresh-token rotation, failure-prone — second `auth/refresh/` round-trip.
     */
    suspend fun refreshIfNeeded(client: HttpClient, staleAccessToken: String?): Boolean =
        refreshMutex.withLock {
            val current = tokenStorage.getAccessToken()
            if (current != null && current != staleAccessToken) {
                AppLogger.info(tag, "Access token already refreshed by a concurrent call; reusing it")
                return@withLock true
            }
            val refreshToken = tokenStorage.getRefreshToken() ?: run {
                AppLogger.warn(tag, "Unauthorized response and no refresh token available")
                onAuthFailure?.invoke()
                return@withLock false
            }
            try {
                AppLogger.info(tag, "Refreshing access token after HTTP 401")
                val refreshResponse = client.post("auth/refresh/") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(refreshToken))
                }
                if (refreshResponse.status == HttpStatusCode.OK) {
                    val body = refreshResponse.body<RefreshResponse>()
                    tokenStorage.setAccessToken(body.access)
                    // The backend rotates + blacklists refresh tokens, so it returns a new
                    // one here. Persist it, otherwise the next refresh sends the now-blacklisted
                    // token, fails, and ejects the user right after login.
                    body.refresh?.let { tokenStorage.setRefreshToken(it) }
                    AppLogger.info(tag, "Access token refresh succeeded")
                    true
                } else {
                    AppLogger.warn(tag, "Access token refresh failed with HTTP ${refreshResponse.status.value}")
                    tokenStorage.clearAuthTokens()
                    onAuthFailure?.invoke()
                    false
                }
            } catch (e: Exception) {
                AppLogger.error(tag, "Access token refresh threw an exception", e)
                tokenStorage.clearAuthTokens()
                onAuthFailure?.invoke()
                false
            }
        }
}
