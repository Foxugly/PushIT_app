package com.foxugly.pushit_app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.data.api.PushItApi
import com.foxugly.pushit_app.data.repository.AuthRepository
import com.foxugly.pushit_app.data.repository.NotificationRepository
import com.foxugly.pushit_app.data.storage.TokenStorage
import com.foxugly.pushit_app.diagnostics.AppLogger
import com.foxugly.pushit_app.navigation.Screen
import com.foxugly.pushit_app.platform.DeviceLinkManager
import com.foxugly.pushit_app.platform.FcmTokenProvider
import com.foxugly.pushit_app.ui.components.ErrorBanner
import com.foxugly.pushit_app.ui.login.LoginScreen
import com.foxugly.pushit_app.ui.notifications.NotificationDetailScreen
import com.foxugly.pushit_app.ui.notifications.NotificationListScreen
import com.foxugly.pushit_app.ui.qrscanner.QrScannerScreen
import com.foxugly.pushit_app.ui.register.RegisterScreen
import com.foxugly.pushit_app.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun App(
    tokenStorage: TokenStorage,
    fcmTokenProvider: FcmTokenProvider,
    externalRefreshTrigger: Int = 0,
) {
    val api = remember { PushItApi(tokenStorage) }
    val authRepository = remember { AuthRepository(api, tokenStorage) }
    val notificationRepository = remember { NotificationRepository(api) }
    val deviceLinkManager = remember { DeviceLinkManager(api, tokenStorage, fcmTokenProvider) }

    var currentScreen by remember { mutableStateOf<Screen?>(null) }
    var previousScreen by remember { mutableStateOf<Screen>(Screen.Login) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var runtimeError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Combine internal and external refresh triggers
    val effectiveRefreshTrigger = refreshTrigger + externalRefreshTrigger

    // Auth failure callback
    LaunchedEffect(Unit) {
        api.onAuthFailure = {
            AppLogger.warn(TAG, "Authentication failure callback received")
            currentScreen = Screen.Login
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

    // Observe FCM token changes
    LaunchedEffect(Unit) {
        deviceLinkManager.startObservingTokenChanges {
            scope.launch {
                deviceLinkManager.syncAuthenticatedDevice().onFailure { throwable ->
                    runtimeError = throwable.message ?: "Device connection failed"
                }
            }
        }
    }

    fun navigateTo(screen: Screen) {
        AppLogger.info(TAG, "Navigate to $screen")
        previousScreen = currentScreen ?: Screen.Login
        currentScreen = screen
    }

    fun onLoginOrRegisterSuccess() {
        scope.launch {
            val state = deviceLinkManager.syncAuthenticatedDevice().getOrElse {
                runtimeError = it.message ?: "Device connection failed"
                null
            }
            val hasKnownLinkedApps = state?.linkedApplications?.isNotEmpty() == true
            if (!hasKnownLinkedApps && tokenStorage.getAppToken() == null) {
                navigateTo(Screen.QrScanner)
            } else {
                navigateTo(Screen.NotificationList)
            }
        }
    }

    MaterialTheme {
        val screen = currentScreen
        if (screen == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@MaterialTheme
        }

        Column(Modifier.fillMaxSize()) {
            runtimeError?.let {
                ErrorBanner(it, modifier = Modifier.padding(top = 8.dp))
            }
            Box(Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.Login -> LoginScreen(
                        authRepository = authRepository,
                        onLoginSuccess = ::onLoginOrRegisterSuccess,
                        onNavigateToRegister = { navigateTo(Screen.Register) },
                    )
                    Screen.Register -> RegisterScreen(
                        authRepository = authRepository,
                        onRegisterSuccess = ::onLoginOrRegisterSuccess,
                        onNavigateToLogin = { navigateTo(Screen.Login) },
                    )
                    Screen.QrScanner -> QrScannerScreen(
                        tokenStorage = tokenStorage,
                        onTokenScanned = {
                            scope.launch {
                                deviceLinkManager.linkWithStoredAppToken().onFailure { throwable ->
                                    runtimeError = throwable.message ?: "Device link failed"
                                }
                            }
                            navigateTo(
                                if (authRepository.isAuthenticated()) Screen.NotificationList
                                else Screen.Login
                            )
                        },
                        onBack = { navigateTo(previousScreen) },
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
                        onNavigateBack = { navigateTo(Screen.NotificationList) },
                    )
                    Screen.Settings -> SettingsScreen(
                        authRepository = authRepository,
                        tokenStorage = tokenStorage,
                        onNavigateToQrScanner = { navigateTo(Screen.QrScanner) },
                        onLogout = { currentScreen = Screen.Login },
                        onBack = { navigateTo(Screen.NotificationList) },
                    )
                }
            }
        }
    }
}

private const val TAG = "PushIT/App"
