package com.foxugly.pushit_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.diagnostics.AppLogger
import com.foxugly.pushit_app.data.storage.TokenStorage
import com.foxugly.pushit_app.platform.FcmTokenProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppLogger.info(TAG, "MainActivity.onCreate started")
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.error(TAG, "Uncaught exception on ${thread.name}", throwable)
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tokenStorage = try {
            TokenStorage(this).also {
                AppLogger.info(TAG, "TokenStorage initialized")
            }
        } catch (throwable: Throwable) {
            AppLogger.error(TAG, "TokenStorage initialization failed", throwable)
            setContent {
                FatalStartupScreen(
                    title = "Startup failed",
                    details = throwable.message ?: throwable::class.simpleName.orEmpty(),
                    onRetry = { recreate() },
                )
            }
            return
        }

        val fcmTokenProvider = FcmTokenProvider.instance.also {
            it.refreshToken()
        }

        setContent {
            var refreshTrigger by remember { mutableStateOf(0) }

            DisposableEffect(Unit) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        AppLogger.info(TAG, "Notification broadcast received")
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
                    runCatching { unregisterReceiver(receiver) }
                        .onFailure { AppLogger.warn(TAG, "Notification receiver unregister failed", it) }
                }
            }

            App(
                tokenStorage = tokenStorage,
                fcmTokenProvider = fcmTokenProvider,
                externalRefreshTrigger = refreshTrigger,
            )
        }
    }

    companion object {
        private const val TAG = "PushIT/MainActivity"
    }
}

@Composable
private fun FatalStartupScreen(
    title: String,
    details: String,
    onRetry: () -> Unit,
) {
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Text(text = details, modifier = Modifier.padding(vertical = 16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
