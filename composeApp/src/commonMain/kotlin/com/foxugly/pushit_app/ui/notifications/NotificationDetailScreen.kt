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
import com.foxugly.pushit_app.data.api.Notification
import com.foxugly.pushit_app.data.repository.NotificationRepository
import com.foxugly.pushit_app.ui.components.ErrorBanner
import com.foxugly.pushit_app.ui.i18n.LocalStrings
import com.foxugly.pushit_app.ui.theme.pushItTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDetailScreen(
    notificationId: Int,
    notificationRepository: NotificationRepository,
    onNavigateBack: () -> Unit,
) {
    var notification by remember { mutableStateOf<Notification?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // Bump to re-run the loader (the Retry button) — the effect keys on it.
    var reloadKey by remember { mutableStateOf(0) }
    val strings = LocalStrings.current

    LaunchedEffect(notificationId, reloadKey) {
        isLoading = true
        error = null
        notificationRepository.getNotification(notificationId).fold(
            onSuccess = { result ->
                notification = result
                error = null
            },
            onFailure = { throwable ->
                error = throwable.message ?: strings.loadNotificationFailed
            },
        )
        isLoading = false
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null && notification == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ErrorBanner(error!!)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { reloadKey++ }) {
                            Text(strings.retry)
                        }
                    }
                }
                notification != null -> {
                    NotificationDetailContent(
                        notification = notification!!,
                        error = error,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationDetailContent(
    notification: Notification,
    error: String?,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        error?.let {
            ErrorBanner(it)
            Spacer(Modifier.height(12.dp))
        }

        // Title
        Text(
            text = notification.title,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))

        // Status badge
        StatusBadge(status = notification.status)
        Spacer(Modifier.height(16.dp))

        // Message body
        Text(
            text = notification.message,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))

        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // Timestamps
        LabeledTimestamp(label = strings.createdLabel, timestamp = notification.createdAt)

        notification.sentAt?.let { sentAt ->
            Spacer(Modifier.height(8.dp))
            LabeledTimestamp(label = strings.sentLabel, timestamp = sentAt)
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

/**
 * Formats an ISO-8601 timestamp for display in the detail screen.
 * Input format: "2024-01-15T10:30:00Z" or "2024-01-15T10:30:00.000000Z"
 */
private fun formatDetailTimestamp(isoTimestamp: String, at: String): String {
    return try {
        val parts = isoTimestamp.split("T")
        if (parts.size < 2) return isoTimestamp
        val date = parts[0]
        val timePart = parts[1].substringBefore("Z").substringBefore("+").take(8) // "HH:mm:ss"
        "$date $at $timePart UTC"
    } catch (_: Exception) {
        isoTimestamp
    }
}
