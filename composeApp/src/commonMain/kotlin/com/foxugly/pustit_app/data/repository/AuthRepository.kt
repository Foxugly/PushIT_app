package com.foxugly.pustit_app.data.repository

import com.foxugly.pustit_app.data.api.*
import com.foxugly.pustit_app.data.storage.TokenStorage

class AuthRepository(
    private val api: PushItApi,
    private val tokenStorage: TokenStorage,
) {
    suspend fun login(email: String, password: String): Result<UserProfile> {
        return api.login(LoginRequest(email, password)).map { response ->
            tokenStorage.setAccessToken(response.access)
            tokenStorage.setRefreshToken(response.refresh)
            response.user
        }
    }

    suspend fun register(email: String, username: String, password: String): Result<UserProfile> {
        val registerResult = api.register(RegisterRequest(email, username, password))
        if (registerResult.isFailure) return registerResult
        return login(email, password)
    }

    suspend fun logout(): Result<Unit> {
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
        val result = api.refresh(RefreshRequest(refreshToken))
        return result.fold(
            onSuccess = { response ->
                tokenStorage.setAccessToken(response.access)
                true
            },
            onFailure = {
                tokenStorage.clearAuthTokens()
                false
            }
        )
    }
}
