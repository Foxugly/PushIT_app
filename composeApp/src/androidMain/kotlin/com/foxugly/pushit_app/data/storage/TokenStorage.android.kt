package com.foxugly.pushit_app.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.foxugly.pushit_app.diagnostics.AppLogger

actual class TokenStorage(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "pushit_secure_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    actual fun getAccessToken(): String? = readToken(KEY_ACCESS)
    actual fun setAccessToken(token: String?) {
        writeToken(KEY_ACCESS, token)
    }

    actual fun getRefreshToken(): String? = readToken(KEY_REFRESH)
    actual fun setRefreshToken(token: String?) {
        writeToken(KEY_REFRESH, token)
    }

    actual fun getAppToken(): String? = readToken(KEY_APP_TOKEN)
    actual fun setAppToken(token: String?) {
        writeToken(KEY_APP_TOKEN, token)
    }

    actual fun getLanguage(): String? = readToken(KEY_LANGUAGE)
    actual fun setLanguage(code: String?) {
        writeToken(KEY_LANGUAGE, code)
    }

    actual fun getNotificationState(): String? = readToken(KEY_NOTIF_STATE)
    actual fun setNotificationState(json: String?) {
        writeToken(KEY_NOTIF_STATE, json)
    }

    actual fun clearAuthTokens() {
        runCatching {
            prefs.edit()
                .remove(KEY_ACCESS)
                .remove(KEY_REFRESH)
                .apply()
        }.onSuccess {
            AppLogger.info(TAG, "Auth tokens cleared")
        }.onFailure {
            AppLogger.error(TAG, "Failed to clear auth tokens", it)
        }
    }

    private fun readToken(key: String): String? {
        return runCatching {
            prefs.getString(key, null)
        }.onFailure {
            AppLogger.error(TAG, "Failed to read $key", it)
        }.getOrNull()
    }

    private fun writeToken(key: String, token: String?) {
        runCatching {
            prefs.edit().putString(key, token).apply()
        }.onSuccess {
            AppLogger.info(TAG, "$key ${if (token == null) "cleared" else "stored"}")
        }.onFailure {
            AppLogger.error(TAG, "Failed to write $key", it)
        }
    }

    companion object {
        private const val TAG = "PushIT/TokenStorage"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_APP_TOKEN = "app_token"
        private const val KEY_LANGUAGE = "ui_language"
        private const val KEY_NOTIF_STATE = "notification_state"
    }
}
