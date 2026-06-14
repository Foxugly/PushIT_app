package com.foxugly.pushit_app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.data.api.PushItApi
import com.foxugly.pushit_app.data.repository.AuthRepository
import com.foxugly.pushit_app.data.repository.NotificationRepository
import com.foxugly.pushit_app.data.storage.TokenStorage
import com.foxugly.pushit_app.data.storage.TokenStorageStore
import com.foxugly.pushit_app.diagnostics.AppLogger
import com.foxugly.pushit_app.navigation.Screen
import com.foxugly.pushit_app.navigation.SessionViewModel
import com.foxugly.pushit_app.platform.DeviceLinkManager
import com.foxugly.pushit_app.platform.FcmTokenProvider
import com.foxugly.pushit_app.platform.FcmTokenProviderSource
import com.foxugly.pushit_app.platform.updateAppBadge
import com.foxugly.pushit_app.ui.components.ErrorBanner
import com.foxugly.pushit_app.ui.login.LoginScreen
import com.foxugly.pushit_app.ui.notifications.AppFolderScreen
import com.foxugly.pushit_app.ui.notifications.InboxStore
import com.foxugly.pushit_app.ui.notifications.NotificationDetailScreen
import com.foxugly.pushit_app.ui.notifications.NotificationListScreen
import com.foxugly.pushit_app.ui.i18n.AppLanguage
import com.foxugly.pushit_app.ui.i18n.LocalStrings
import com.foxugly.pushit_app.ui.i18n.errorText
import com.foxugly.pushit_app.ui.i18n.stringsFor
import com.foxugly.pushit_app.ui.qrscanner.QrScannerScreen
import com.foxugly.pushit_app.ui.settings.SettingsScreen
import com.foxugly.pushit_app.ui.theme.PushItTheme
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.launch

@Composable
fun App(
    tokenStorage: TokenStorage,
    fcmTokenProvider: FcmTokenProvider,
    externalRefreshTrigger: Int = 0,
    apiBaseUrl: String = "https://pushit-api.foxugly.com/api/v1/",
    enableHttpLogging: Boolean = false,
    // Deep-link target from a tapped push (null when launched normally). App
    // opens the message once the inbox has it, then calls onDeepLinkConsumed.
    deepLinkNotificationId: Int? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val tokenStore = remember(tokenStorage) { TokenStorageStore(tokenStorage) }
    val api = remember(apiBaseUrl) { PushItApi(tokenStore, apiBaseUrl, enableHttpLogging) }
    val authRepository = remember(api) { AuthRepository(api, tokenStore) }
    val notificationRepository = remember(api) { NotificationRepository(api) }
    val inbox = remember(notificationRepository) {
        InboxStore(notificationRepository, tokenStorage, FcmTokenProviderSource(fcmTokenProvider))
    }
    val deviceLinkManager = remember(api) {
        DeviceLinkManager(api, tokenStore, FcmTokenProviderSource(fcmTokenProvider))
    }

    val session = remember(authRepository) { SessionViewModel(authRepository) }
    var refreshTrigger by remember { mutableStateOf(0) }
    // UI language: a local preference (persisted in TokenStorage), defaulting to
    // the fleet default when unset. Changed from Settings.
    var language by remember { mutableStateOf(AppLanguage.fromCode(tokenStorage.getLanguage())) }
    val strings = stringsFor(language)
    val scope = rememberCoroutineScope()

    // Combine internal and external refresh triggers
    val effectiveRefreshTrigger = refreshTrigger + externalRefreshTrigger

    // Auth failure callback
    LaunchedEffect(Unit) {
        api.onAuthFailure = {
            AppLogger.warn(TAG, "Authentication failure callback received")
            session.resetTo(Screen.Login)
        }
    }

    // Startup flow — the routing decision lives in SessionViewModel (testable).
    LaunchedEffect(Unit) {
        session.start { strings.errorText(it, strings.startupFailed) }
    }

    // Identify the authenticated device and link it when an app token is available.
    LaunchedEffect(session.currentScreen) {
        if (session.currentScreen == Screen.NotificationList) {
            deviceLinkManager.syncAuthenticatedDevice()
                .onSuccess { state -> state?.let { inbox.updateLinkedApps(it.linkedApplications) } }
                .onFailure {
                    session.runtimeError = strings.errorText(it, strings.deviceConnectionFailed)
                }
        }
    }

    // Mirror the unread count onto the OS app-icon badge (best-effort on
    // Android, exact on iOS). Clears to a dot/zero when everything is read.
    LaunchedEffect(inbox.unread.size) { updateAppBadge(inbox.unread.size) }

    // Deep-link from a tapped push: open the message once the inbox has loaded
    // it. Waits while the inbox is still loading; gives up (consumes) if the
    // user isn't authenticated or the message simply isn't in the inbox.
    LaunchedEffect(deepLinkNotificationId, session.currentScreen, inbox.notifications, inbox.loading) {
        val id = deepLinkNotificationId ?: return@LaunchedEffect
        when (val screen = session.currentScreen) {
            null -> Unit // startup still resolving — wait
            Screen.Login, Screen.QrScanner -> onDeepLinkConsumed() // not a recipient context
            Screen.NotificationDetail(id) -> onDeepLinkConsumed() // already showing it
            else -> when {
                inbox.find(id) != null -> {
                    session.navigateTo(Screen.NotificationDetail(id))
                    onDeepLinkConsumed()
                }
                // Inbox settled without this message (old / dismissed) — stop waiting.
                !inbox.loading && inbox.notifications.isNotEmpty() -> onDeepLinkConsumed()
                else -> Unit // inbox still loading — re-runs when it updates
            }
        }
    }

    // Observe FCM token changes — DisposableEffect so the singleton provider's
    // callback (and its captured scope) is detached when App leaves composition.
    DisposableEffect(Unit) {
        deviceLinkManager.startObservingTokenChanges {
            scope.launch {
                deviceLinkManager.syncAuthenticatedDevice()
                    .onSuccess { state -> state?.let { inbox.updateLinkedApps(it.linkedApplications) } }
                    .onFailure { throwable ->
                        session.runtimeError = strings.errorText(throwable, strings.deviceConnectionFailed)
                    }
            }
        }
        onDispose { deviceLinkManager.stopObservingTokenChanges() }
    }

    fun onLoginOrRegisterSuccess() {
        scope.launch {
            val state = deviceLinkManager.syncAuthenticatedDevice().getOrElse {
                session.runtimeError = strings.errorText(it, strings.deviceConnectionFailed)
                null
            }
            state?.let { inbox.updateLinkedApps(it.linkedApplications) }
            val hasKnownLinkedApps = state?.linkedApplications?.isNotEmpty() == true
            session.resetTo(session.routeAfterLogin(hasKnownLinkedApps, tokenStorage.getAppToken() != null))
        }
    }

    PushItTheme {
        CompositionLocalProvider(LocalStrings provides strings) {
        val screen = session.currentScreen
        if (screen == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@CompositionLocalProvider
        }

        Column(Modifier.fillMaxSize()) {
            session.runtimeError?.let {
                // Tap to dismiss — otherwise a transient sync error stuck around forever.
                ErrorBanner(
                    it,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { session.runtimeError = null },
                )
            }
            Box(Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.Login -> LoginScreen(
                        authRepository = authRepository,
                        onLoginSuccess = ::onLoginOrRegisterSuccess,
                    )
                    Screen.QrScanner -> QrScannerScreen(
                        tokenStorage = tokenStorage,
                        onTokenScanned = {
                            // Link THEN navigate in the same coroutine: the old code
                            // navigated synchronously before the link finished, so a
                            // link failure surfaced on an already-different screen.
                            scope.launch {
                                deviceLinkManager.linkWithStoredAppToken().onFailure { throwable ->
                                    session.runtimeError = strings.errorText(throwable, strings.deviceLinkFailed)
                                }
                                session.resetTo(session.routeAfterQrLink())
                            }
                        },
                        onBack = { session.navigateBack() },
                    )
                    Screen.NotificationList -> NotificationListScreen(
                        inbox = inbox,
                        onNavigateToDetail = { id -> session.navigateTo(Screen.NotificationDetail(id)) },
                        onNavigateToFolder = { appId, name -> session.navigateTo(Screen.AppFolder(appId, name)) },
                        onNavigateToSettings = { session.navigateTo(Screen.Settings) },
                        refreshTrigger = effectiveRefreshTrigger,
                    )
                    is Screen.AppFolder -> AppFolderScreen(
                        inbox = inbox,
                        applicationId = screen.applicationId,
                        applicationName = screen.applicationName,
                        onNavigateToDetail = { id -> session.navigateTo(Screen.NotificationDetail(id)) },
                        onNavigateBack = { session.navigateBack() },
                    )
                    is Screen.NotificationDetail -> NotificationDetailScreen(
                        notificationId = screen.notificationId,
                        inbox = inbox,
                        onNavigateBack = { session.navigateBack() },
                    )
                    Screen.Settings -> SettingsScreen(
                        authRepository = authRepository,
                        tokenStorage = tokenStorage,
                        deviceLinkManager = deviceLinkManager,
                        language = language,
                        onLanguageChange = { selected ->
                            language = selected
                            tokenStorage.setLanguage(selected.code)
                        },
                        onNavigateToQrScanner = { session.navigateTo(Screen.QrScanner) },
                        onLogout = { session.resetTo(Screen.Login) },
                        onBack = { session.navigateBack() },
                    )
                }
            }
        }
        }
    }
}

private const val TAG = "PushIT/App"
