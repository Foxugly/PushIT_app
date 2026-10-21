package com.foxugly.pushit_app.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.foxugly.pushit_app.data.repository.AuthRepository
import com.foxugly.pushit_app.diagnostics.AppLogger

/**
 * Owns the app's session/navigation state and the routing DECISIONS that used to
 * live inline in `App.kt`. Pulling them here lets them be unit-tested without a
 * Compose host (the startup route, the post-login and post-QR routes were the
 * most critical logic in the app yet entirely untested inside `@Composable`s).
 *
 * State is Compose snapshot state, so `App()` recomposes when it changes. This is
 * a plain class (not an androidx `ViewModel`) on purpose: no lifecycle/scope to
 * manage, and it constructs trivially in tests.
 */
class SessionViewModel(private val authRepository: AuthRepository) {
    private val tag = "PushIT/SessionViewModel"

    /** Current screen; `null` while the startup route is still resolving. */
    var currentScreen by mutableStateOf<Screen?>(null)
        private set

    /** Localized, dismissible error banner text (set by the caller with i18n). */
    var runtimeError by mutableStateOf<String?>(null)

    /**
     * True after a dead session sent the user back to Login (refresh token refused
     * at startup or on a later call). The login screen shows a discreet "session
     * expired" note — never an error dialog. Cleared by any [resetTo].
     */
    var sessionExpired by mutableStateOf(false)
        private set

    // Real back stack (a single "previous" screen lost history on 2-hop nav).
    private val backStack = mutableStateListOf<Screen>()

    /** Push the current screen onto the back stack and go to [screen]. */
    fun navigateTo(screen: Screen) {
        AppLogger.info(tag, "Navigate to $screen")
        currentScreen?.let { backStack.add(it) }
        currentScreen = screen
    }

    /** Pop the back stack, or fall back to Login when it's empty. */
    fun navigateBack() {
        currentScreen = if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) else Screen.Login
    }

    /**
     * Clear history + error and jump to [screen]. Used after login/logout/auth
     * failure so a back press can't return to a stale (e.g. authenticated) screen.
     */
    fun resetTo(screen: Screen) {
        backStack.clear()
        runtimeError = null
        sessionExpired = false
        currentScreen = screen
    }

    /**
     * The server refused the refresh token: tokens are already cleared. Go to
     * Login without an error dialog, flagging [sessionExpired] for the discreet note.
     */
    fun expireSession() {
        AppLogger.info(tag, "Session expired; back to login")
        resetTo(Screen.Login)
        sessionExpired = true
    }

    /**
     * Resolve and apply the cold-start route. [localizeError] keeps i18n out of
     * the view-model while still surfacing a localized message on failure (the
     * decision itself is in [resolveStartupRoute], which stays pure/testable).
     */
    suspend fun start(localizeError: (Throwable) -> String?) {
        AppLogger.info(tag, "Startup flow started")
        val hadSession = authRepository.isAuthenticated() || authRepository.hasRefreshToken()
        runCatching {
            val route = resolveStartupRoute()
            // A stored session that resolves to Login was refused by the server
            // (refresh 401/403): a discreet note, not an error.
            if (route == Screen.Login && hadSession) expireSession() else currentScreen = route
        }
            .onFailure {
                AppLogger.error(tag, "Startup flow failed", it)
                runtimeError = localizeError(it)
                currentScreen = Screen.Login
            }
    }

    /**
     * Where a cold start should land, given stored credentials: the inbox on a
     * valid access token, else a single refresh attempt, else login. Suspends
     * (may refresh). Pure decision (no state mutation) → unit-testable.
     */
    suspend fun resolveStartupRoute(): Screen = when {
        authRepository.isAuthenticated() && !authRepository.accessTokenExpired() -> Screen.NotificationList
        authRepository.hasRefreshToken() -> if (authRepository.tryRefresh()) Screen.NotificationList else Screen.Login
        else -> Screen.Login
    }

    /**
     * After a successful login: always the inbox. The scanner is no longer
     * forced on a device without linked apps; the user opens it with the "+"
     * of the inbox top bar.
     */
    fun routeAfterLogin(): Screen = Screen.NotificationList

    /** After linking via QR: the inbox while still authenticated, else login. */
    fun routeAfterQrLink(): Screen =
        if (authRepository.isAuthenticated()) Screen.NotificationList else Screen.Login
}
