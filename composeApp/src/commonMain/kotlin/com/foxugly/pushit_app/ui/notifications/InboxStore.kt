package com.foxugly.pushit_app.ui.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import com.foxugly.pushit_app.data.api.LinkedApplication
import com.foxugly.pushit_app.data.api.Notification
import com.foxugly.pushit_app.data.repository.NotificationRepository
import com.foxugly.pushit_app.data.storage.TokenStorage
import com.foxugly.pushit_app.platform.FcmTokenSource
import com.foxugly.pushit_app.platform.isoUtcDaysAgo
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
    val logoUrl: String?,
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
    private val fcmTokenSource: FcmTokenSource,
) {
    private val json = Json { ignoreUnknownKeys = true }

    var notifications by mutableStateOf<List<Notification>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set

    // The inbox loads a recent window by default; "load older" drops the bound
    // to fetch the full history. Exposed so the UI can show/hide the button.
    private var allLoaded by mutableStateOf(false)
    val isAllLoaded: Boolean get() = allLoaded

    /** Apps this device is linked to (from device identify) — so a folder shows
     * even before any notification has been delivered for that app. */
    private var linkedApps by mutableStateOf<List<LinkedApplication>>(emptyList())

    fun updateLinkedApps(apps: List<LinkedApplication>) {
        linkedApps = apps
    }

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

    /**
     * One folder per application, ordered by name, each with its unread count.
     * Built from delivered notifications, then merged with the device's linked
     * apps so a folder appears even for a linked app with no notification yet.
     */
    val folders: List<InboxFolder>
        get() {
            val fromNotifications = visible
                .groupBy { it.applicationId }
                .map { (appId, items) ->
                    InboxFolder(
                        applicationId = appId,
                        name = items.firstOrNull()?.applicationName ?: "—",
                        logoUrl = items.firstNotNullOfOrNull { it.applicationLogo },
                        unreadCount = items.count { it.id !in readIds },
                        total = items.size,
                    )
                }
            val knownAppIds = fromNotifications.mapNotNull { it.applicationId }.toSet()
            val emptyFromLinks = linkedApps
                .filter { it.id !in knownAppIds }
                .map { app ->
                    InboxFolder(
                        applicationId = app.id,
                        name = app.name,
                        logoUrl = app.logo,
                        unreadCount = 0,
                        total = 0,
                    )
                }
            return (fromNotifications + emptyFromLinks).sortedBy { it.name.lowercase() }
        }

    fun notificationsForApp(applicationId: Int?): List<Notification> =
        visible.filter { it.applicationId == applicationId }

    fun find(id: Int): Notification? = notifications.firstOrNull { it.id == id }

    /** Fetch + decode an app logo image (best-effort; null on any failure). */
    suspend fun loadImage(url: String): ImageBitmap? =
        repository.getImageBytes(url).getOrNull()?.let { bytes ->
            runCatching { bytes.decodeToImageBitmap() }.getOrNull()
        }

    suspend fun refresh(): Result<Unit> {
        loading = true
        // The recipient inbox is keyed on this device's FCM push token. Without
        // one yet (token not provisioned), there's simply nothing to show.
        val pushToken = fcmTokenSource.getCurrentToken()
        // Recent window by default; full history once the user loads older.
        val sentSince = if (allLoaded) null else isoUtcDaysAgo(RECENT_WINDOW_DAYS)
        val result = if (pushToken == null) {
            Result.success(emptyList())
        } else {
            repository.getDeviceNotifications(pushToken, sentSince)
        }
        result.onSuccess { notifications = it }
        loading = false
        return result.map { }
    }

    /** Drop the recent-window bound and reload the full history. */
    suspend fun loadOlder(): Result<Unit> {
        val previous = allLoaded
        allLoaded = true
        val result = refresh()
        if (result.isFailure) allLoaded = previous
        return result
    }

    fun markRead(id: Int) {
        if (id in readIds) return
        readIds = readIds + id
        persist()
    }

    /** Mark every still-visible notification of one app as read. */
    fun markAllReadForApp(applicationId: Int?) {
        val ids = visible.filter { it.applicationId == applicationId }.map { it.id }
        if (ids.isEmpty()) return
        readIds = readIds + ids
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
        // Prune to known ids so stored state can't grow unbounded — but ONLY when
        // the full history is loaded. On the recent window, older ids are absent,
        // so pruning would drop their read/dismissed state and resurface them.
        val known = notifications.map { it.id }.toSet()
        val prune = allLoaded && known.isNotEmpty()
        val read = if (prune) readIds intersect known else readIds
        val dismissed = if (prune) dismissedIds intersect known else dismissedIds
        storage.setNotificationState(json.encodeToString(InboxLocalState(read, dismissed)))
    }

    private companion object {
        const val RECENT_WINDOW_DAYS = 30
    }
}
