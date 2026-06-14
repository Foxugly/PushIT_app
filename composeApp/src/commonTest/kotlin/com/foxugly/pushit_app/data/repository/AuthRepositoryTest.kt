package com.foxugly.pushit_app.data.repository

import com.foxugly.pushit_app.FakeTokenStore
import com.foxugly.pushit_app.data.api.PushItApi
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
import kotlin.test.assertTrue

private val jsonHeader = headersOf(HttpHeaders.ContentType, "application/json")

private fun api(store: FakeTokenStore, handler: MockEngine) =
    PushItApi(store, baseUrl = "https://test/api/v1/", engine = handler)

private fun repo(store: FakeTokenStore) =
    AuthRepository(api(store, MockEngine { respond("", HttpStatusCode.OK) }), store)

/** Builds a minimal unsigned JWT carrying (or omitting) an `exp` claim. */
@OptIn(ExperimentalEncodingApi::class)
private fun jwt(exp: Long?): String {
    val payload = if (exp == null) """{"sub":"x"}""" else """{"exp":$exp}"""
    val seg = Base64.UrlSafe.encode(payload.encodeToByteArray()).trimEnd('=')
    return "header.$seg.signature"
}

class AuthRepositoryTest {

    @Test
    fun loginStoresTokenPairAndReturnsUser() = runTest {
        val store = FakeTokenStore()
        val engine = MockEngine {
            respond(
                """{"access":"acc","refresh":"ref","user":{"id":5,"email":"a@b.test","userkey":"k"}}""",
                HttpStatusCode.OK, jsonHeader,
            )
        }
        val repo = AuthRepository(api(store, engine), store)

        val result = repo.login("a@b.test", "pw")

        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals(5, result.getOrNull()?.id)
        assertEquals("acc", store.getAccessToken())
        assertEquals("ref", store.getRefreshToken())
    }

    @Test
    fun loginFailureLeavesTokensUntouched() = runTest {
        val store = FakeTokenStore()
        val engine = MockEngine { respond("""{"detail":"bad creds"}""", HttpStatusCode.Unauthorized, jsonHeader) }
        val repo = AuthRepository(api(store, engine), store)

        // 401 on /auth/login is NOT retried (login is in the no-auth paths); it fails.
        val result = repo.login("a@b.test", "wrong")

        assertTrue(result.isFailure)
        assertEquals(null, store.getAccessToken())
    }

    @Test
    fun logoutClearsTokensEvenWithoutRefreshToken() = runTest {
        val store = FakeTokenStore(access = "acc") // no refresh token
        var hit = false
        val engine = MockEngine { hit = true; respond("", HttpStatusCode.OK) }
        val repo = AuthRepository(api(store, engine), store)

        val result = repo.logout()

        assertTrue(result.isSuccess)
        assertTrue(store.cleared, "tokens must be cleared on logout")
        assertFalse(hit, "no refresh token → no network logout call")
    }

    @Test
    fun isAuthenticatedReflectsTokenPresence() {
        assertTrue(AuthRepository(api(FakeTokenStore(access = "x"), MockEngine { respond("", HttpStatusCode.OK) }), FakeTokenStore(access = "x")).isAuthenticated())
        assertFalse(AuthRepository(api(FakeTokenStore(), MockEngine { respond("", HttpStatusCode.OK) }), FakeTokenStore()).isAuthenticated())
    }

    @Test
    fun accessTokenExpiredTrueForPastExp() {
        // exp = 0 → 1970, comfortably in the past.
        assertTrue(repo(FakeTokenStore(access = jwt(0))).accessTokenExpired())
    }

    @Test
    fun accessTokenExpiredFalseForFutureExp() {
        // exp far in the future (year 2286) → not expired.
        assertFalse(repo(FakeTokenStore(access = jwt(9_999_999_999))).accessTokenExpired())
    }

    @Test
    fun accessTokenExpiredFalseWhenNoToken() {
        assertFalse(repo(FakeTokenStore()).accessTokenExpired())
    }

    @Test
    fun accessTokenExpiredFalseForMalformedToken() {
        // Garbage that isn't a JWT must be treated as not-expired (let the request
        // path's 401→refresh handle it), never crash.
        assertFalse(repo(FakeTokenStore(access = "not-a-jwt")).accessTokenExpired())
    }

    @Test
    fun accessTokenExpiredFalseWhenExpClaimMissing() {
        assertFalse(repo(FakeTokenStore(access = jwt(null))).accessTokenExpired())
    }

    @Test
    fun tryRefreshStoresNewAccessTokenOnSuccess() = runTest {
        val store = FakeTokenStore(refresh = "ref")
        val engine = MockEngine { respond("""{"access":"fresh"}""", HttpStatusCode.OK, jsonHeader) }
        val repo = AuthRepository(api(store, engine), store)

        assertTrue(repo.tryRefresh())
        assertEquals("fresh", store.getAccessToken())
    }

    @Test
    fun tryRefreshClearsTokensAndReturnsFalseOnFailure() = runTest {
        val store = FakeTokenStore(refresh = "bad")
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        val repo = AuthRepository(api(store, engine), store)

        assertFalse(repo.tryRefresh())
        assertTrue(store.cleared)
    }

    @Test
    fun tryRefreshShortCircuitsWithoutRefreshToken() = runTest {
        val store = FakeTokenStore()
        var hit = false
        val engine = MockEngine { hit = true; respond("", HttpStatusCode.OK) }
        val repo = AuthRepository(api(store, engine), store)

        assertFalse(repo.tryRefresh())
        assertFalse(hit, "no refresh token → no network call")
    }
}
