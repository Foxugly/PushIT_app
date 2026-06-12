package com.foxugly.pushit_app.ui.qrscanner

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.data.storage.TokenStorage
import com.foxugly.pushit_app.platform.QrScannerView
import com.foxugly.pushit_app.ui.components.ErrorBanner

private const val APP_TOKEN_PREFIX = "apt_"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    tokenStorage: TokenStorage,
    onTokenScanned: () -> Unit,
    onBack: () -> Unit,
) {
    var manualMode by remember { mutableStateOf(false) }
    var manualToken by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan App Token") },
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
                .padding(paddingValues),
        ) {
            if (!manualMode) {
                // Camera preview area fills remaining space
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    QrScannerView(
                        onQrCodeScanned = { scanned ->
                            error = null
                            if (scanned.startsWith(APP_TOKEN_PREFIX)) {
                                tokenStorage.setAppToken(scanned)
                                onTokenScanned()
                            } else {
                                error = "Invalid QR code. Token must start with \"$APP_TOKEN_PREFIX\"."
                            }
                        },
                        onError = { errorMessage ->
                            error = errorMessage
                        },
                    )
                }

                error?.let { msg ->
                    ErrorBanner(
                        msg,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                    )
                }

                TextButton(
                    onClick = {
                        error = null
                        manualMode = true
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp),
                ) {
                    Text("Enter token manually")
                }
            } else {
                // Manual entry mode
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Enter App Token",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = manualToken,
                        onValueChange = {
                            manualToken = it
                            error = null
                        },
                        label = { Text("Token (starts with apt_)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = error != null,
                        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    )
                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val trimmed = manualToken.trim()
                            if (trimmed.startsWith(APP_TOKEN_PREFIX)) {
                                tokenStorage.setAppToken(trimmed)
                                onTokenScanned()
                            } else {
                                error = "Token must start with \"$APP_TOKEN_PREFIX\"."
                            }
                        },
                        enabled = manualToken.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save Token")
                    }
                    Spacer(Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            manualToken = ""
                            error = null
                            manualMode = false
                        },
                    ) {
                        Text("Back to scanner")
                    }
                }
            }
        }
    }
}
