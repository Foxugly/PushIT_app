package com.foxugly.pustit_app.data.api

import com.foxugly.pustit_app.data.storage.TokenStorage
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
                onAuthFailure?.invoke()
                return response
            }
            try {
                val refreshResponse = client.post("auth/refresh/") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(refreshToken))
                }
                if (refreshResponse.status == HttpStatusCode.OK) {
                    val body = refreshResponse.body<RefreshResponse>()
                    tokenStorage.setAccessToken(body.access)
                    body.access
                } else {
                    tokenStorage.clearAuthTokens()
                    onAuthFailure?.invoke()
                    null
                }
            } catch (e: Exception) {
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
