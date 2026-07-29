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
import com.foxugly.pushit_app.ui.i18n.LocalStrings
import com.foxugly.pushit_app.ui.theme.pushItTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    tokenStorage: TokenStorage,
    onCodeScanned: () -> Unit,
    onBack: () -> Unit,
) {
    var manualMode by remember { mutableStateOf(false) }
    var manualCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val strings = LocalStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.scanEnrolmentCode) },
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
                            if (looksLikeEnrolmentCode(scanned)) {
                                tokenStorage.setEnrolmentCode(scanned)
                                onCodeScanned()
                            } else {
                                error = strings.invalidQrCode
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
                    Text(strings.enterManually)
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
                        text = strings.enterEnrolmentCode,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = manualCode,
                        onValueChange = {
                            manualCode = it
                            error = null
                        },
                        label = { Text(strings.codeFieldLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = error != null,
                        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    )
                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val trimmed = manualCode.trim()
                            if (looksLikeEnrolmentCode(trimmed)) {
                                tokenStorage.setEnrolmentCode(trimmed)
                                onCodeScanned()
                            } else {
                                error = strings.codeMustStartWith
                            }
                        },
                        enabled = manualCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.saveCode)
                    }
                    Spacer(Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            manualCode = ""
                            error = null
                            manualMode = false
                        },
                    ) {
                        Text(strings.backToScanner)
                    }
                }
            }
        }
    }
}
