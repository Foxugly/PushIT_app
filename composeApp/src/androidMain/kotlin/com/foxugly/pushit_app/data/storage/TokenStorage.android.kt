package com.foxugly.pushit_app.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.foxugly.pushit_app.diagnostics.AppLogger
import java.security.KeyStore

// EncryptedSharedPreferences is itself deprecated in security-crypto 1.1.0 (no
// successor in AndroidX yet); it stays until a DataStore + Tink migration.
@Suppress("DEPRECATION")
actual class TokenStorage(context: Context) {

    // MasterKey.Builder replaces the deprecated MasterKeys.getOrCreate(AES256_GCM_SPEC).
    // Migration-safe: same Keystore alias (DEFAULT_MASTER_KEY_ALIAS ==
    // "_androidx_security_master_key_", what MasterKeys used) and the same
    // AES256-GCM key spec, so the key created by earlier versions is reused and
    // the existing prefs file stays readable.
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
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

    /**
     * Le code d'enrôlement (`apk_…`, ou l'ancien jeton `apt_…` d'une install
     * qui n'a pas re-scanné).
     *
     * Migration : la valeur vivait sous `app_token`. On la relit une fois, on la
     * réécrit sous la nouvelle clé et on efface l'ancienne — sinon un
     * utilisateur déjà rattaché repartirait de zéro à la mise à jour, écran
     * « non lié » à l'appui, sans rien avoir fait.
     */
    actual fun getEnrolmentCode(): String? =
        readToken(KEY_ENROLMENT_CODE) ?: migrateLegacyEnrolmentCode()

    actual fun setEnrolmentCode(code: String?) {
        writeToken(KEY_ENROLMENT_CODE, code)
    }

    private fun migrateLegacyEnrolmentCode(): String? {
        val legacy = readToken(KEY_LEGACY_APP_TOKEN) ?: return null
        writeToken(KEY_ENROLMENT_CODE, legacy)
        runCatching { prefs.edit().remove(KEY_LEGACY_APP_TOKEN).commit() }
            .onFailure { AppLogger.error(TAG, "Failed to drop the legacy app_token key", it) }
        AppLogger.info(TAG, "Enrolment code migrated from the legacy key")
        return legacy
    }

    actual fun getLastPushToken(): String? = readToken(KEY_LAST_PUSH_TOKEN)
    actual fun setLastPushToken(token: String?) {
        writeToken(KEY_LAST_PUSH_TOKEN, token)
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
        private const val PREFS_FILE = "pushit_secure_prefs"

        /**
         * Last-resort recovery when the encrypted store can't be opened (Keystore
         * key lost or invalidated, e.g. after a device restore or a lock-screen
         * reset, or a corrupt prefs file): delete the prefs file AND the master
         * key so the next [TokenStorage] starts clean. Everything in it is lost —
         * session, enrolment code, language, inbox state — so the user signs in
         * and re-scans. Returns true when both deletions went through.
         */
        fun resetCorruptedStorage(context: Context): Boolean {
            val prefsDeleted = runCatching { context.deleteSharedPreferences(PREFS_FILE) }
                .onFailure { AppLogger.error(TAG, "Failed to delete the encrypted prefs", it) }
                .getOrDefault(false)
            val keyDeleted = runCatching {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }.onFailure { AppLogger.error(TAG, "Failed to delete the master key", it) }.isSuccess
            AppLogger.warn(TAG, "Secure storage reset (prefs=$prefsDeleted, key=$keyDeleted)")
            return prefsDeleted && keyDeleted
        }
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_ENROLMENT_CODE = "enrolment_code"
        // Ancienne clé, lue une seule fois puis effacée (voir la migration).
        private const val KEY_LEGACY_APP_TOKEN = "app_token"
        private const val KEY_LAST_PUSH_TOKEN = "last_push_token"
        private const val KEY_LANGUAGE = "ui_language"
        private const val KEY_NOTIF_STATE = "notification_state"
    }
}
