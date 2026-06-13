package com.foxugly.pushit_app.data.storage

expect class TokenStorage {
    fun getAccessToken(): String?
    fun setAccessToken(token: String?)
    fun getRefreshToken(): String?
    fun setRefreshToken(token: String?)
    fun getAppToken(): String?
    fun setAppToken(token: String?)
    fun clearAuthTokens()
    // UI language preference (lowercase ISO code, e.g. "fr"). Local-only: the
    // mobile API has no language PATCH endpoint, so this is never sent server-side.
    fun getLanguage(): String?
    fun setLanguage(code: String?)
}
