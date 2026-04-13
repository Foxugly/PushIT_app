package com.foxugly.pustit_app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.foxugly.pustit_app.data.api.PushItApi
import com.foxugly.pustit_app.data.repository.AuthRepository
import com.foxugly.pustit_app.data.repository.NotificationRepository
import com.foxugly.pustit_app.data.storage.TokenStorage
import com.foxugly.pustit_app.navigation.Screen
import com.foxugly.pustit_app.platform.DeviceLinkManager
import com.foxugly.pustit_app.platform.FcmTokenProvider
import com.foxugly.pustit_app.ui.login.LoginScreen
import com.foxugly.pustit_app.ui.notifications.NotificationDetailScreen
import com.foxugly.pustit_app.ui.notifications.NotificationListScreen
import com.foxugly.pustit_app.ui.qrscanner.QrScannerScreen
import com.foxugly.pustit_app.ui.register.RegisterScreen
import com.foxugly.pustit_app.ui.settings.SettingsScreen
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
    val scope = rememberCoroutineScope()

    // Combine internal and external refresh triggers
    val effectiveRefreshTrigger = refreshTrigger + externalRefreshTrigger

    // Auth failure callback
    LaunchedEffect(Unit) {
        api.onAuthFailure = { currentScreen = Screen.Login }
    }

    // Startup flow
    LaunchedEffect(Unit) {
        currentScreen = when {
            authRepository.isAuthenticated() -> Screen.NotificationList
            authRepository.hasRefreshToken() -> {
                if (authRepository.tryRefresh()) Screen.NotificationList else Screen.Login
            }
            else -> Screen.Login
        }
    }

    // Try device link when on NotificationList
    LaunchedEffect(currentScreen) {
        if (currentScreen == Screen.NotificationList) {
            deviceLinkManager.tryLink()
        }
    }

    // Observe FCM token changes
    LaunchedEffect(Unit) {
        deviceLinkManager.startObservingTokenChanges { }
    }

    fun navigateTo(screen: Screen) {
        previousScreen = currentScreen ?: Screen.Login
        currentScreen = screen
    }

    fun onLoginOrRegisterSuccess() {
        if (tokenStorage.getAppToken() == null) {
            navigateTo(Screen.QrScanner)
        } else {
            navigateTo(Screen.NotificationList)
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
                    scope.launch { deviceLinkManager.tryLink() }
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
