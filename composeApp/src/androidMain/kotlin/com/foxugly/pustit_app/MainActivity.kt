package com.foxugly.pustit_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.foxugly.pustit_app.data.storage.TokenStorage
import com.foxugly.pustit_app.platform.FcmTokenProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val tokenStorage = TokenStorage(this)
        val fcmTokenProvider = FcmTokenProvider.instance

        setContent {
            App(
                tokenStorage = tokenStorage,
                fcmTokenProvider = fcmTokenProvider,
            )
        }
    }
}