package com.foxugly.pushit_app.ui.notifications

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import com.foxugly.pushit_app.data.api.LinkedApplication
import com.foxugly.pushit_app.data.api.Notification
import com.foxugly.pushit_app.data.repository.NotificationRepository
import com.foxugly.pushit_app.data.storage.InboxStateStore
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
 * the fetched notifications plus the LOCAL read / dismissed sets (persisted via
 * [InboxStateStore] as a JSON blob — the backend has no per-device read state yet).
 *
 * Backed by Compose snapshot state, so reading [unread] / [folders] / [isRead]
 * in a composable recomposes it when a message is opened, dismissed or marked
 * unread on any screen.
 */
class InboxStore(
    private val repository: NotificationRepository,
    private val storage: InboxStateStore,
    private val fcmTokenSource: FcmTokenSource,
) {
    private val json = Json { ignoreUnknownKeys = true }

    var notifications by mutableStateOf<List<Notification>>(emptyList())
        private set

    // In-flight load counter, not a single flag: overlapping loads (e.g. a deep-link
    // fetchById racing a window refresh) each inc/dec, so the spinner stays up until
    // ALL of them finish instead of the first one to return flipping it off.
    private var inFlight by mutableStateOf(0)
    val loading: Boolean get() = inFlight > 0

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

    // Decoded app logos cached by url, so a recycled list row reuses the bitmap
    // instead of re-fetching/re-decoding on every scroll. Snapshot-backed so a row
    // already in composition recomposes once its logo lands.
    private val logoCache = mutableStateMapOf<String, ImageBitmap>()

    init {
        storage.getNotificationState()?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { json.decodeFromString<InboxLocalState>(raw) }.getOrNull()?.let {
                readIds = it.read
                dismissedIds = it.dismissed
            }
        }
    }

    /** Notifications still visible (not dismissed locally), newest first.
     * Memoized: only recomputed when [notifications] or [dismissedIds] change. */
    private val visibleState = derivedStateOf {
        notifications.filter { it.id !in dismissedIds }
    }
    private val visible: List<Notification> get() = visibleState.value

    fun isRead(id: Int): Boolean = id in readIds

    /** Unread across all apps — the inbox's top section. Memoized over the
     * snapshot inputs (visible notifications + readIds) so a plain read in a
     * composable doesn't refilter the whole list on every recomposition. */
    private val unreadState = derivedStateOf {
        visible.filter { it.id !in readIds }
    }
    val unread: List<Notification> get() = unreadState.value

    /**
     * One folder per application, ordered by name, each with its unread count.
     * Built from delivered notifications, then merged with the device's linked
     * apps so a folder appears even for a linked app with no notification yet.
     * Memoized over (notifications, dismissedIds, readIds, linkedApps).
     */
    private val foldersState = derivedStateOf {
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
        (fromNotifications + emptyFromLinks).sortedBy { it.name.lowercase() }
    }
    val folders: List<InboxFolder> get() = foldersState.value

    fun notificationsForApp(applicationId: Int?): List<Notification> =
        visible.filter { it.applicationId == applicationId }

    fun find(id: Int): Notification? = notifications.firstOrNull { it.id == id }

    /**
     * Ensure notification [id] is available, fetching it directly when the inbox
     * list doesn't already hold it — e.g. opened via a deep-link after the app was
     * killed, before (or without) a window refresh that includes it, or while the
     * FCM push token isn't ready yet. The fetched message is merged into the
     * in-memory list (deduped by id, newest first) so it also appears in the inbox
     * and its app folder. Returns null only if the fetch fails.
     */
    suspend fun fetchById(id: Int): Notification? {
        find(id)?.let { return it }
        inFlight++
        try {
            val fetched = repository.getNotification(id).getOrNull()
            if (fetched != null && notifications.none { it.id == fetched.id }) {
                notifications = listOf(fetched) + notifications
            }
            return fetched
        } finally {
            inFlight--
        }
    }

    /**
     * Fetch + decode an app logo image (best-effort; null on any failure).
     * Decoded bitmaps are cached by url so list rows that scroll back into view
     * (recycled composables) get the logo synchronously instead of re-downloading.
     */
    suspend fun loadImage(url: String): ImageBitmap? {
        cachedLogo(url)?.let { return it }
        val bitmap = repository.getImageBytes(url).getOrNull()?.let { bytes ->
            runCatching { bytes.decodeToImageBitmap() }.getOrNull()
        }
        if (bitmap != null) logoCache[url] = bitmap
        return bitmap
    }

    /** Decoded logo for [url] if already cached, else null (caller fetches). */
    fun cachedLogo(url: String): ImageBitmap? = logoCache[url]

    suspend fun refresh(): Result<Unit> {
        inFlight++
        try {
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
            // Merge (union by id), not replace: a message fetched directly via a deep
            // link (fetchById) isn't necessarily in the server window, so a plain
            // replacement would evict it from the list and its folder. Server results
            // win on conflict; locally-held extras are kept. Newest first (by id desc,
            // a monotonic server sequence) so order is stable across both sources.
            result.onSuccess { fresh ->
                val freshIds = fresh.map { it.id }.toSet()
                val keptLocal = notifications.filter { it.id !in freshIds }
                notifications = (fresh + keptLocal).sortedByDescending { it.id }
            }
            return result.map { }
        } finally {
            inFlight--
        }
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

    /**
     * Best-effort recipient receipt: tell the server the user opened notification
     * [id] on this device. Fire-and-forget — no token, no inbox state change, and
     * failures are swallowed (logged in the repository); it must never disrupt the
     * reading flow.
     */
    suspend fun confirmOpened(id: Int) {
        val pushToken = fcmTokenSource.getCurrentToken() ?: return
        repository.confirmOpened(id, pushToken)
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
