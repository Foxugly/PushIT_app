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
import com.foxugly.pushit_app.platform.DeviceLinkManager
import com.foxugly.pushit_app.platform.FcmTokenProvider
import com.foxugly.pushit_app.platform.FcmTokenProviderSource
import com.foxugly.pushit_app.ui.components.ErrorBanner
import com.foxugly.pushit_app.ui.login.LoginScreen
import com.foxugly.pushit_app.ui.notifications.NotificationDetailScreen
import com.foxugly.pushit_app.ui.notifications.NotificationListScreen
import com.foxugly.pushit_app.ui.qrscanner.QrScannerScreen
import com.foxugly.pushit_app.ui.settings.SettingsScreen
import com.foxugly.pushit_app.ui.theme.PushItTheme
import kotlinx.coroutines.launch

@Composable
fun App(
    tokenStorage: TokenStorage,
    fcmTokenProvider: FcmTokenProvider,
    externalRefreshTrigger: Int = 0,
    apiBaseUrl: String = "https://pushit-api.foxugly.com/api/v1/",
    enableHttpLogging: Boolean = false,
) {
    val tokenStore = remember(tokenStorage) { TokenStorageStore(tokenStorage) }
    val api = remember(apiBaseUrl) { PushItApi(tokenStore, apiBaseUrl, enableHttpLogging) }
    val authRepository = remember(api) { AuthRepository(api, tokenStore) }
    val notificationRepository = remember(api) { NotificationRepository(api) }
    val deviceLinkManager = remember(api) {
        DeviceLinkManager(api, tokenStore, FcmTokenProviderSource(fcmTokenProvider))
    }

    var currentScreen by remember { mutableStateOf<Screen?>(null) }
    // Real back stack (the old single `previousScreen` lost history on 2-hop nav).
    val backStack = remember { mutableStateListOf<Screen>() }
    var refreshTrigger by remember { mutableStateOf(0) }
    var runtimeError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Combine internal and external refresh triggers
    val effectiveRefreshTrigger = refreshTrigger + externalRefreshTrigger

    fun resetTo(screen: Screen) {
        // Clears history — used after login/logout/auth-failure so a back press
        // can't return to a stale (e.g. authenticated) screen.
        backStack.clear()
        runtimeError = null
        currentScreen = screen
    }

    fun navigateTo(screen: Screen) {
        AppLogger.info(TAG, "Navigate to $screen")
        currentScreen?.let { backStack.add(it) }
        currentScreen = screen
    }

    fun navigateBack() {
        currentScreen = if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) else Screen.Login
    }

    // Auth failure callback
    LaunchedEffect(Unit) {
        api.onAuthFailure = {
            AppLogger.warn(TAG, "Authentication failure callback received")
            resetTo(Screen.Login)
        }
    }

    // Startup flow
    LaunchedEffect(Unit) {
        AppLogger.info(TAG, "Startup flow started")
        runCatching {
            currentScreen = when {
                authRepository.isAuthenticated() -> {
                    AppLogger.info(TAG, "Startup route: authenticated")
                    Screen.NotificationList
                }
                authRepository.hasRefreshToken() -> {
                    AppLogger.info(TAG, "Startup route: refresh token available")
                    if (authRepository.tryRefresh()) Screen.NotificationList else Screen.Login
                }
                else -> {
                    AppLogger.info(TAG, "Startup route: login")
                    Screen.Login
                }
            }
        }.onFailure {
            AppLogger.error(TAG, "Startup flow failed", it)
            runtimeError = it.message ?: "Startup failed"
            currentScreen = Screen.Login
        }
    }

    // Identify the authenticated device and link it when an app token is available.
    LaunchedEffect(currentScreen) {
        if (currentScreen == Screen.NotificationList) {
            deviceLinkManager.syncAuthenticatedDevice().onFailure {
                runtimeError = it.message ?: "Device connection failed"
            }
        }
    }

    // Observe FCM token changes — DisposableEffect so the singleton provider's
    // callback (and its captured scope) is detached when App leaves composition.
    DisposableEffect(Unit) {
        deviceLinkManager.startObservingTokenChanges {
            scope.launch {
                deviceLinkManager.syncAuthenticatedDevice().onFailure { throwable ->
                    runtimeError = throwable.message ?: "Device connection failed"
                }
            }
        }
        onDispose { deviceLinkManager.stopObservingTokenChanges() }
    }

    fun onLoginOrRegisterSuccess() {
        scope.launch {
            val state = deviceLinkManager.syncAuthenticatedDevice().getOrElse {
                runtimeError = it.message ?: "Device connection failed"
                null
            }
            val hasKnownLinkedApps = state?.linkedApplications?.isNotEmpty() == true
            if (!hasKnownLinkedApps && tokenStorage.getAppToken() == null) {
                resetTo(Screen.QrScanner)
            } else {
                resetTo(Screen.NotificationList)
            }
        }
    }

    PushItTheme {
        val screen = currentScreen
        if (screen == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@PushItTheme
        }

        Column(Modifier.fillMaxSize()) {
            runtimeError?.let {
                // Tap to dismiss — otherwise a transient sync error stuck around forever.
                ErrorBanner(
                    it,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { runtimeError = null },
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
                                    runtimeError = throwable.message ?: "Device link failed"
                                }
                                resetTo(
                                    if (authRepository.isAuthenticated()) Screen.NotificationList
                                    else Screen.Login
                                )
                            }
                        },
                        onBack = { navigateBack() },
                    )
                    Screen.NotificationList -> NotificationListScreen(
                        notificationRepository = notificationRepository,
                        onNavigateToDetail = { id -> navigateTo(Screen.NotificationDetail(id)) },
                        onNavigateToSettings = { navigateTo(Screen.Settings) },
                        refreshTrigger = effectiveRefreshTrigger,
                    )
                    is Screen.NotificationDetail -> NotificationDetailScreen(
                        notificationId = screen.notificationId,
                        notificationRepository = notificationRepository,
                        onNavigateBack = { navigateBack() },
                    )
                    Screen.Settings -> SettingsScreen(
                        authRepository = authRepository,
                        tokenStorage = tokenStorage,
                        deviceLinkManager = deviceLinkManager,
                        onNavigateToQrScanner = { navigateTo(Screen.QrScanner) },
                        onLogout = { resetTo(Screen.Login) },
                        onBack = { navigateBack() },
                    )
                }
            }
        }
    }
}

private const val TAG = "PushIT/App"
