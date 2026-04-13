package com.foxugly.pustit_app.data.storage

expect class TokenStorage {
    fun getAccessToken(): String?
    fun setAccessToken(token: String?)
    fun getRefreshToken(): String?
    fun setRefreshToken(token: String?)
    fun getAppToken(): String?
    fun setAppToken(token: String?)
    fun clearAuthTokens()
}
