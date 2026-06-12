package com.foxugly.pushit_app.data.repository

import com.foxugly.pushit_app.FakeTokenStore
import com.foxugly.pushit_app.data.api.PushItApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val jsonHeader = headersOf(HttpHeaders.ContentType, "application/json")

class NotificationRepositoryTest {

    @Test
    fun getNotificationsPassesPageAndParsesList() = runTest {
        var requestedPage: String? = null
        val engine = MockEngine { request ->
            requestedPage = request.url.parameters["page"]
            respond(
                """{"count":1,"next":"https://x/?page=3","previous":null,
                    "results":[{"id":42,"title":"t","message":"m","status":"sent","created_at":"2026-01-01T00:00:00Z"}]}""",
                HttpStatusCode.OK, jsonHeader,
            )
        }
        val repo = NotificationRepository(PushItApi(FakeTokenStore(access = "a"), baseUrl = "https://test/api/v1/", engine = engine))

        val result = repo.getNotifications(page = 2)

        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals("2", requestedPage)
        assertEquals(1, result.getOrNull()?.results?.size)
        assertEquals(42, result.getOrNull()?.results?.first()?.id)
        assertEquals("https://x/?page=3", result.getOrNull()?.next)
    }

    @Test
    fun getNotificationReturnsSingleItem() = runTest {
        val engine = MockEngine {
            respond(
                """{"id":7,"title":"Hi","message":"body","status":"sent","created_at":"2026-01-01T00:00:00Z"}""",
                HttpStatusCode.OK, jsonHeader,
            )
        }
        val repo = NotificationRepository(PushItApi(FakeTokenStore(access = "a"), baseUrl = "https://test/api/v1/", engine = engine))

        val result = repo.getNotification(7)

        assertTrue(result.isSuccess)
        assertEquals("Hi", result.getOrNull()?.title)
    }

    @Test
    fun getNotificationsSurfacesServerError() = runTest {
        val engine = MockEngine { respond("""{"detail":"boom"}""", HttpStatusCode.InternalServerError, jsonHeader) }
        val repo = NotificationRepository(PushItApi(FakeTokenStore(access = "a"), baseUrl = "https://test/api/v1/", engine = engine))

        assertTrue(repo.getNotifications().isFailure)
    }
}
