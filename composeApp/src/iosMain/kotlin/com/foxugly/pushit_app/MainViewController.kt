package com.foxugly.pushit_app

import androidx.compose.ui.window.ComposeUIViewController
import com.foxugly.pushit_app.data.storage.TokenStorage
import com.foxugly.pushit_app.platform.FcmTokenProvider

fun MainViewController() = ComposeUIViewController {
    App(
        tokenStorage = TokenStorage(),
        fcmTokenProvider = FcmTokenProvider.instance,
    )
}