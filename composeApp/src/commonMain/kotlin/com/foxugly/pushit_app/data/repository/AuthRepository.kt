package com.foxugly.pushit_app.data.repository

import com.foxugly.pushit_app.data.api.*
import com.foxugly.pushit_app.diagnostics.AppLogger
import com.foxugly.pushit_app.data.storage.TokenStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AuthRepository(
    private val api: PushItApi,
    private val tokenStorage: TokenStore,
) {
    private val tag = "PushIT/AuthRepository"

    suspend fun login(email: String, password: String): Result<UserProfile> {
        // Don't log the email — it's PII and would land in Logcat.
        AppLogger.info(tag, "Login requested")
        return api.login(LoginRequest(email, password)).map { response ->
            tokenStorage.setAccessToken(response.access)
            tokenStorage.setRefreshToken(response.refresh)
            AppLogger.info(tag, "Login succeeded for user=${response.user.id}")
            response.user
        }.onFailure {
            AppLogger.error(tag, "Login failed: ${it.message}", it)
        }
    }

    suspend fun register(email: String, password: String): Result<UserProfile> {
        AppLogger.info(tag, "Register requested")
        val registerResult = api.register(RegisterRequest(email = email, password = password))
        if (registerResult.isFailure) {
            AppLogger.error(tag, "Register failed", registerResult.exceptionOrNull())
            return registerResult
        }
        return login(email, password)
    }

    suspend fun logout(): Result<Unit> {
        AppLogger.info(tag, "Logout requested")
        val refreshToken = tokenStorage.getRefreshToken()
        val result = if (refreshToken != null) {
            api.logout(refreshToken)
        } else {
            Result.success(Unit)
        }
        tokenStorage.clearAuthTokens()
        return result
    }

    suspend fun getCurrentUser(): Result<UserProfile> = api.getMe()

    fun isAuthenticated(): Boolean = tokenStorage.getAccessToken() != null

    /**
     * True when the stored access token's `exp` claim is already in the past.
     * Lets startup route a stale-but-refreshable session straight to the refresh
     * branch instead of letting the first API call eat a 401. Anything we can't
     * decode (no token, malformed JWT, missing/non-numeric `exp`) is treated as
     * NOT expired — the request path's 401→refresh→replay still covers those, so
     * this never makes startup worse than before.
     */
    @OptIn(ExperimentalEncodingApi::class, ExperimentalTime::class)
    fun accessTokenExpired(): Boolean {
        val token = tokenStorage.getAccessToken() ?: return false
        return runCatching {
            val payload = token.split(".").getOrNull(1) ?: return false
            val padded = payload.padEnd((payload.length + 3) / 4 * 4, '=')
            val claims = Base64.UrlSafe.decode(padded).decodeToString()
            val exp = Json.parseToJsonElement(claims).jsonObject["exp"]?.jsonPrimitive?.longOrNull
                ?: return false
            exp < Clock.System.now().epochSeconds
        }.getOrDefault(false)
    }

    fun hasRefreshToken(): Boolean = tokenStorage.getRefreshToken() != null

    suspend fun tryRefresh(): Boolean {
        val refreshToken = tokenStorage.getRefreshToken() ?: return false
        AppLogger.info(tag, "Trying startup token refresh")
        val result = api.refresh(RefreshRequest(refreshToken))
        return result.fold(
            onSuccess = { response ->
                tokenStorage.setAccessToken(response.access)
                AppLogger.info(tag, "Startup token refresh succeeded")
                true
            },
            onFailure = {
                AppLogger.error(tag, "Startup token refresh failed", it)
                tokenStorage.clearAuthTokens()
                false
            }
        )
    }
}
