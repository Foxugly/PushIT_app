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
    fun getEnrolmentCode(): String?
    fun setEnrolmentCode(code: String?)
    fun clearAuthTokens()
    /** The FCM token last registered with the server (identify/link), so an FCM
     * rotation can hand the existing Device over (`previous_push_token`). */
    fun getLastPushToken(): String?
    fun setLastPushToken(token: String?)
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

/**
 * Persistence seam for the "new APK available" banner: the version code the user
 * dismissed, so the banner stays hidden until a NEWER version is published.
 */
interface AppUpdateStateStore {
    fun getDismissedUpdateVersionCode(): Long?
    fun setDismissedUpdateVersionCode(versionCode: Long?)
}

/** Adapts the platform [TokenStorage] to [TokenStore] + [InboxStateStore] +
 * [AppUpdateStateStore]. */
class TokenStorageStore(private val storage: TokenStorage) : TokenStore, InboxStateStore, AppUpdateStateStore {
    override fun getAccessToken(): String? = storage.getAccessToken()
    override fun setAccessToken(token: String?) = storage.setAccessToken(token)
    override fun getRefreshToken(): String? = storage.getRefreshToken()
    override fun setRefreshToken(token: String?) = storage.setRefreshToken(token)
    override fun getEnrolmentCode(): String? = storage.getEnrolmentCode()
    override fun setEnrolmentCode(code: String?) = storage.setEnrolmentCode(code)
    override fun clearAuthTokens() = storage.clearAuthTokens()
    override fun getLastPushToken(): String? = storage.getLastPushToken()
    override fun setLastPushToken(token: String?) = storage.setLastPushToken(token)
    override fun getNotificationState(): String? = storage.getNotificationState()
    override fun setNotificationState(json: String?) = storage.setNotificationState(json)
    override fun getDismissedUpdateVersionCode(): Long? =
        storage.getDismissedUpdateVersionCode()?.toLongOrNull()
    override fun setDismissedUpdateVersionCode(versionCode: Long?) =
        storage.setDismissedUpdateVersionCode(versionCode?.toString())
}
