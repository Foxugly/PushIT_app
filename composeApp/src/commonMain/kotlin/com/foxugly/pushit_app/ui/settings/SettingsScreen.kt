package com.foxugly.pushit_app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.data.api.LinkedApplication
import com.foxugly.pushit_app.data.api.UserProfile
import com.foxugly.pushit_app.data.repository.AuthRepository
import com.foxugly.pushit_app.data.storage.TokenStorage
import com.foxugly.pushit_app.platform.DeviceLinkManager
import com.foxugly.pushit_app.ui.components.ErrorBanner
import com.foxugly.pushit_app.ui.i18n.AppLanguage
import com.foxugly.pushit_app.ui.i18n.LocalStrings
import com.foxugly.pushit_app.ui.i18n.errorText
import com.foxugly.pushit_app.ui.theme.pushItTopAppBarColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authRepository: AuthRepository,
    tokenStorage: TokenStorage,
    deviceLinkManager: DeviceLinkManager,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onNavigateToQrScanner: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    // Called with the new linked-apps list whenever it changes (load / unlink) so
    // the caller can keep the inbox folders in sync — without unlinking, a folder
    // for an app just removed here would linger until the next full refresh.
    onLinkedAppsChanged: (List<LinkedApplication>) -> Unit = {},
) {
    val strings = LocalStrings.current
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var userError by remember { mutableStateOf<String?>(null) }
    var isLoadingUser by remember { mutableStateOf(true) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var linkedApps by remember { mutableStateOf<List<LinkedApplication>>(emptyList()) }
    var unlinkingAppId by remember { mutableStateOf<Int?>(null) }
    var unlinkError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        authRepository.getCurrentUser().fold(
            onSuccess = { profile ->
                userProfile = profile
                userError = null
            },
            onFailure = { throwable ->
                userError = strings.errorText(throwable, strings.loadUserFailed)
            },
        )
        isLoadingUser = false
        // Refreshed on every entry (e.g. after returning from the QR scanner).
        deviceLinkManager.listLinkedApplications().onSuccess {
            linkedApps = it
            onLinkedAppsChanged(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle) },
                colors = pushItTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back,
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
                text = strings.account,
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
                            LabeledValue(label = strings.email, value = userProfile!!.email)
                            userProfile!!.userkey?.let { key ->
                                Spacer(Modifier.height(8.dp))
                                LabeledValue(label = strings.userKey, value = key)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // Language section
            Text(
                text = strings.language,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppLanguage.entries.forEachIndexed { index, lang ->
                    SegmentedButton(
                        selected = lang == language,
                        onClick = { onLanguageChange(lang) },
                        shape = SegmentedButtonDefaults.itemShape(index, AppLanguage.entries.size),
                    ) {
                        Text(lang.code.uppercase())
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // Linked applications (recipient inbox): one per app this device has
            // scanned. Each can be unlinked individually; scanning adds another.
            Text(
                text = strings.linkedAppsSection,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            if (linkedApps.isEmpty()) {
                Text(
                    text = strings.notLinked,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                linkedApps.forEach { app ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(
                                enabled = unlinkingAppId != app.id,
                                onClick = {
                                    scope.launch {
                                        unlinkingAppId = app.id
                                        unlinkError = null
                                        deviceLinkManager.unlinkApplication(app.id).fold(
                                            onSuccess = {
                                                val remaining = linkedApps.filterNot { it.id == app.id }
                                                linkedApps = remaining
                                                // Drop the now-stale inbox folder reactively.
                                                onLinkedAppsChanged(remaining)
                                            },
                                            onFailure = { unlinkError = strings.errorText(it, strings.unlinkFailed) },
                                        )
                                        unlinkingAppId = null
                                    }
                                },
                            ) {
                                if (unlinkingAppId == app.id) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(strings.unlinkApp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            unlinkError?.let {
                Spacer(Modifier.height(8.dp))
                ErrorBanner(it)
            }
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onNavigateToQrScanner,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.scanQr)
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
                    Text(strings.logout)
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
