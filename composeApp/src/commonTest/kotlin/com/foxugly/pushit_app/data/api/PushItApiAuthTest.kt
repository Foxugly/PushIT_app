package com.foxugly.pushit_app.data.api

import com.foxugly.pushit_app.data.storage.TokenStore
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

private class FakeTokenStore(
    private var access: String? = null,
    private var refresh: String? = null,
    private var app: String? = null,
) : TokenStore {
    var cleared = false
    override fun getAccessToken() = access
    override fun setAccessToken(token: String?) { access = token }
    override fun getRefreshToken() = refresh
    override fun setRefreshToken(token: String?) { refresh = token }
    override fun getAppToken() = app
    override fun setAppToken(token: String?) { app = token }
    override fun clearAuthTokens() { access = null; refresh = null; cleared = true }
}

private val jsonHeader = headersOf(HttpHeaders.ContentType, "application/json")

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
    fun signalsAuthFailureAndClearsTokensWhenRefreshFails() = runTest {
        val store = FakeTokenStore(access = "stale", refresh = "bad")
        var authFailed = false
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        val api = PushItApi(store, baseUrl = "https://test/api/v1/", engine = engine)
        api.onAuthFailure = { authFailed = true }

        val result = api.getMe()

        assertTrue(result.isFailure)
        assertTrue(authFailed, "onAuthFailure must fire when refresh fails")
        assertTrue(store.cleared, "tokens must be cleared on refresh failure")
        api.close()
    }
}
