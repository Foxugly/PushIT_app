package com.foxugly.pustit_app.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

actual class TokenStorage(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "pustit_secure_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    actual fun getAccessToken(): String? = prefs.getString(KEY_ACCESS, null)
    actual fun setAccessToken(token: String?) {
        prefs.edit().putString(KEY_ACCESS, token).apply()
    }

    actual fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)
    actual fun setRefreshToken(token: String?) {
        prefs.edit().putString(KEY_REFRESH, token).apply()
    }

    actual fun getAppToken(): String? = prefs.getString(KEY_APP_TOKEN, null)
    actual fun setAppToken(token: String?) {
        prefs.edit().putString(KEY_APP_TOKEN, token).apply()
    }

    actual fun clearAuthTokens() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .apply()
    }

    companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_APP_TOKEN = "app_token"
    }
}
