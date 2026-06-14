package com.foxugly.pushit_app.ui.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.foxugly.pushit_app.data.api.Notification
import com.foxugly.pushit_app.data.repository.NotificationRepository
import com.foxugly.pushit_app.data.storage.TokenStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class InboxLocalState(
    val read: Set<Int> = emptySet(),
    val dismissed: Set<Int> = emptySet(),
)

/** A per-application folder of the inbox, with its unread count. */
data class InboxFolder(
    val applicationId: Int?,
    val name: String,
    val unreadCount: Int,
    val total: Int,
)

/**
 * Holds the inbox state shared across the list / folder / detail screens:
 * the fetched notifications plus the LOCAL read / dismissed sets (persisted in
 * [TokenStorage] as a JSON blob — the backend has no per-device read state yet).
 *
 * Backed by Compose snapshot state, so reading [unread] / [folders] / [isRead]
 * in a composable recomposes it when a message is opened, dismissed or marked
 * unread on any screen.
 */
class InboxStore(
    private val repository: NotificationRepository,
    private val storage: TokenStorage,
) {
    private val json = Json { ignoreUnknownKeys = true }

    var notifications by mutableStateOf<List<Notification>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set

    private var readIds by mutableStateOf(emptySet<Int>())
    private var dismissedIds by mutableStateOf(emptySet<Int>())

    init {
        storage.getNotificationState()?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { json.decodeFromString<InboxLocalState>(raw) }.getOrNull()?.let {
                readIds = it.read
                dismissedIds = it.dismissed
            }
        }
    }

    /** Notifications still visible (not dismissed locally), newest first. */
    private val visible: List<Notification>
        get() = notifications.filter { it.id !in dismissedIds }

    fun isRead(id: Int): Boolean = id in readIds

    /** Unread across all apps — the inbox's top section. */
    val unread: List<Notification>
        get() = visible.filter { it.id !in readIds }

    /** One folder per application, ordered by name, each with its unread count. */
    val folders: List<InboxFolder>
        get() = visible
            .groupBy { it.applicationId }
            .map { (appId, items) ->
                InboxFolder(
                    applicationId = appId,
                    name = items.firstOrNull()?.applicationName ?: "—",
                    unreadCount = items.count { it.id !in readIds },
                    total = items.size,
                )
            }
            .sortedBy { it.name.lowercase() }

    fun notificationsForApp(applicationId: Int?): List<Notification> =
        visible.filter { it.applicationId == applicationId }

    fun find(id: Int): Notification? = notifications.firstOrNull { it.id == id }

    suspend fun refresh(): Result<Unit> {
        loading = true
        val result = repository.getNotifications()
        result.onSuccess { notifications = it }
        loading = false
        return result.map { }
    }

    fun markRead(id: Int) {
        if (id in readIds) return
        readIds = readIds + id
        persist()
    }

    fun markUnread(id: Int) {
        if (id !in readIds) return
        readIds = readIds - id
        persist()
    }

    fun dismiss(id: Int) {
        dismissedIds = dismissedIds + id
        readIds = readIds - id
        persist()
    }

    private fun persist() {
        // Prune to currently-known ids so the stored state can't grow unbounded.
        val known = notifications.map { it.id }.toSet()
        val read = if (known.isEmpty()) readIds else readIds intersect known
        val dismissed = if (known.isEmpty()) dismissedIds else dismissedIds intersect known
        storage.setNotificationState(json.encodeToString(InboxLocalState(read, dismissed)))
    }
}
