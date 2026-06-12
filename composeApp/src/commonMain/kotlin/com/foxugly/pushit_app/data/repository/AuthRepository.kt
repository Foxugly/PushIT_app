package com.foxugly.pushit_app.data.repository

import com.foxugly.pushit_app.data.api.*
import com.foxugly.pushit_app.diagnostics.AppLogger
import com.foxugly.pushit_app.data.storage.TokenStore

class AuthRepository(
    private val api: PushItApi,
    private val tokenStorage: TokenStore,
) {
    private val tag = "PushIT/AuthRepository"

    suspend fun login(email: String, password: String): Result<UserProfile> {
        AppLogger.info(tag, "Login requested for email=$email")
        return api.login(LoginRequest(email, password)).map { response ->
            tokenStorage.setAccessToken(response.access)
            tokenStorage.setRefreshToken(response.refresh)
            AppLogger.info(tag, "Login succeeded for user=${response.user.id}")
            response.user
        }.onFailure {
            AppLogger.error(tag, "Login failed for email=$email: ${it.message}", it)
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
