package com.foxugly.pushit_app.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.data.api.Notification
import com.foxugly.pushit_app.data.repository.NotificationRepository
import com.foxugly.pushit_app.ui.components.ErrorBanner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationListScreen(
    notificationRepository: NotificationRepository,
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    refreshTrigger: Int = 0,
) {
    var notifications by remember { mutableStateOf<List<Notification>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(1) }
    var hasNextPage by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isInitialLoad by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    suspend fun loadPage(page: Int, replace: Boolean) {
        notificationRepository.getNotifications(page).fold(
            onSuccess = { response ->
                notifications = if (replace) response.results else notifications + response.results
                hasNextPage = response.next != null
                currentPage = page
                error = null
            },
            onFailure = { throwable ->
                error = throwable.message ?: "Failed to load notifications"
            },
        )
    }

    // Initial load and refresh-trigger response
    LaunchedEffect(refreshTrigger) {
        isRefreshing = true
        loadPage(page = 1, replace = true)
        isRefreshing = false
        isInitialLoad = false
    }

    // Infinite scroll: detect when near bottom
    val lastVisibleIndex by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }
    }
    LaunchedEffect(lastVisibleIndex) {
        val totalItems = notifications.size
        if (
            hasNextPage &&
            !isLoadingMore &&
            totalItems > 0 &&
            lastVisibleIndex >= totalItems - 4 // 3-item threshold
        ) {
            isLoadingMore = true
            loadPage(page = currentPage + 1, replace = false)
            isLoadingMore = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    loadPage(page = 1, replace = true)
                    isRefreshing = false
                }
            },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                isInitialLoad -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                notifications.isEmpty() && error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ErrorBanner(error!!)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            scope.launch {
                                isRefreshing = true
                                loadPage(page = 1, replace = true)
                                isRefreshing = false
                            }
                        }) {
                            Text("Retry")
                        }
                    }
                }
                notifications.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No notifications yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        error?.let {
                            item {
                                ErrorBanner(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            }
                        }

                        itemsIndexed(
                            items = notifications,
                            key = { _, notification -> notification.id },
                        ) { _, notification ->
                            NotificationListItem(
                                notification = notification,
                                onClick = { onNavigateToDetail(notification.id) },
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }

                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationListItem(
    notification: Notification,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            StatusBadge(status = notification.status)
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatTimestamp(notification.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Formats an ISO-8601 timestamp string into a short human-readable form.
 * Uses simple string slicing to stay multiplatform without requiring java.time.
 * Input format: "2024-01-15T10:30:00Z" or "2024-01-15T10:30:00.000000Z"
 */
private fun formatTimestamp(isoTimestamp: String): String {
    return try {
        // Take date and time parts: "2024-01-15T10:30"
        val parts = isoTimestamp.split("T")
        if (parts.size < 2) return isoTimestamp
        val date = parts[0] // "2024-01-15"
        val time = parts[1].take(5) // "10:30"
        "$date $time"
    } catch (_: Exception) {
        isoTimestamp
    }
}
