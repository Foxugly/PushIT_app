package com.foxugly.pushit_app.ui.notifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.data.api.Notification
import com.foxugly.pushit_app.ui.components.ErrorBanner
import com.foxugly.pushit_app.ui.i18n.LocalStrings
import com.foxugly.pushit_app.ui.i18n.errorText
import com.foxugly.pushit_app.ui.i18n.relativeTime
import com.foxugly.pushit_app.ui.theme.pushItTopAppBarColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationListScreen(
    inbox: InboxStore,
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToFolder: (Int?, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    refreshTrigger: Int = 0,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var isInitialLoad by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val strings = LocalStrings.current

    suspend fun refresh() {
        inbox.refresh().onFailure { error = strings.errorText(it, strings.loadNotificationsFailed) }
        if (inbox.notifications.isNotEmpty()) error = null
        isInitialLoad = false
    }

    LaunchedEffect(refreshTrigger) { refresh() }

    val unread = inbox.unread
    val folders = inbox.folders

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.notificationsTitle) },
                colors = pushItTopAppBarColors(),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = strings.settingsAction)
                    }
                },
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = inbox.loading,
            onRefresh = { scope.launch { refresh() } },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            when {
                isInitialLoad && inbox.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                inbox.notifications.isEmpty() && folders.isEmpty() && error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ErrorBanner(error!!)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { scope.launch { refresh() } }) { Text(strings.retry) }
                    }
                }
                inbox.notifications.isEmpty() && folders.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = strings.noNotifications,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Recent window may be empty while older messages exist.
                        if (!inbox.isAllLoaded) {
                            Spacer(Modifier.height(12.dp))
                            LoadOlderButton(
                                loading = inbox.loading,
                                onClick = {
                                    scope.launch {
                                        inbox.loadOlder().onFailure {
                                            error = strings.errorText(it, strings.loadNotificationsFailed)
                                        }
                                    }
                                },
                            )
                        }
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

                        // ── Unread section ──
                        item { SectionHeader("${strings.inboxUnread} (${unread.size})") }
                        if (unread.isEmpty()) {
                            item {
                                Text(
                                    text = strings.noUnread,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        } else {
                            items(unread, key = { "u-${it.id}" }) { notification ->
                                NotificationRow(
                                    notification = notification,
                                    read = false,
                                    onClick = { onNavigateToDetail(notification.id) },
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }

                        // ── Folders (one per app) ──
                        item {
                            Spacer(Modifier.height(8.dp))
                            SectionHeader(strings.inboxFolders)
                        }
                        items(folders, key = { "f-${it.applicationId}" }) { folder ->
                            FolderRow(
                                folder = folder,
                                inbox = inbox,
                                onClick = { onNavigateToFolder(folder.applicationId, folder.name) },
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }

                        // ── Load older (drops the recent-window bound) ──
                        if (!inbox.isAllLoaded) {
                            item {
                                LoadOlderButton(
                                    loading = inbox.loading,
                                    onClick = {
                                        scope.launch {
                                            inbox.loadOlder().onFailure {
                                                error = strings.errorText(it, strings.loadNotificationsFailed)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadOlderButton(loading: Boolean, onClick: () -> Unit) {
    val strings = LocalStrings.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick, enabled = !loading) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(strings.loadOlder)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun FolderRow(folder: InboxFolder, inbox: InboxStore, onClick: () -> Unit) {
    val logo = rememberLogo(folder.logoUrl, inbox)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (logo != null) {
                Image(
                    bitmap = logo,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = folder.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                text = "${folder.total}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (folder.unreadCount > 0) {
            Badge { Text("${folder.unreadCount}") }
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun rememberLogo(url: String?, inbox: InboxStore): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = if (url.isNullOrBlank()) null else inbox.loadImage(url)
    }
    return bitmap
}

/** A single notification row. Unread rows are emphasized (bold + dot). */
@Composable
internal fun NotificationRow(notification: Notification, read: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!read) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (read) FontWeight.Normal else FontWeight.SemiBold,
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
                text = LocalStrings.current.relativeTime(notification.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

