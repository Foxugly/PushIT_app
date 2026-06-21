package com.foxugly.pushit_app.data.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS token storage.
 *
 * The THREE secret values (JWT access + refresh tokens and the app token) live in
 * the iOS **Keychain** (`kSecClassGenericPassword`,
 * `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` — encrypted at rest, not in
 * iCloud/iTunes backups, never synced across devices). The earlier implementation
 * kept them in `NSUserDefaults`, which is plaintext on disk.
 *
 * Non-secret preferences (UI language, local inbox read state) stay in
 * `NSUserDefaults` — they need no protection and are convenient to read
 * synchronously.
 *
 * NOTE: this file is iosMain and is only compiled on macOS; it cannot be built on
 * the Windows CI host, so it is verified by review rather than by the JVM test run.
 */
@OptIn(ExperimentalForeignApi::class)
actual class TokenStorage {

    private val defaults = NSUserDefaults.standardUserDefaults

    // --- Secrets: Keychain ---
    actual fun getAccessToken(): String? = keychainGet(KEY_ACCESS)
    actual fun setAccessToken(token: String?) = keychainSet(KEY_ACCESS, token)

    actual fun getRefreshToken(): String? = keychainGet(KEY_REFRESH)
    actual fun setRefreshToken(token: String?) = keychainSet(KEY_REFRESH, token)

    actual fun getAppToken(): String? = keychainGet(KEY_APP_TOKEN)
    actual fun setAppToken(token: String?) = keychainSet(KEY_APP_TOKEN, token)

    actual fun clearAuthTokens() {
        keychainSet(KEY_ACCESS, null)
        keychainSet(KEY_REFRESH, null)
    }

    // --- Non-secret preferences: NSUserDefaults ---
    actual fun getLanguage(): String? = defaults.stringForKey(KEY_LANGUAGE)
    actual fun setLanguage(code: String?) = defaultsSet(KEY_LANGUAGE, code)

    actual fun getNotificationState(): String? = defaults.stringForKey(KEY_NOTIF_STATE)
    actual fun setNotificationState(json: String?) = defaultsSet(KEY_NOTIF_STATE, json)

    private fun defaultsSet(key: String, value: String?) {
        if (value != null) {
            defaults.setObject(value, forKey = key)
        } else {
            defaults.removeObjectForKey(key)
        }
    }

    // --- Keychain helpers ---
    //
    // Each secret is one generic-password item keyed by (service, account). A
    // write deletes any existing item first (SecItemAdd fails with errSecDuplicateItem
    // otherwise), keeping set() idempotent. Reads return null when absent or on any
    // error — the caller treats that as "no token".

    private fun keychainBaseQuery(account: String): Map<Any?, Any?> = mapOf(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to SERVICE,
        kSecAttrAccount to account,
    )

    private fun keychainGet(account: String): String? = memScoped {
        val query = (
            keychainBaseQuery(account) + mapOf(
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne,
            )
        ).toCFDictionary()
        try {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status != errSecSuccess) {
                // errSecItemNotFound is the normal "no token yet" case.
                return@memScoped null
            }
            // Transfer ownership of the returned CFData to ARC and decode as UTF-8.
            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            NSString.create(data, NSUTF8StringEncoding) as String?
        } finally {
            CFBridgingRelease(query)
        }
    }

    private fun keychainSet(account: String, value: String?) {
        // Always remove the existing item first so set() is idempotent and a null
        // clears the secret.
        keychainDelete(account)
        if (value == null) return

        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val attributes = (
            keychainBaseQuery(account) + mapOf(
                kSecValueData to data,
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            )
        ).toCFDictionary()
        try {
            SecItemAdd(attributes, null)
        } finally {
            CFBridgingRelease(attributes)
        }
    }

    private fun keychainDelete(account: String) {
        val query = keychainBaseQuery(account).toCFDictionary()
        try {
            val status = SecItemDelete(query)
            // errSecSuccess (deleted) and errSecItemNotFound (nothing to delete) are
            // both fine; anything else is left silent — a stale item would only fail
            // a later read, surfacing as "no token".
            @Suppress("UNUSED_EXPRESSION")
            (status == errSecSuccess || status == errSecItemNotFound)
        } finally {
            CFBridgingRelease(query)
        }
    }

    // Build a retained CFDictionary from a Kotlin map of CoreFoundation values.
    // Ownership is transferred to the caller, which must CFBridgingRelease it.
    private fun Map<Any?, Any?>.toCFDictionary(): CFDictionaryRef? {
        val dict = platform.Foundation.NSMutableDictionary()
        for ((k, v) in this) {
            if (k != null && v != null) dict.setObject(v, forKey = k as Any)
        }
        return CFBridgingRetain(dict) as CFDictionaryRef?
    }

    companion object {
        // Keychain service namespace for this app's secrets.
        private const val SERVICE = "com.foxugly.pushit_app"

        // Keychain account keys (secrets).
        private const val KEY_ACCESS = "pushit_access_token"
        private const val KEY_REFRESH = "pushit_refresh_token"
        private const val KEY_APP_TOKEN = "pushit_app_token"

        // NSUserDefaults keys (non-secret).
        private const val KEY_LANGUAGE = "pushit_ui_language"
        private const val KEY_NOTIF_STATE = "pushit_notification_state"
    }
}
