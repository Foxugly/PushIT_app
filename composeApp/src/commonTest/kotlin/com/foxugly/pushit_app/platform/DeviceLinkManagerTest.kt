package com.foxugly.pushit_app.platform

import com.foxugly.pushit_app.FakeFcmTokenSource
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val jsonHeader = headersOf(HttpHeaders.ContentType, "application/json")

private const val IDENTIFY_OK =
    """{"status":"ok","device_id":1,"device_created":false,
        "linked_applications":[{"id":2,"name":"App","description":"","is_active":true,"linked_at":"2026-01-01T00:00:00Z"}]}"""

class DeviceLinkManagerTest {

    private fun manager(store: FakeTokenStore, fcm: FakeFcmTokenSource, engine: MockEngine) =
        DeviceLinkManager(PushItApi(store, baseUrl = "https://test/api/v1/", engine = engine), store, fcm)

    @Test
    fun identifySkippedWithoutAccessToken() = runTest {
        var hit = false
        val engine = MockEngine { hit = true; respond(IDENTIFY_OK, HttpStatusCode.OK, jsonHeader) }
        val result = manager(FakeTokenStore(), FakeFcmTokenSource("fcm"), engine).identify()

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull(), "no access token → no identify payload")
        assertFalse(hit, "must not call the network without an access token")
    }

    @Test
    fun identifySkippedWithoutFcmToken() = runTest {
        var hit = false
        val engine = MockEngine { hit = true; respond(IDENTIFY_OK, HttpStatusCode.OK, jsonHeader) }
        val result = manager(FakeTokenStore(access = "a"), FakeFcmTokenSource(token = null), engine).identify()

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        assertFalse(hit, "must not call the network without an FCM token")
    }

    @Test
    fun syncWithoutAppTokenIdentifiesAndReturnsLinkedApps() = runTest {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            respond(IDENTIFY_OK, HttpStatusCode.OK, jsonHeader)
        }
        // access + fcm present, but no app token → identify only, no link.
        val result = manager(FakeTokenStore(access = "a"), FakeFcmTokenSource("fcm"), engine)
            .syncAuthenticatedDevice()

        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        val state = result.getOrNull()
        assertEquals(1, state?.deviceId)
        assertEquals(1, state?.linkedApplications?.size)
        assertTrue(paths.all { it.endsWith("/devices/identify/") }, "no /devices/link/ without an app token")
    }

    @Test
    fun unlinkClearsLocalAppTokenOnServerSuccess() = runTest {
        val store = FakeTokenStore(access = "a", app = "apt_x")
        val engine = MockEngine {
            respond("""{"status":"ok","device_id":1,"application_id":2,"unlinked":true}""", HttpStatusCode.OK, jsonHeader)
        }
        val result = manager(store, FakeFcmTokenSource("fcm"), engine).unlinkCurrentDevice()

        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals(true, result.getOrNull())
        assertNull(store.getAppToken(), "local app token must be cleared after a server unlink")
    }

    @Test
    fun unlinkIsNoOpWithoutAppToken() = runTest {
        var hit = false
        val engine = MockEngine { hit = true; respond("", HttpStatusCode.OK) }
        val result = manager(FakeTokenStore(access = "a"), FakeFcmTokenSource("fcm"), engine).unlinkCurrentDevice()

        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrNull())
        assertFalse(hit, "nothing linked → no network call")
    }

    @Test
    fun unlinkKeepsLocalAppTokenOnServerFailure() = runTest {
        val store = FakeTokenStore(access = "a", app = "apt_x")
        val engine = MockEngine { respond("""{"detail":"boom"}""", HttpStatusCode.InternalServerError, jsonHeader) }
        val result = manager(store, FakeFcmTokenSource("fcm"), engine).unlinkCurrentDevice()

        assertTrue(result.isFailure)
        assertEquals("apt_x", store.getAppToken(), "keep the app token so the user can retry")
    }

    @Test
    fun stopObservingDetachesTheCallback() {
        val fcm = FakeFcmTokenSource("fcm")
        val engine = MockEngine { respond(IDENTIFY_OK, HttpStatusCode.OK, jsonHeader) }
        val mgr = manager(FakeTokenStore(access = "a"), fcm, engine)

        var observed = 0
        mgr.startObservingTokenChanges { observed++ }
        fcm.emit("rotated")
        assertEquals(1, observed, "observer should fire on token change")

        mgr.stopObservingTokenChanges()
        assertTrue(fcm.observerDetached)
        fcm.emit("again")
        assertEquals(1, observed, "no more callbacks after stopObserving")
    }
}
