package com.foxugly.pustit_app.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun QrScannerView(
    onQrCodeScanned: (String) -> Unit,
    onError: (String) -> Unit,
) {
    // AVFoundation camera interop is complex in Kotlin/Native;
    // fall back to manual entry for now.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text("QR scanning requires the native camera.\nPlease use manual token entry.")
    }
    LaunchedEffect(Unit) {
        onError("Camera not available on this platform")
    }
}
