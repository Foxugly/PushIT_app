package com.foxugly.pushit_app.platform

import com.foxugly.pushit_app.diagnostics.AppLogger
import com.google.firebase.messaging.FirebaseMessaging

actual class FcmTokenProvider {
    private var currentToken: String? = null
    private var tokenCallback: ((String) -> Unit)? = null

    actual fun getCurrentToken(): String? {
        if (currentToken == null) {
            AppLogger.warn(TAG, "FCM token requested before it is available")
        }
        return currentToken
    }

    actual fun observeTokenChanges(onNewToken: (String) -> Unit) {
        tokenCallback = onNewToken
        currentToken?.let(onNewToken)
    }

    fun refreshToken() {
        AppLogger.info(TAG, "Requesting current FCM token")
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                AppLogger.info(TAG, "Current FCM token loaded")
                updateToken(token)
            }
            .addOnFailureListener { throwable ->
                AppLogger.error(TAG, "Failed to load current FCM token", throwable)
            }
    }

    fun updateToken(token: String) {
        AppLogger.info(TAG, "FCM token updated")
        currentToken = token
        tokenCallback?.invoke(token)
    }

    companion object {
        private const val TAG = "PushIT/FcmToken"
        val instance = FcmTokenProvider()
    }
}
