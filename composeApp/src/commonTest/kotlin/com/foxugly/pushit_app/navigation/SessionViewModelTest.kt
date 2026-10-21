package com.foxugly.pushit_app.navigation

import com.foxugly.pushit_app.FakeTokenStore
import com.foxugly.pushit_app.data.api.PushItApi
import com.foxugly.pushit_app.data.repository.AuthRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.ktor.utils.io.errors.IOException

private val jsonHeader = headersOf(HttpHeaders.ContentType, "application/json")

private fun okEngine() = MockEngine { respond("", HttpStatusCode.OK) }

private fun vm(store: FakeTokenStore = FakeTokenStore(), engine: MockEngine = okEngine()) =
    SessionViewModel(AuthRepository(PushItApi(store, baseUrl = "https://test/api/v1/", engine = engine), store))

/** Minimal unsigned JWT carrying an `exp` claim (seconds since epoch). */
@OptIn(ExperimentalEncodingApi::class)
private fun jwt(exp: Long): String {
    val seg = Base64.UrlSafe.encode("""{"exp":$exp}""".encodeToByteArray()).trimEnd('=')
    return "h.$seg.s"
}

class SessionViewModelTest {

    // --- navigation stack ---

    @Test
    fun navigateToPushesAndNavigateBackPops() {
        val s = vm()
        assertNull(s.currentScreen)

        s.navigateTo(Screen.NotificationList)
        assertEquals(Screen.NotificationList, s.currentScreen)

        s.navigateTo(Screen.Settings)
        assertEquals(Screen.Settings, s.currentScreen)

        s.navigateBack()
        assertEquals(Screen.NotificationList, s.currentScreen, "back returns to the pushed screen")
    }

    @Test
    fun navigateBackOnEmptyStackFallsBackToLogin() {
        val s = vm()
        s.navigateTo(Screen.NotificationList) // nothing pushed (was null) → empty stack
        s.navigateBack()
        assertEquals(Screen.Login, s.currentScreen)
    }

    @Test
    fun resetToClearsHistoryAndError() {
        val s = vm()
        s.navigateTo(Screen.NotificationList)
        s.navigateTo(Screen.Settings)
        s.runtimeError = "boom"

        s.resetTo(Screen.Login)

        assertEquals(Screen.Login, s.currentScreen)
        assertNull(s.runtimeError, "resetTo clears the error banner")
        // History cleared: a back press now falls through to Login, not Settings.
        s.navigateBack()
        assertEquals(Screen.Login, s.currentScreen)
    }

    // --- startup routing decision ---

    @Test
    fun startupRouteIsInboxForValidAccessToken() = runTest {
        val s = vm(FakeTokenStore(access = jwt(9_999_999_999))) // far-future exp
        assertEquals(Screen.NotificationList, s.resolveStartupRoute())
    }

    @Test
    fun startupRouteRefreshesWhenAccessExpiredAndSucceeds() = runTest {
        val engine = MockEngine { respond("""{"access":"fresh"}""", HttpStatusCode.OK, jsonHeader) }
        val s = vm(FakeTokenStore(access = jwt(0), refresh = "r"), engine) // exp in 1970
        assertEquals(Screen.NotificationList, s.resolveStartupRoute())
    }

    @Test
    fun startupRouteIsLoginWhenRefreshFails() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        val s = vm(FakeTokenStore(access = jwt(0), refresh = "r"), engine)
        assertEquals(Screen.Login, s.resolveStartupRoute())
    }

    @Test
    fun startupRouteIsLoginWithoutAnyCredentials() = runTest {
        assertEquals(Screen.Login, vm().resolveStartupRoute())
    }

    @Test
    fun startAppliesTheResolvedRoute() = runTest {
        val s = vm(FakeTokenStore(access = jwt(9_999_999_999)))
        s.start { "ignored" }
        assertEquals(Screen.NotificationList, s.currentScreen)
        assertNull(s.runtimeError)
    }

    // --- stale session at startup (OPERATIONS.md §3.25) ---

    @Test
    fun startupWithExpiredAccessAndRefusedRefreshLogsOutWithoutError() = runTest {
        var refreshCalls = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/auth/refresh/")) refreshCalls++
            respond(
                """{"detail":"Token is invalid or expired","code":"token_not_valid"}""",
                HttpStatusCode.Unauthorized, jsonHeader,
            )
        }
        val store = FakeTokenStore(access = jwt(0), refresh = "dead")
        val s = vm(store, engine)

        s.start { it.message } // would surface the raw 401 body if called

        assertEquals(Screen.Login, s.currentScreen)
        assertNull(s.runtimeError, "no error dialog for a stale session")
        assertTrue(s.sessionExpired, "discreet session-expired note on login")
        assertTrue(store.cleared, "dead session is cleared")
        assertEquals(1, refreshCalls, "the refresh token is tried once, not twice")
    }

    @Test
    fun startupKeepsSessionWhenRefreshCannotReachServer() = runTest {
        val engine = MockEngine { throw IOException("offline") }
        val store = FakeTokenStore(access = jwt(0), refresh = "r")
        val s = vm(store, engine)

        s.start { it.message }

        assertEquals(Screen.NotificationList, s.currentScreen)
        assertFalse(store.cleared, "offline must not sign the user out")
        assertEquals("r", store.getRefreshToken())
        assertFalse(s.sessionExpired)
    }

    @Test
    fun startupKeepsSessionWhenRefreshHitsServerError() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.BadGateway) }
        val store = FakeTokenStore(access = jwt(0), refresh = "r")
        val s = vm(store, engine)

        s.start { it.message }

        assertEquals(Screen.NotificationList, s.currentScreen)
        assertFalse(store.cleared, "a 5xx on refresh must not sign the user out")
        assertEquals("r", store.getRefreshToken())
        assertFalse(s.sessionExpired)
    }

    @Test
    fun startupWithoutStoredSessionShowsNoExpiryNote() = runTest {
        val s = vm()
        s.start { it.message }
        assertEquals(Screen.Login, s.currentScreen)
        assertFalse(s.sessionExpired)
    }

    @Test
    fun expireSessionGoesToLoginAndResetClearsTheNote() {
        val s = vm()
        s.navigateTo(Screen.NotificationList)
        s.runtimeError = "boom"

        s.expireSession()

        assertEquals(Screen.Login, s.currentScreen)
        assertNull(s.runtimeError)
        assertTrue(s.sessionExpired)
        s.resetTo(Screen.NotificationList) // e.g. after signing in again
        assertFalse(s.sessionExpired)
    }

    // --- post-login routing decision ---

    @Test
    fun routeAfterLoginAlwaysGoesToTheInboxNeverTheScanner() {
        assertEquals(Screen.NotificationList, vm().routeAfterLogin())
    }

    // --- post-QR-link routing decision ---

    @Test
    fun routeAfterQrLinkIsInboxWhenAuthenticatedElseLogin() {
        assertEquals(Screen.NotificationList, vm(FakeTokenStore(access = "x")).routeAfterQrLink())
        assertEquals(Screen.Login, vm(FakeTokenStore()).routeAfterQrLink())
    }
}
