package com.foxugly.pushit_app.data.storage

import platform.Foundation.NSUserDefaults

actual class TokenStorage {

    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getAccessToken(): String? = defaults.stringForKey(KEY_ACCESS)
    actual fun setAccessToken(token: String?) {
        if (token != null) {
            defaults.setObject(token, forKey = KEY_ACCESS)
        } else {
            defaults.removeObjectForKey(KEY_ACCESS)
        }
    }

    actual fun getRefreshToken(): String? = defaults.stringForKey(KEY_REFRESH)
    actual fun setRefreshToken(token: String?) {
        if (token != null) {
            defaults.setObject(token, forKey = KEY_REFRESH)
        } else {
            defaults.removeObjectForKey(KEY_REFRESH)
        }
    }

    actual fun getAppToken(): String? = defaults.stringForKey(KEY_APP_TOKEN)
    actual fun setAppToken(token: String?) {
        if (token != null) {
            defaults.setObject(token, forKey = KEY_APP_TOKEN)
        } else {
            defaults.removeObjectForKey(KEY_APP_TOKEN)
        }
    }

    actual fun getLanguage(): String? = defaults.stringForKey(KEY_LANGUAGE)
    actual fun setLanguage(code: String?) {
        if (code != null) {
            defaults.setObject(code, forKey = KEY_LANGUAGE)
        } else {
            defaults.removeObjectForKey(KEY_LANGUAGE)
        }
    }

    actual fun clearAuthTokens() {
        defaults.removeObjectForKey(KEY_ACCESS)
        defaults.removeObjectForKey(KEY_REFRESH)
    }

    companion object {
        private const val KEY_ACCESS = "pushit_access_token"
        private const val KEY_REFRESH = "pushit_refresh_token"
        private const val KEY_APP_TOKEN = "pushit_app_token"
        private const val KEY_LANGUAGE = "pushit_ui_language"
    }
}
