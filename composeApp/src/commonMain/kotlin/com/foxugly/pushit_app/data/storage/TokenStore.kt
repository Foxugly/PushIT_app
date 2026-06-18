package com.foxugly.pushit_app.data.storage

/**
 * Token persistence seam used by the data layer (PushItApi / AuthInterceptor /
 * repositories). [TokenStorage] is an `expect class` and so can't be faked in
 * commonTest; depending on this interface — with [TokenStorageStore] adapting
 * the real platform storage — lets tests inject a fake without touching the
 * expect/actual declarations.
 */
interface TokenStore {
    fun getAccessToken(): String?
    fun setAccessToken(token: String?)
    fun getRefreshToken(): String?
    fun setRefreshToken(token: String?)
    fun getAppToken(): String?
    fun setAppToken(token: String?)
    fun clearAuthTokens()
}

/**
 * Persistence seam for the inbox's LOCAL read/dismissed state (a JSON blob).
 * Separated from [TokenStore] so [com.foxugly.pushit_app.ui.notifications.InboxStore]
 * can depend on an interface — fakeable in commonTest — instead of the
 * `expect class` [TokenStorage], which can't be instantiated there.
 */
interface InboxStateStore {
    fun getNotificationState(): String?
    fun setNotificationState(json: String?)
}

/** Adapts the platform [TokenStorage] to [TokenStore] + [InboxStateStore] (pure
 * commonMain — no expect/actual change, so no iOS-side risk). */
class TokenStorageStore(private val storage: TokenStorage) : TokenStore, InboxStateStore {
    override fun getAccessToken(): String? = storage.getAccessToken()
    override fun setAccessToken(token: String?) = storage.setAccessToken(token)
    override fun getRefreshToken(): String? = storage.getRefreshToken()
    override fun setRefreshToken(token: String?) = storage.setRefreshToken(token)
    override fun getAppToken(): String? = storage.getAppToken()
    override fun setAppToken(token: String?) = storage.setAppToken(token)
    override fun clearAuthTokens() = storage.clearAuthTokens()
    override fun getNotificationState(): String? = storage.getNotificationState()
    override fun setNotificationState(json: String?) = storage.setNotificationState(json)
}
