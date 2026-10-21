package com.foxugly.pushit_app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.ui.i18n.AppLanguage
import com.foxugly.pushit_app.ui.i18n.Strings
import com.foxugly.pushit_app.ui.i18n.stringsFor
import java.util.Locale
import com.foxugly.pushit_app.diagnostics.AppLogger
import com.foxugly.pushit_app.data.storage.TokenStorage
import com.foxugly.pushit_app.platform.AppContextHolder
import com.foxugly.pushit_app.platform.FcmTokenProvider

class MainActivity : ComponentActivity() {
    // Android 13+ requires runtime consent for POST_NOTIFICATIONS; without it
    // notificationManager.notify() is silently dropped. Registered before STARTED.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            AppLogger.info(TAG, "POST_NOTIFICATIONS permission granted=$granted")
        }

    // Notification id from a tapped push (deep-link target). Set from the launch
    // intent and from onNewIntent when the app is already running; consumed by
    // App() once it navigates to the message.
    private val deepLinkNotificationId = mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLogger.info(TAG, "MainActivity.onCreate started")
        installCrashLogger()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppContextHolder.app = applicationContext
        maybeRequestNotificationPermission()
        deepLinkNotificationId.value = readDeepLinkId(intent)

        val tokenStorage = try {
            TokenStorage(this).also {
                AppLogger.info(TAG, "TokenStorage initialized")
            }
        } catch (throwable: Throwable) {
            AppLogger.error(TAG, "TokenStorage initialization failed", throwable)
            // The saved language lives in the storage that just failed: use the device's.
            val strings = stringsFor(AppLanguage.resolve(null, Locale.getDefault().language))
            setContent {
                FatalStartupScreen(
                    strings = strings,
                    details = throwable.message ?: throwable::class.simpleName.orEmpty(),
                    onRetry = { recreate() },
                    // A corrupted Keystore key / prefs file never heals on retry:
                    // offer to wipe the local secure storage and start clean.
                    onResetStorage = {
                        TokenStorage.resetCorruptedStorage(applicationContext)
                        recreate()
                    },
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
                apiBaseUrl = PROD_API_BASE_URL,
                enableHttpLogging = BuildConfig.DEBUG,
                deepLinkNotificationId = deepLinkNotificationId.value,
                onDeepLinkConsumed = { deepLinkNotificationId.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep getIntent() current and surface the new deep-link to App().
        setIntent(intent)
        readDeepLinkId(intent)?.let { deepLinkNotificationId.value = it }
    }

    private fun readDeepLinkId(intent: Intent?): Int? =
        PushItFirebaseService.deepLinkNotificationId(this, intent)

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        private const val TAG = "PushIT/MainActivity"

        @Volatile
        private var crashLoggerInstalled = false

        /**
         * Log uncaught exceptions, then hand them to the previous default handler
         * (Sentry's when a DSN is built in — PushItApplication installs it first —
         * which itself chains to the platform's), so the process still dies and Play Vitals records the
         * crash. Swallowing them froze the app (dead main thread, then an ANR)
         * and hid the crash. Installed once per process: onCreate runs again on
         * every recreate(), and re-wrapping would chain our handler onto itself.
         */
        @Synchronized
        private fun installCrashLogger() {
            if (crashLoggerInstalled) return
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching { AppLogger.error(TAG, "Uncaught exception on ${thread.name}", throwable) }
                if (previous != null) {
                    previous.uncaughtException(thread, throwable)
                } else {
                    // No platform handler (never expected on Android): die anyway.
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            }
            crashLoggerInstalled = true
        }
        // Both release and debug builds talk to prod; debug only adds HTTP logging.
        private const val PROD_API_BASE_URL = "https://pushit-api.foxugly.com/api/v1/"
    }
}

@Composable
private fun FatalStartupScreen(
    strings: Strings,
    details: String,
    onRetry: () -> Unit,
    onResetStorage: () -> Unit,
) {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = strings.startupFailed, style = MaterialTheme.typography.headlineSmall)
            Text(text = details, modifier = Modifier.padding(vertical = 16.dp))
            Button(onClick = onRetry) {
                Text(strings.retry)
            }
            Text(
                text = strings.resetLocalStorageHint,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
            )
            OutlinedButton(onClick = onResetStorage) {
                Text(strings.resetLocalStorage)
            }
        }
    }
}
