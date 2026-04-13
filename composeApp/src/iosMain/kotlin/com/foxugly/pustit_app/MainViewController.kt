package com.foxugly.pustit_app

import androidx.compose.ui.window.ComposeUIViewController
import com.foxugly.pustit_app.data.storage.TokenStorage
import com.foxugly.pustit_app.platform.FcmTokenProvider

fun MainViewController() = ComposeUIViewController {
    App(
        tokenStorage = TokenStorage(),
        fcmTokenProvider = FcmTokenProvider.instance,
    )
}