package com.foxugly.pushit_app.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.foxugly.pushit_app.data.api.AndroidAppVersion
import com.foxugly.pushit_app.data.storage.AppUpdateStateStore
import com.foxugly.pushit_app.diagnostics.AppLogger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

/**
 * Version code of the installed app, or null when the platform has no
 * sideloaded-APK update channel (iOS) or it can't be read. Null disables the
 * "new version available" check entirely.
 */
expect fun installedAppVersionCode(): Long?

/** Website page where a signed-in user downloads the Android APK. */
const val APK_DOWNLOAD_PAGE_URL = "https://pushit.foxugly.com/dashboard/devices"

/**
 * True when [latest] is newer than the installed build and the user has not
 * already dismissed that version (or a newer one).
 */
fun shouldOfferUpdate(latestVersionCode: Long, installedVersionCode: Long, dismissedVersionCode: Long?): Boolean =
    latestVersionCode > installedVersionCode &&
        (dismissedVersionCode == null || latestVersionCode > dismissedVersionCode)

/**
 * Polls the server for the APK published on the website (Android only — the APK
 * is sideloaded, not on Play) and exposes [available] for the inbox banner.
 *
 * Checks are throttled to one per [minInterval] per process (any outcome counts,
 * so an outage is not hammered on every resume). Every failure — 404 when no APK
 * is published, offline, decoding — just means "no banner"; apiCall logs it.
 */
@OptIn(ExperimentalTime::class)
class AppUpdateChecker(
    private val fetchLatest: suspend () -> Result<AndroidAppVersion>,
    private val store: AppUpdateStateStore,
    private val installedVersionCode: () -> Long? = ::installedAppVersionCode,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val minInterval: Duration = 6.hours,
) {
    /** The newer version to advertise, or null for no banner. */
    var available by mutableStateOf<AndroidAppVersion?>(null)
        private set

    private var lastCheckAtMillis: Long? = null

    /** Fetches the published version unless a check ran less than [minInterval] ago. */
    suspend fun checkIfDue() {
        val installed = installedVersionCode() ?: return
        val now = nowMillis()
        val last = lastCheckAtMillis
        if (last != null && now - last < minInterval.inWholeMilliseconds) return
        // Stamped before suspending: start-up and resume can both fire at once.
        lastCheckAtMillis = now

        fetchLatest()
            .onSuccess { latest ->
                available = latest.takeIf {
                    shouldOfferUpdate(it.versionCode, installed, store.getDismissedUpdateVersionCode())
                }
            }
            .onFailure { AppLogger.info(TAG, "App version check skipped: ${it.message}") }
    }

    /** Hides the banner until a version newer than the one shown is published. */
    fun dismiss() {
        val shown = available ?: return
        store.setDismissedUpdateVersionCode(shown.versionCode)
        available = null
    }

    private companion object {
        const val TAG = "PushIT/AppUpdate"
    }
}
