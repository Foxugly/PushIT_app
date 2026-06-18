package com.foxugly.pushit_app

import com.foxugly.pushit_app.data.storage.InboxStateStore
import com.foxugly.pushit_app.data.storage.TokenStore
import com.foxugly.pushit_app.platform.FcmTokenSource

/** In-memory [TokenStore] for tests. */
internal class FakeTokenStore(
    private var access: String? = null,
    private var refresh: String? = null,
    private var app: String? = null,
) : TokenStore {
    var cleared = false
        private set

    override fun getAccessToken() = access
    override fun setAccessToken(token: String?) { access = token }
    override fun getRefreshToken() = refresh
    override fun setRefreshToken(token: String?) { refresh = token }
    override fun getAppToken() = app
    override fun setAppToken(token: String?) { app = token }
    override fun clearAuthTokens() { access = null; refresh = null; cleared = true }
}

/** In-memory [InboxStateStore] for tests. */
internal class FakeInboxStateStore(private var state: String? = null) : InboxStateStore {
    override fun getNotificationState(): String? = state
    override fun setNotificationState(json: String?) { state = json }
}

/** In-memory [FcmTokenSource] for tests. */
internal class FakeFcmTokenSource(private var token: String? = null) : FcmTokenSource {
    private var callback: ((String) -> Unit)? = null
    var observerDetached = false
        private set

    override fun getCurrentToken(): String? = token
    override fun observeTokenChanges(onNewToken: (String) -> Unit) { callback = onNewToken }
    override fun stopObservingTokenChanges() { callback = null; observerDetached = true }

    /** Simulate a token rotation pushed by the platform. */
    fun emit(newToken: String) {
        token = newToken
        callback?.invoke(newToken)
    }
}
