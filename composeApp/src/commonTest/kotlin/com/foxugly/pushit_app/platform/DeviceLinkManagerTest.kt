package com.foxugly.pushit_app.platform

import com.foxugly.pushit_app.FakeFcmTokenSource
import com.foxugly.pushit_app.FakeTokenStore
import com.foxugly.pushit_app.data.api.PushItApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.ktor.utils.io.errors.IOException
import io.ktor.client.engine.mock.toByteArray

private val jsonHeader = headersOf(HttpHeaders.ContentType, "application/json")

private const val IDENTIFY_OK =
    """{"status":"ok","device_id":1,"device_created":false,
        "linked_applications":[{"id":2,"name":"App","description":"","is_active":true,"linked_at":"2026-01-01T00:00:00Z"}]}"""

private const val IDENTIFY_NO_LINKS =
    """{"status":"ok","device_id":1,"device_created":false,"linked_applications":[]}"""

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceLinkManagerTest {

    private fun manager(
        store: FakeTokenStore,
        fcm: FakeFcmTokenSource,
        engine: MockEngine,
        scope: CoroutineScope? = null,
    ) = DeviceLinkManager(
        PushItApi(store, baseUrl = "https://test/api/v1/", engine = engine),
        store,
        fcm,
        scope ?: CoroutineScope(UnconfinedTestDispatcher()),
    )

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
    fun syncWithoutEnrolmentCodeIdentifiesAndReturnsLinkedApps() = runTest {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            respond(IDENTIFY_OK, HttpStatusCode.OK, jsonHeader)
        }
        // access + fcm present, but no enrolment code → identify only, no link.
        val result = manager(FakeTokenStore(access = "a"), FakeFcmTokenSource("fcm"), engine)
            .syncAuthenticatedDevice()

        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        val state = result.getOrNull()
        assertEquals(1, state?.deviceId)
        assertEquals(1, state?.linkedApplications?.size)
        assertTrue(paths.all { it.endsWith("/devices/identify/") }, "no /devices/link/ without an enrolment code")
    }

    @Test
    fun syncSkipsRedundantRelinkWhenAlreadyLinked() = runTest {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            respond(IDENTIFY_OK, HttpStatusCode.OK, jsonHeader)
        }
        // access + fcm + a stored enrolment code, and identify shows the device is already linked.
        val result = manager(FakeTokenStore(access = "a", app = "apt_x"), FakeFcmTokenSource("fcm"), engine)
            .syncAuthenticatedDevice()

        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals(1, result.getOrNull()?.linkedApplications?.size)
        assertTrue(
            paths.all { it.endsWith("/devices/identify/") },
            "an already-linked device must not re-link on every sync (no /devices/link/ call)",
        )
    }

    @Test
    fun syncSwallowsAndForgetsRefusedEnrolmentCodeWhenNotYetLinked() = runTest {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            if (request.url.encodedPath.endsWith("/devices/link/")) {
                respond(
                    """{"detail":"Invalid app token.","code":"app_token_invalid"}""",
                    HttpStatusCode.Forbidden,
                    jsonHeader,
                )
            } else {
                respond(IDENTIFY_NO_LINKS, HttpStatusCode.OK, jsonHeader)
            }
        }
        val store = FakeTokenStore(access = "a", refresh = "r", app = "apk_stale")
        // Not yet linked + a dead stored code → the automatic link gets 403, which is non-fatal.
        val result = manager(store, FakeFcmTokenSource("fcm"), engine).syncAuthenticatedDevice()

        assertTrue(result.isSuccess, "a refused enrolment code on an automatic sync must be non-fatal")
        assertEquals(1, result.getOrNull()?.deviceId)
        assertEquals(0, result.getOrNull()?.linkedApplications?.size)
        assertNull(store.getEnrolmentCode(), "a refused code must be forgotten, not retried on every launch")
        assertFalse(paths.any { it.endsWith("/auth/refresh/") }, "a refused enrolment code is not a session problem")
    }

    @Test
    fun legacy401EnrolmentRejectionDoesNotRefreshTheSession() = runTest {
        // Servers before the 403 switch answered 401 app_token_invalid: that must not
        // rotate the JWT pair and replay the link.
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            if (request.url.encodedPath.endsWith("/devices/link/")) {
                respond("""{"detail":"Invalid app token.","code":"app_token_invalid"}""", HttpStatusCode.Unauthorized, jsonHeader)
            } else {
                respond(IDENTIFY_NO_LINKS, HttpStatusCode.OK, jsonHeader)
            }
        }
        val store = FakeTokenStore(access = "a", refresh = "r", app = "apk_dead")

        val result = manager(store, FakeFcmTokenSource("fcm"), engine).linkWithStoredEnrolmentCode()

        assertTrue(result.isFailure, "an explicit link still surfaces the refusal")
        assertEquals(1, paths.count { it.endsWith("/devices/link/") }, "no replay")
        assertFalse(paths.any { it.endsWith("/auth/refresh/") })
        assertEquals("r", store.getRefreshToken())
        assertNull(store.getEnrolmentCode())
    }

    @Test
    fun identifyHandsTheDeviceOverAfterAnFcmRotation() = runTest {
        val bodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            bodies += request.body.toByteArray().decodeToString()
            respond(IDENTIFY_OK, HttpStatusCode.OK, jsonHeader)
        }
        val store = FakeTokenStore(access = "a", lastPush = "fcm-old")
        val fcm = FakeFcmTokenSource("fcm-new")
        val mgr = manager(store, fcm, engine)

        assertTrue(mgr.identify().isSuccess)
        assertTrue(bodies.single().contains("\"previous_push_token\":\"fcm-old\""), bodies.single())
        assertTrue(bodies.single().contains("\"push_token\":\"fcm-new\""), bodies.single())
        assertEquals("fcm-new", store.getLastPushToken(), "the new token becomes the registered one")

        // Next identify with the same token: no hand-over any more.
        assertTrue(mgr.identify().isSuccess)
        assertFalse(bodies.last().contains("previous_push_token"), bodies.last())
    }

    @Test
    fun failedHandOverKeepsThePreviousTokenForTheNextTry() = runTest {
        val engine = MockEngine { throw IOException("offline") }
        val store = FakeTokenStore(access = "a", lastPush = "fcm-old")

        assertTrue(manager(store, FakeFcmTokenSource("fcm-new"), engine).identify().isFailure)
        assertEquals("fcm-old", store.getLastPushToken())
    }

    @Test
    fun observedRotationResyncsWithThePreviousToken() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val bodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            bodies += request.body.toByteArray().decodeToString()
            respond(IDENTIFY_OK, HttpStatusCode.OK, jsonHeader)
        }
        val store = FakeTokenStore(access = "a", app = "apk_x")
        val fcm = FakeFcmTokenSource("fcm-1")
        val mgr = manager(store, fcm, engine, scope)
        assertTrue(mgr.syncAuthenticatedDevice().isSuccess)
        assertEquals("fcm-1", store.getLastPushToken())

        var resync: Deferred<Result<DeviceConnectionState?>>? = null
        mgr.startObservingTokenChanges { resync = scope.async { mgr.syncAuthenticatedDevice() } }
        fcm.emit("fcm-2")
        runCurrent()
        assertTrue(resync!!.await().isSuccess)

        assertTrue(bodies.last().contains("\"previous_push_token\":\"fcm-1\""), bodies.last())
        assertEquals("fcm-2", store.getLastPushToken())
    }

    @Test
    fun logoutForgetsTheRegisteredPushToken() = runTest {
        val engine = MockEngine { respond(IDENTIFY_NO_LINKS, HttpStatusCode.OK, jsonHeader) }
        val store = FakeTokenStore(access = "a", app = "apk_x", lastPush = "fcm")

        manager(store, FakeFcmTokenSource("fcm"), engine).deregisterForLogout()

        assertNull(store.getLastPushToken(), "the next account must not inherit this Device")
    }

    @Test
    fun syncPropagatesNonAuthLinkErrorsWhenNotYetLinked() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/devices/link/")) {
                respond("""{"detail":"boom"}""", HttpStatusCode.InternalServerError, jsonHeader)
            } else {
                respond(IDENTIFY_NO_LINKS, HttpStatusCode.OK, jsonHeader)
            }
        }
        val result = manager(FakeTokenStore(access = "a", app = "apt_x"), FakeFcmTokenSource("fcm"), engine)
            .syncAuthenticatedDevice()

        assertTrue(result.isFailure, "a 5xx during automatic link is not swallowed")
    }

    @Test
    fun unlinkClearsLocalEnrolmentCodeOnServerSuccess() = runTest {
        val store = FakeTokenStore(access = "a", app = "apt_x")
        val engine = MockEngine {
            respond("""{"status":"ok","device_id":1,"application_id":2,"unlinked":true}""", HttpStatusCode.OK, jsonHeader)
        }
        val result = manager(store, FakeFcmTokenSource("fcm"), engine).unlinkCurrentDevice()

        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals(true, result.getOrNull())
        assertNull(store.getEnrolmentCode(), "the local enrolment code must be cleared after a server unlink")
    }

    @Test
    fun unlinkIsNoOpWithoutEnrolmentCode() = runTest {
        var hit = false
        val engine = MockEngine { hit = true; respond("", HttpStatusCode.OK) }
        val result = manager(FakeTokenStore(access = "a"), FakeFcmTokenSource("fcm"), engine).unlinkCurrentDevice()

        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrNull())
        assertFalse(hit, "nothing linked → no network call")
    }

    @Test
    fun unlinkKeepsLocalEnrolmentCodeOnServerFailure() = runTest {
        val store = FakeTokenStore(access = "a", app = "apt_x")
        val engine = MockEngine { respond("""{"detail":"boom"}""", HttpStatusCode.InternalServerError, jsonHeader) }
        val result = manager(store, FakeFcmTokenSource("fcm"), engine).unlinkCurrentDevice()

        assertTrue(result.isFailure)
        assertEquals("apt_x", store.getEnrolmentCode(), "keep the enrolment code so the user can retry")
    }

    @Test
    fun stopObservingDetachesTheCallback() = runTest {
        // The reset-on-token-change now dispatches through the manager's scope (so it
        // can take linkMutex off the FCM callback thread); drive it with the test
        // scheduler. Inject a scope on this test's dispatcher so launches are advanced
        // by runCurrent().
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val fcm = FakeFcmTokenSource("fcm")
        val engine = MockEngine { respond(IDENTIFY_OK, HttpStatusCode.OK, jsonHeader) }
        val mgr = manager(FakeTokenStore(access = "a"), fcm, engine, scope)

        var observed = 0
        mgr.startObservingTokenChanges { observed++ }
        fcm.emit("rotated")
        runCurrent()
        assertEquals(1, observed, "observer should fire on token change")

        mgr.stopObservingTokenChanges()
        assertTrue(fcm.observerDetached)
        fcm.emit("again")
        runCurrent()
        assertEquals(1, observed, "no more callbacks after stopObserving")
    }

    @Test
    fun concurrentLinksAreSingleFlight() = runTest {
        // Two concurrent link attempts with the same token pair must hit the network
        // once: the mutex serializes the check-then-link so the second sees the cache
        // populated and skips the redundant /devices/link/ call.
        val links = mutableListOf<String>()
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/devices/link/")) {
                links += request.url.encodedPath
                respond(
                    """{"status":"ok","device_id":1,"device_created":false,"link_created":true,"application_id":2}""",
                    HttpStatusCode.OK, jsonHeader,
                )
            } else {
                respond(IDENTIFY_NO_LINKS, HttpStatusCode.OK, jsonHeader)
            }
        }
        val mgr = manager(FakeTokenStore(access = "a", app = "apt_x"), FakeFcmTokenSource("fcm"), engine)

        coroutineScope {
            val a = async { mgr.linkWithStoredEnrolmentCode() }
            val b = async { mgr.linkWithStoredEnrolmentCode() }
            assertTrue(a.await().isSuccess && b.await().isSuccess)
        }

        assertEquals(1, links.size, "concurrent identical links must dedup to a single /devices/link/")
    }

    @Test
    fun deregisterForLogoutUnlinksEveryLinkedAppAndForgetsCode() = runTest {
        val unlinkedIds = mutableListOf<String>()
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/devices/identify/") ->
                    respond(IDENTIFY_OK, HttpStatusCode.OK, jsonHeader)
                request.url.encodedPath.endsWith("/devices/unlink-app/") -> {
                    unlinkedIds += String(request.body.toByteArray())
                    respond(
                        """{"status":"ok","device_id":1,"application_id":2,"unlinked":true}""",
                        HttpStatusCode.OK, jsonHeader,
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val store = FakeTokenStore(access = "a", app = "code")

        val count = manager(store, FakeFcmTokenSource("fcm-token-0123456789"), engine).deregisterForLogout()

        assertEquals(1, count)
        assertEquals(1, unlinkedIds.size)
        assertTrue(unlinkedIds.single().contains("\"application_id\":2"), unlinkedIds.single())
        assertNull(store.getEnrolmentCode(), "enrolment code must be forgotten on logout")
    }

    @Test
    fun deregisterForLogoutStillClearsLocallyWhenOffline() = runTest {
        val engine = MockEngine { throw IOException("offline") }
        val store = FakeTokenStore(access = "a", app = "code")

        val count = manager(store, FakeFcmTokenSource("fcm"), engine).deregisterForLogout()

        assertEquals(0, count)
        assertNull(store.getEnrolmentCode())
    }
}
