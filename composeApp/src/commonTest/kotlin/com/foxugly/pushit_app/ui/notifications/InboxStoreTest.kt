package com.foxugly.pushit_app.ui.notifications

import com.foxugly.pushit_app.FakeFcmTokenSource
import com.foxugly.pushit_app.FakeInboxStateStore
import com.foxugly.pushit_app.FakeTokenStore
import com.foxugly.pushit_app.data.api.PushItApi
import com.foxugly.pushit_app.data.repository.NotificationRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val jsonHeader = headersOf(HttpHeaders.ContentType, "application/json")

private fun notification(id: Int, appId: Int = 3, appName: String = "Acme") =
    """{"id":$id,"application_id":$appId,"application_name":"$appName","device_ids":[1],"title":"t$id","message":"m$id","status":"sent","created_at":"2026-01-01T00:00:00Z"}"""

class InboxStoreTest {

    private fun store(engine: MockEngine): InboxStore {
        val api = PushItApi(FakeTokenStore(access = "a"), baseUrl = "https://test/api/v1/", engine = engine)
        return InboxStore(NotificationRepository(api), FakeInboxStateStore(), FakeFcmTokenSource("fcm"))
    }

    @Test
    fun refreshMergesDeepLinkFetchedMessageInsteadOfEvictingIt() = runTest {
        // A deep-link fetched message (id 99) is NOT in the server window; a refresh
        // that returns only [1, 2] must keep 99 (union by id), not drop it.
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/notifications/99/") ->
                    respond(notification(99), HttpStatusCode.OK, jsonHeader)
                request.url.encodedPath.endsWith("/notifications/device/") ->
                    respond("[${notification(2)},${notification(1)}]", HttpStatusCode.OK, jsonHeader)
                else -> respond("[]", HttpStatusCode.OK, jsonHeader)
            }
        }
        val inbox = store(engine)

        val fetched = inbox.fetchById(99)
        assertNotNull(fetched, "deep-link fetch should succeed")
        assertEquals(99, inbox.find(99)?.id)

        val result = inbox.refresh()
        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")

        // 99 survives the refresh, alongside the server window.
        assertNotNull(inbox.find(99), "deep-link message must NOT be evicted by refresh")
        val ids = inbox.notifications.map { it.id }
        assertTrue(ids.containsAll(listOf(99, 2, 1)), "expected union, got $ids")
        // Newest first (by id desc).
        assertEquals(listOf(99, 2, 1), ids)
    }

    @Test
    fun refreshLetsServerResultsWinOnConflict() = runTest {
        // When the server window includes an id the inbox already holds, the server
        // copy wins (no duplicate row).
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/notifications/5/") ->
                    respond(notification(5, appName = "Old"), HttpStatusCode.OK, jsonHeader)
                request.url.encodedPath.endsWith("/notifications/device/") ->
                    respond("[${notification(5, appName = "New")}]", HttpStatusCode.OK, jsonHeader)
                else -> respond("[]", HttpStatusCode.OK, jsonHeader)
            }
        }
        val inbox = store(engine)

        inbox.fetchById(5)
        inbox.refresh()

        assertEquals(1, inbox.notifications.count { it.id == 5 }, "no duplicate after merge")
        assertEquals("New", inbox.find(5)?.applicationName, "server copy wins on conflict")
    }
}
