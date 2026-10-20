package com.foxugly.pushit_app.data.api

import com.foxugly.pushit_app.FakeTokenStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import io.ktor.utils.io.errors.IOException

private val jsonHeader = headersOf(HttpHeaders.ContentType, "application/json")

private const val ME_JSON = """{"id":1,"email":"a@b.test"}"""

class PushItApiAuthTest {

    @Test
    fun replaysOriginalPostWithBodyAndFreshTokenAfter401() = runTest {
        val store = FakeTokenStore(access = "stale", refresh = "refresh-1")
        val seen = mutableListOf<Triple<String, HttpMethod, String>>()

        val engine = MockEngine { request ->
            val body = String(request.body.toByteArray())
            seen += Triple(request.url.encodedPath, request.method, body)
            when {
                request.url.encodedPath.endsWith("/auth/refresh/") ->
                    respond("""{"access":"fresh"}""", HttpStatusCode.OK, jsonHeader)
                // First identify (stale token) → 401; the replay (fresh token) → 200.
                request.headers[HttpHeaders.Authorization] == "Bearer stale" ->
                    respond("", HttpStatusCode.Unauthorized)
                else ->
                    respond(
                        """{"status":"ok","device_id":1,"device_created":false,"linked_applications":[]}""",
                        HttpStatusCode.OK, jsonHeader,
                    )
            }
        }

        val api = PushItApi(store, baseUrl = "https://test/api/v1/", engine = engine)
        val result = api.identifyDevice(
            DeviceIdentifyRequest(pushToken = "p", platform = "android", deviceName = "d"),
        )

        assertTrue(result.isSuccess, "identify should succeed after refresh: ${result.exceptionOrNull()}")
        assertEquals("fresh", store.getAccessToken())
        val identifyCalls = seen.filter { it.first.endsWith("/devices/identify/") }
        assertEquals(2, identifyCalls.size, "identify should be attempted then replayed")
        assertTrue(identifyCalls.all { it.second == HttpMethod.Post }, "replay must stay a POST, not a GET")
        assertTrue(
            identifyCalls.last().third.contains("\"push_token\":\"p\""),
            "replayed POST must keep its body: ${identifyCalls.last().third}",
        )
        api.close()
    }

    @Test
    fun persistsRotatedRefreshTokenAfterRefresh() = runTest {
        // The backend rotates + blacklists refresh tokens, so a refresh returns a NEW
        // refresh token. If we don't persist it, the next refresh fails and ejects the user.
        val store = FakeTokenStore(access = "stale", refresh = "refresh-1")
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/auth/refresh/") ->
                    respond("""{"access":"fresh","refresh":"refresh-2"}""", HttpStatusCode.OK, jsonHeader)
                request.headers[HttpHeaders.Authorization] == "Bearer stale" ->
                    respond("", HttpStatusCode.Unauthorized)
                else ->
                    respond(
                        """{"status":"ok","device_id":1,"device_created":false,"linked_applications":[]}""",
                        HttpStatusCode.OK, jsonHeader,
                    )
            }
        }
        val api = PushItApi(store, baseUrl = "https://test/api/v1/", engine = engine)

        val result = api.identifyDevice(
            DeviceIdentifyRequest(pushToken = "p", platform = "android", deviceName = "d"),
        )

        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals("fresh", store.getAccessToken())
        assertEquals("refresh-2", store.getRefreshToken(), "rotated refresh token must be persisted")
        api.close()
    }

    @Test
    fun signalsAuthFailureAndClearsTokensWhenRefreshFails() = runTest {
        val store = FakeTokenStore(access = "stale", refresh = "bad")
        var authFailed = false
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        val api = PushItApi(store, baseUrl = "https://test/api/v1/", engine = engine)
        api.onAuthFailure = { authFailed = true }

        val result = api.getMe()

        assertIs<SessionExpiredException>(result.exceptionOrNull())
        assertTrue(authFailed, "onAuthFailure must fire when refresh fails")
        assertTrue(store.cleared, "tokens must be cleared on refresh failure")
        api.close()
    }

    @Test
    fun unauthorizedThenSuccessfulRefreshRetriesExactlyOnce() = runTest {
        val store = FakeTokenStore(access = "stale", refresh = "r")
        var meCalls = 0
        var refreshCalls = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/auth/refresh/")) {
                refreshCalls++
                respond("""{"access":"fresh","refresh":"r2"}""", HttpStatusCode.OK, jsonHeader)
            } else {
                meCalls++
                if (request.headers[HttpHeaders.Authorization] == "Bearer fresh") {
                    respond(ME_JSON, HttpStatusCode.OK, jsonHeader)
                } else {
                    respond("", HttpStatusCode.Unauthorized)
                }
            }
        }
        val api = PushItApi(store, baseUrl = "https://test/api/v1/", engine = engine)

        val result = api.getMe()

        assertTrue(result.isSuccess, "getMe should succeed after refresh: ${result.exceptionOrNull()}")
        assertEquals(1, refreshCalls, "one refresh attempt")
        assertEquals(2, meCalls, "original call + exactly one replay")
        assertEquals("r2", store.getRefreshToken())
        api.close()
    }

    @Test
    fun replayStillUnauthorizedIsNotRetriedAgain() = runTest {
        val store = FakeTokenStore(access = "stale", refresh = "r")
        var meCalls = 0
        var refreshCalls = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/auth/refresh/")) {
                refreshCalls++
                respond("""{"access":"fresh"}""", HttpStatusCode.OK, jsonHeader)
            } else {
                meCalls++
                respond("", HttpStatusCode.Unauthorized)
            }
        }
        val api = PushItApi(store, baseUrl = "https://test/api/v1/", engine = engine)

        assertTrue(api.getMe().isFailure)
        assertEquals(1, refreshCalls)
        assertEquals(2, meCalls, "no refresh loop: one replay only")
        api.close()
    }

    @Test
    fun refreshEndpoint401IsNotItselfRefreshed() = runTest {
        val store = FakeTokenStore(access = "stale", refresh = "dead")
        var refreshCalls = 0
        var authFailed = false
        val engine = MockEngine {
            refreshCalls++
            respond("""{"code":"token_not_valid"}""", HttpStatusCode.Unauthorized, jsonHeader)
        }
        val api = PushItApi(store, baseUrl = "https://test/api/v1/", engine = engine)
        api.onAuthFailure = { authFailed = true }

        val result = api.refresh(RefreshRequest("dead"))

        assertEquals(401, (result.exceptionOrNull() as? ApiException)?.statusCode)
        assertEquals(1, refreshCalls, "auth/refresh/ must not trigger a second refresh")
        assertFalse(authFailed, "the caller (startup) decides, not the interceptor")
        api.close()
    }

    @Test
    fun imageGetAfterRefusedRefreshIsSessionExpired() = runTest {
        val store = FakeTokenStore(access = "stale", refresh = "dead")
        var authFailed = false
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        val api = PushItApi(store, baseUrl = "https://test/api/v1/", engine = engine)
        api.onAuthFailure = { authFailed = true }

        val result = api.getImageBytes("https://test/media/logo.png")

        assertIs<SessionExpiredException>(result.exceptionOrNull())
        assertTrue(authFailed)
        assertTrue(store.cleared)
        api.close()
    }

    @Test
    fun keepsTokensWhenRefreshHitsServerError() = runTest {
        val store = FakeTokenStore(access = "stale", refresh = "r")
        var authFailed = false
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/auth/refresh/")) respond("", HttpStatusCode.ServiceUnavailable)
            else respond("", HttpStatusCode.Unauthorized)
        }
        val api = PushItApi(store, baseUrl = "https://test/api/v1/", engine = engine)
        api.onAuthFailure = { authFailed = true }

        val result = api.getMe()

        assertTrue(result.isFailure)
        assertEquals(503, (result.exceptionOrNull() as? ApiException)?.statusCode)
        assertFalse(store.cleared, "a 5xx on refresh must not sign the user out")
        assertFalse(authFailed)
        assertEquals("r", store.getRefreshToken())
        api.close()
    }

    @Test
    fun keepsTokensWhenRefreshCannotReachServer() = runTest {
        val store = FakeTokenStore(access = "stale", refresh = "r")
        var authFailed = false
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/auth/refresh/")) throw IOException("offline")
            respond("", HttpStatusCode.Unauthorized)
        }
        val api = PushItApi(store, baseUrl = "https://test/api/v1/", engine = engine)
        api.onAuthFailure = { authFailed = true }

        val result = api.getMe()

        assertIs<NetworkException>(result.exceptionOrNull())
        assertFalse(store.cleared, "being offline must not sign the user out")
        assertFalse(authFailed)
        api.close()
    }

    @Test
    fun clearsTokensWhenRefreshIsForbidden() = runTest {
        val store = FakeTokenStore(access = "stale", refresh = "r")
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/auth/refresh/")) respond("", HttpStatusCode.Forbidden)
            else respond("", HttpStatusCode.Unauthorized)
        }
        val api = PushItApi(store, baseUrl = "https://test/api/v1/", engine = engine)

        assertTrue(api.getMe().isFailure)
        assertTrue(store.cleared)
        api.close()
    }

    @Test
    fun bearerOnlySentToApiHost() = runTest {
        val store = FakeTokenStore(access = "tok", refresh = "r")
        val authByHost = mutableMapOf<String, String?>()
        val engine = MockEngine { request ->
            authByHost[request.url.host] = request.headers[HttpHeaders.Authorization]
            respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK)
        }
        val api = PushItApi(store, baseUrl = "https://test/api/v1/", engine = engine)

        assertTrue(api.getImageBytes("https://test/media/logo.png").isSuccess)
        assertTrue(api.getImageBytes("https://elsewhere.example/logo.png").isSuccess)

        assertEquals("Bearer tok", authByHost["test"])
        assertNull(authByHost["elsewhere.example"], "token must not leak to another host")
        api.close()
    }

    @Test
    fun decodeErrorOmitsResponseBodyUnlessDebug() = runTest {
        val body = """{"unexpected":"secret-jwt-value"}"""
        val engine = MockEngine { respond(body, HttpStatusCode.OK, jsonHeader) }

        val release = PushItApi(FakeTokenStore(), baseUrl = "https://test/api/v1/", engine = engine)
        val releaseError = release.login(LoginRequest("a@b.test", "pw")).exceptionOrNull()
        assertIs<ResponseDecodingException>(releaseError)
        assertFalse(releaseError.message.orEmpty().contains("secret-jwt-value"))
        release.close()

        val debug = PushItApi(FakeTokenStore(), baseUrl = "https://test/api/v1/", enableLogging = true, engine = engine)
        val debugError = debug.login(LoginRequest("a@b.test", "pw")).exceptionOrNull()
        assertTrue(debugError?.message.orEmpty().contains("secret-jwt-value"))
        debug.close()
    }
}
