package com.foxugly.pushit_app.data.api

import com.foxugly.pushit_app.data.storage.TokenStorage
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
    private val tokenStorage: TokenStorage,
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

    suspend fun handleUnauthorized(
        client: HttpClient,
        originalRequest: HttpRequestBuilder,
        response: HttpResponse,
    ): HttpResponse {
        if (response.status != HttpStatusCode.Unauthorized) return response

        val newAccessToken = refreshMutex.withLock {
            val refreshToken = tokenStorage.getRefreshToken() ?: run {
                AppLogger.warn(tag, "Unauthorized response and no refresh token available")
                onAuthFailure?.invoke()
                return response
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
                    AppLogger.info(tag, "Access token refresh succeeded")
                    body.access
                } else {
                    AppLogger.warn(tag, "Access token refresh failed with HTTP ${refreshResponse.status.value}")
                    tokenStorage.clearAuthTokens()
                    onAuthFailure?.invoke()
                    null
                }
            } catch (e: Exception) {
                AppLogger.error(tag, "Access token refresh threw an exception", e)
                tokenStorage.clearAuthTokens()
                onAuthFailure?.invoke()
                null
            }
        } ?: return response

        return client.request {
            takeFrom(originalRequest)
            headers.remove(HttpHeaders.Authorization)
            headers.append(HttpHeaders.Authorization, "Bearer $newAccessToken")
        }
    }
}
