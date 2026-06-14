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
    fun getNotificationsParsesBareArray() = runTest {
        // /notifications/ is un-paginated: a bare JSON array.
        val engine = MockEngine {
            respond(
                """[{"id":42,"application_id":3,"application_name":"Acme","device_ids":[1],"title":"t","message":"m","status":"sent","created_at":"2026-01-01T00:00:00Z"}]""",
                HttpStatusCode.OK, jsonHeader,
            )
        }
        val repo = NotificationRepository(PushItApi(FakeTokenStore(access = "a"), baseUrl = "https://test/api/v1/", engine = engine))

        val result = repo.getNotifications()

        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals(1, result.getOrNull()?.size)
        assertEquals(42, result.getOrNull()?.first()?.id)
    }

    @Test
    fun getNotificationReturnsSingleItem() = runTest {
        val engine = MockEngine {
            respond(
                """{"id":7,"application_id":3,"application_name":"Acme","device_ids":[],"title":"Hi","message":"body","status":"sent","created_at":"2026-01-01T00:00:00Z"}""",
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

    @Test
    fun confirmOpenedPostsToTheOpenedEndpoint() = runTest {
        val methods = mutableListOf<String>()
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            methods += request.method.value
            paths += request.url.encodedPath
            respond("""{"status":"ok","opened_at":"2026-06-14T20:00:00Z"}""", HttpStatusCode.OK, jsonHeader)
        }
        val repo = NotificationRepository(PushItApi(FakeTokenStore(access = "a"), baseUrl = "https://test/api/v1/", engine = engine))

        val result = repo.confirmOpened(42, "fcm_tok")

        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals("POST", methods.single())
        assertTrue(paths.single().endsWith("/notifications/42/opened/"), paths.toString())
    }

    @Test
    fun confirmOpenedSurfacesFailureWithoutThrowing() = runTest {
        // Best-effort: a server error comes back as a failed Result, never an exception.
        val engine = MockEngine { respond("""{"detail":"nope"}""", HttpStatusCode.NotFound, jsonHeader) }
        val repo = NotificationRepository(PushItApi(FakeTokenStore(access = "a"), baseUrl = "https://test/api/v1/", engine = engine))

        assertTrue(repo.confirmOpened(99, "fcm_tok").isFailure)
    }
}
