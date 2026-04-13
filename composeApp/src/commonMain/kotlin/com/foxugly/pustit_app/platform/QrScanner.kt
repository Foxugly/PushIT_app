package com.foxugly.pustit_app.platform

import androidx.compose.runtime.Composable

@Composable
expect fun QrScannerView(
    onQrCodeScanned: (String) -> Unit,
    onError: (String) -> Unit,
)
