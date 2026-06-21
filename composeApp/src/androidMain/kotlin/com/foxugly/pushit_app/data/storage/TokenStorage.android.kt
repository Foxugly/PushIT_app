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
        // commit() (synchronous) so we know the removal actually persisted —
        // a silently-dropped clear could leave stale tokens on disk.
        val committed = runCatching {
            prefs.edit()
                .remove(KEY_ACCESS)
                .remove(KEY_REFRESH)
                .commit()
        }.onFailure {
            AppLogger.error(TAG, "Failed to clear auth tokens", it)
        }.getOrDefault(false)
        if (committed) {
            AppLogger.info(TAG, "Auth tokens cleared")
        } else {
            AppLogger.error(TAG, "Auth token clear was not committed")
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
        // commit() returns whether the write actually persisted. apply() was
        // fire-and-forget: an EncryptedSharedPreferences failure was swallowed and
        // the caller assumed success — a token that never landed then reads back as
        // null on the next launch, surfacing as an unexpected logout. Tokens are
        // few and infrequent, so the synchronous write is fine.
        val committed = runCatching {
            prefs.edit().putString(key, token).commit()
        }.onFailure {
            AppLogger.error(TAG, "Failed to write $key", it)
        }.getOrDefault(false)
        if (committed) {
            AppLogger.info(TAG, "$key ${if (token == null) "cleared" else "stored"}")
        } else {
            AppLogger.error(TAG, "Write of $key was not committed")
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
