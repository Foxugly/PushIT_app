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
import io.ktor.utils.io.errors.IOException
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import com.foxugly.pushit_app.data.api.ApiException
import kotlin.test.assertIs

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
    fun logoutWithExpiredAccessRefreshesThenRevokesTheRotatedRefreshToken() = runTest {
        // auth/logout/ needs a valid access token: with an expired one the first call
        // gets 401, so we refresh (which rotates the refresh token) and replay with the
        // NEW refresh token — the one the server must blacklist.
        val store = FakeTokenStore(access = "expired", refresh = "ref-1")
        val logoutBodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/auth/refresh/") ->
                    respond("""{"access":"fresh","refresh":"ref-2"}""", HttpStatusCode.OK, jsonHeader)
                request.headers[HttpHeaders.Authorization] == "Bearer fresh" -> {
                    logoutBodies += request.body.toByteArray().decodeToString()
                    respond("", HttpStatusCode.NoContent)
                }
                else -> {
                    logoutBodies += request.body.toByteArray().decodeToString()
                    respond("""{"code":"token_not_valid"}""", HttpStatusCode.Unauthorized, jsonHeader)
                }
            }
        }
        val repo = AuthRepository(api(store, engine), store)

        val result = repo.logout()

        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals(listOf("""{"refresh":"ref-1"}""", """{"refresh":"ref-2"}"""), logoutBodies)
        assertTrue(store.cleared)
    }

    @Test
    fun logoutWithDeadSessionSucceedsSilently() = runTest {
        // Refresh token refused too: nothing left to revoke server-side. Logout still
        // succeeds and must not fire the "session expired" eject.
        val store = FakeTokenStore(access = "expired", refresh = "dead")
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        val pushItApi = api(store, engine)
        var authFailures = 0
        pushItApi.onAuthFailure = { authFailures++ }

        val result = AuthRepository(pushItApi, store).logout()

        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals(0, authFailures)
        assertTrue(store.cleared)
    }

    @Test
    fun loginWithUnverifiedEmailExposesTheCode() = runTest {
        val store = FakeTokenStore()
        val engine = MockEngine {
            respond(
                """{"code":"email_not_verified","detail":"Please confirm your email address before signing in."}""",
                HttpStatusCode.Forbidden, jsonHeader,
            )
        }
        val error = AuthRepository(api(store, engine), store).login("a@b.test", "pw").exceptionOrNull()

        val apiError = assertIs<ApiException>(error)
        assertEquals(403, apiError.statusCode)
        assertEquals(AuthRepository.ERROR_EMAIL_NOT_VERIFIED, apiError.errorCode)
        assertEquals(null, store.getAccessToken())
    }

    @Test
    fun resendConfirmationPostsTheEmail() = runTest {
        val store = FakeTokenStore()
        var path = ""
        var body = ""
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            body = request.body.toByteArray().decodeToString()
            respond("""{"code":"ok","detail":"sent"}""", HttpStatusCode.OK, jsonHeader)
        }

        val result = AuthRepository(api(store, engine), store).resendConfirmationEmail(" a@b.test ")

        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals("/api/v1/auth/email/resend/", path)
        assertEquals("""{"email":"a@b.test"}""", body)
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
    fun tryRefreshPersistsRotatedRefreshToken() = runTest {
        // The backend rotates + blacklists refresh tokens, so a successful startup
        // refresh returns a NEW refresh token. tryRefresh() must persist it, else the
        // next refresh presents the now-blacklisted token and the user is ejected.
        val store = FakeTokenStore(refresh = "old-ref")
        val engine = MockEngine {
            respond("""{"access":"fresh","refresh":"rotated-ref"}""", HttpStatusCode.OK, jsonHeader)
        }
        val repo = AuthRepository(api(store, engine), store)

        assertTrue(repo.tryRefresh())
        assertEquals("fresh", store.getAccessToken())
        assertEquals("rotated-ref", store.getRefreshToken())
    }

    @Test
    fun tryRefreshKeepsExistingRefreshTokenWhenServerOmitsIt() = runTest {
        // Defensive: if the server ever omits `refresh`, keep the one we have rather
        // than nulling it out.
        val store = FakeTokenStore(refresh = "old-ref")
        val engine = MockEngine { respond("""{"access":"fresh"}""", HttpStatusCode.OK, jsonHeader) }
        val repo = AuthRepository(api(store, engine), store)

        assertTrue(repo.tryRefresh())
        assertEquals("fresh", store.getAccessToken())
        assertEquals("old-ref", store.getRefreshToken())
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

    @Test
    fun tryRefreshKeepsSessionOnServerError() = runTest {
        val store = FakeTokenStore(access = "old", refresh = "ref")
        val engine = MockEngine { respond("", HttpStatusCode.ServiceUnavailable) }
        val repo = AuthRepository(api(store, engine), store)

        assertTrue(repo.tryRefresh(), "a 5xx must keep the session")
        assertFalse(store.cleared)
        assertEquals("ref", store.getRefreshToken())
    }

    @Test
    fun tryRefreshKeepsSessionWhenOffline() = runTest {
        val store = FakeTokenStore(access = "old", refresh = "ref")
        val engine = MockEngine { throw IOException("offline") }
        val repo = AuthRepository(api(store, engine), store)

        assertTrue(repo.tryRefresh(), "being offline must keep the session")
        assertFalse(store.cleared)
    }

    @Test
    fun deleteAccountSendsPasswordAndClearsLocalSession() = runTest {
        val store = FakeTokenStore(access = "acc", refresh = "ref", app = "apk_x")
        var method: HttpMethod? = null
        var path = ""
        var body = ""
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            body = request.body.toByteArray().decodeToString()
            respond("", HttpStatusCode.NoContent)
        }
        val repo = AuthRepository(api(store, engine), store)

        val result = repo.deleteAccount("s3cret")

        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals(HttpMethod.Delete, method)
        assertEquals("/api/v1/auth/me/", path)
        assertEquals("""{"current_password":"s3cret"}""", body)
        assertTrue(store.cleared)
        assertEquals(null, store.getAccessToken())
        assertEquals(null, store.getRefreshToken())
        assertEquals(null, store.getEnrolmentCode())
    }

    @Test
    fun deleteAccountWrongPasswordExposesCodeAndKeepsSession() = runTest {
        val store = FakeTokenStore(access = "acc", refresh = "ref", app = "apk_x")
        val engine = MockEngine {
            respond(
                """{"code":"current_password_invalid","detail":"Current password is incorrect."}""",
                HttpStatusCode.BadRequest, jsonHeader,
            )
        }
        val repo = AuthRepository(api(store, engine), store)

        val error = repo.deleteAccount("wrong").exceptionOrNull()

        val apiError = assertIs<ApiException>(error)
        assertEquals(400, apiError.statusCode)
        assertEquals(AuthRepository.ERROR_CURRENT_PASSWORD_INVALID, apiError.errorCode)
        assertFalse(store.cleared, "a refused deletion must not sign the user out")
        assertEquals("acc", store.getAccessToken())
        assertEquals("apk_x", store.getEnrolmentCode())
    }

    @Test
    fun deleteAccountValidationErrorKeepsSession() = runTest {
        val store = FakeTokenStore(access = "acc", refresh = "ref")
        val engine = MockEngine {
            respond(
                """{"code":"validation_error","detail":"Invalid data.","errors":{"current_password":["This field is required."]}}""",
                HttpStatusCode.BadRequest, jsonHeader,
            )
        }
        val repo = AuthRepository(api(store, engine), store)

        val apiError = assertIs<ApiException>(repo.deleteAccount("").exceptionOrNull())
        assertEquals("validation_error", apiError.errorCode)
        assertFalse(store.cleared)
    }

    @Test
    fun deleteAccountOfflineKeepsSession() = runTest {
        val store = FakeTokenStore(access = "acc", refresh = "ref")
        val engine = MockEngine { throw IOException("offline") }
        val repo = AuthRepository(api(store, engine), store)

        assertTrue(repo.deleteAccount("pw").isFailure)
        assertFalse(store.cleared)
    }
}
