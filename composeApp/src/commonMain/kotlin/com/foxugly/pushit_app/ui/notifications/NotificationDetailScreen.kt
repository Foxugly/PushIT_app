package com.foxugly.pushit_app.ui.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.platform.formatLocalFull
import com.foxugly.pushit_app.ui.i18n.LocalStrings
import com.foxugly.pushit_app.ui.theme.pushItTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDetailScreen(
    notificationId: Int,
    inbox: InboxStore,
    onNavigateBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val notification = inbox.find(notificationId)

    // Opened via a deep-link (app was killed): the inbox may not hold this message
    // yet (no refresh, stale window, or FCM token not ready). Fetch it directly by
    // id — fetchById merges it into the inbox, so find() then returns it reactively.
    var fetching by remember(notificationId) { mutableStateOf(false) }
    LaunchedEffect(notificationId) {
        if (inbox.find(notificationId) == null) {
            fetching = true
            inbox.fetchById(notificationId)
            fetching = false
        }
    }

    // Opening a notification marks it read (it leaves the "unread" section) and
    // reports an "opened" receipt to the server (best-effort). Guard on presence:
    // a deep-link can land here before the message is known; only act once it is.
    LaunchedEffect(notificationId, notification != null) {
        if (notification != null) {
            inbox.markRead(notificationId)
            inbox.confirmOpened(notificationId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.notificationTitle) },
                colors = pushItTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
            )
        },
    ) { paddingValues ->
        if (notification == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                // Still loading (e.g. opened via a deep-link, fetching by id) →
                // spinner; otherwise the message is genuinely absent.
                if (inbox.loading || fetching) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = strings.loadNotificationFailed,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(text = notification.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            StatusBadge(status = notification.status)
            Spacer(Modifier.height(16.dp))
            Text(text = notification.message, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))

            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            LabeledTimestamp(label = strings.createdLabel, timestamp = notification.createdAt)
            notification.sentAt?.let { sentAt ->
                Spacer(Modifier.height(8.dp))
                LabeledTimestamp(label = strings.sentLabel, timestamp = sentAt)
            }

            Spacer(Modifier.height(24.dp))
            // Actions: mark unread (returns to the unread section) and delete (hide).
            OutlinedButton(
                onClick = {
                    inbox.markUnread(notification.id)
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.markUnread)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    inbox.dismiss(notification.id)
                    onNavigateBack()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.deleteAction)
            }
        }
    }
}

@Composable
private fun LabeledTimestamp(label: String, timestamp: String) {
    val strings = LocalStrings.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatDetailTimestamp(timestamp, strings.timestampAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDetailTimestamp(isoTimestamp: String, at: String): String =
    formatLocalFull(isoTimestamp, at)
