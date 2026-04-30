package com.foxugly.pushit_app.platform

actual class FcmTokenProvider {
    private var currentToken: String? = null
    private var tokenCallback: ((String) -> Unit)? = null

    actual fun getCurrentToken(): String? = currentToken

    actual fun observeTokenChanges(onNewToken: (String) -> Unit) {
        tokenCallback = onNewToken
    }

    fun updateToken(token: String) {
        currentToken = token
        tokenCallback?.invoke(token)
    }

    companion object {
        val instance = FcmTokenProvider()
    }
}
