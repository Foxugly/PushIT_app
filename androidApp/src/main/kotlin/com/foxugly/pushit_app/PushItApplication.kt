package com.foxugly.pushit_app

import android.app.Application
import com.foxugly.pushit_app.diagnostics.AppLogger
import io.sentry.Breadcrumb
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid

/**
 * Process entry point. Its only job is crash reporting: Sentry is initialised
 * here — before any Activity or the FCM service runs, and before
 * MainActivity.installCrashLogger() — so Sentry's uncaught-exception handler
 * sits between our logging handler and the platform one:
 * our logger → Sentry → platform (process dies, Play Vitals records it).
 *
 * The DSN comes from BuildConfig.SENTRY_DSN (Gradle property / env var
 * PUSHIT_SENTRY_DSN, a CI secret). Empty ⇒ Sentry is not initialised at all,
 * which is the case for local and PR builds.
 */
class PushItApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initSentry()
    }

    private fun initSentry() {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank()) {
            AppLogger.info(TAG, "Sentry DSN not configured; crash reporting disabled")
            return
        }
        SentryAndroid.init(this) { options ->
            options.dsn = dsn
            options.environment = if (BuildConfig.DEBUG) "debug" else "production"
            // No PII: no IP address, no user, no request bodies/headers.
            options.isSendDefaultPii = false
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            // Taps would record view ids; not needed, and keeps the trail minimal.
            options.isEnableUserInteractionBreadcrumbs = false
            options.tracesSampleRate = 0.0
            options.setBeforeBreadcrumb { breadcrumb, _ -> scrubBreadcrumb(breadcrumb) }
            options.setBeforeSend { event, _ ->
                event.user = null
                event.request = null
                event
            }
        }
        AppLogger.info(TAG, "Sentry initialised")
    }

    companion object {
        private const val TAG = "PushIT/Application"

        // JWTs (eyJ…), Bearer headers, FCM tokens and enrolment codes must never
        // ride along in a crash report.
        private val SECRET_PATTERNS = listOf(
            Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+"""),
            Regex("""(?i)bearer\s+\S+"""),
            Regex("""\b(apk|apt)_[A-Za-z0-9_-]+"""),
            Regex("(?i)(token|password|authorization)"),
        )

        /** Drop HTTP breadcrumbs entirely and any breadcrumb that looks like it carries a secret. */
        internal fun scrubBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb? {
            val category = breadcrumb.category.orEmpty()
            if (category.startsWith("http") || category == "network.event") return null
            val text = buildString {
                append(breadcrumb.message.orEmpty())
                breadcrumb.data.forEach { (k, v) -> append(' ').append(k).append('=').append(v) }
            }
            if (SECRET_PATTERNS.any { it.containsMatchIn(text) }) return null
            if (breadcrumb.level == SentryLevel.DEBUG) return null
            return breadcrumb
        }
    }
}
