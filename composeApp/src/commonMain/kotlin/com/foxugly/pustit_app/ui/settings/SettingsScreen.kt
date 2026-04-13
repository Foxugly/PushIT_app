package com.foxugly.pustit_app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foxugly.pustit_app.data.api.UserProfile
import com.foxugly.pustit_app.data.repository.AuthRepository
import com.foxugly.pustit_app.data.storage.TokenStorage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authRepository: AuthRepository,
    tokenStorage: TokenStorage,
    onNavigateToQrScanner: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var userError by remember { mutableStateOf<String?>(null) }
    var isLoadingUser by remember { mutableStateOf(true) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var appToken by remember { mutableStateOf(tokenStorage.getAppToken()) }

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
                    Text(
                        text = userError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
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
                            Spacer(Modifier.height(8.dp))
                            LabeledValue(label = "Username", value = userProfile!!.username)
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
