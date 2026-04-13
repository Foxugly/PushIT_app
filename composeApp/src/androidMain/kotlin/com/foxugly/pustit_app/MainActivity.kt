package com.foxugly.pustit_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.foxugly.pustit_app.data.storage.TokenStorage
import com.foxugly.pustit_app.platform.FcmTokenProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val tokenStorage = TokenStorage(this)
        val fcmTokenProvider = FcmTokenProvider.instance

        setContent {
            var refreshTrigger by remember { mutableStateOf(0) }

            DisposableEffect(Unit) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        refreshTrigger++
                    }
                }
                val filter = IntentFilter(PushItFirebaseService.ACTION_NOTIFICATION_RECEIVED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(receiver, filter)
                }

                onDispose {
                    unregisterReceiver(receiver)
                }
            }

            App(
                tokenStorage = tokenStorage,
                fcmTokenProvider = fcmTokenProvider,
                externalRefreshTrigger = refreshTrigger,
            )
        }
    }
}
