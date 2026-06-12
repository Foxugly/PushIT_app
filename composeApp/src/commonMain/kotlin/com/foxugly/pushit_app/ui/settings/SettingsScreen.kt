package com.foxugly.pushit_app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.data.api.UserProfile
import com.foxugly.pushit_app.data.repository.AuthRepository
import com.foxugly.pushit_app.data.storage.TokenStorage
import com.foxugly.pushit_app.platform.DeviceLinkManager
import com.foxugly.pushit_app.ui.components.ErrorBanner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authRepository: AuthRepository,
    tokenStorage: TokenStorage,
    deviceLinkManager: DeviceLinkManager,
    onNavigateToQrScanner: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var userError by remember { mutableStateOf<String?>(null) }
    var isLoadingUser by remember { mutableStateOf(true) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var appToken by remember { mutableStateOf(tokenStorage.getAppToken()) }
    var isUnlinking by remember { mutableStateOf(false) }
    var unlinkError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        authRepository.getCurrentUser().fold(
            onSuccess = { profile ->
                userProfile = profile
                userError = null
            },
            onFailure = { throwable ->
                userError = throwable.message ?: "Failed to load user info"
            },
        )
        isLoadingUser = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // User info section
            Text(
                text = "Account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            when {
                isLoadingUser -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
                userError != null -> {
                    ErrorBanner(userError!!)
                }
                userProfile != null -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            LabeledValue(label = "Email", value = userProfile!!.email)
                            userProfile!!.userkey?.let { key ->
                                Spacer(Modifier.height(8.dp))
                                LabeledValue(label = "User key", value = key)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // App token section
            Text(
                text = "App Token",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val tokenDisplay = appToken
                    if (tokenDisplay != null) {
                        // Show a truncated version: "Linked (apt_xxxx...)"
                        val truncated = if (tokenDisplay.length > 12) {
                            tokenDisplay.take(12) + "..."
                        } else {
                            tokenDisplay
                        }
                        Text(
                            text = "Linked ($truncated)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "Not linked to an application",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    appToken = tokenStorage.getAppToken()
                    onNavigateToQrScanner()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (appToken != null) "Re-scan QR Code" else "Scan QR Code")
            }

            // Unlink: forget the app token on this device (e.g. shared/returned
            // device). The token survives a normal logout by design; this is the
            // explicit opt-out. Server-side unlink is a separate backlog item.
            if (appToken != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    enabled = !isUnlinking,
                    onClick = {
                        scope.launch {
                            isUnlinking = true
                            unlinkError = null
                            deviceLinkManager.unlinkCurrentDevice().fold(
                                onSuccess = { appToken = null },
                                onFailure = { unlinkError = it.message ?: "Unlink failed" },
                            )
                            isUnlinking = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isUnlinking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Unlink this device")
                    }
                }
                unlinkError?.let {
                    Spacer(Modifier.height(8.dp))
                    ErrorBanner(it)
                }
            }

            Spacer(Modifier.weight(1f))

            // Logout button at bottom
            Button(
                onClick = {
                    scope.launch {
                        isLoggingOut = true
                        authRepository.logout()
                        isLoggingOut = false
                        onLogout()
                    }
                },
                enabled = !isLoggingOut,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                if (isLoggingOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    Text("Logout")
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
