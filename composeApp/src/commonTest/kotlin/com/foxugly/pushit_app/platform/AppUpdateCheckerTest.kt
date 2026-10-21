package com.foxugly.pushit_app.platform

import com.foxugly.pushit_app.FakeTokenStore
import com.foxugly.pushit_app.data.api.AndroidAppVersion
import com.foxugly.pushit_app.data.api.ApiException
import com.foxugly.pushit_app.data.api.PushItApi
import com.foxugly.pushit_app.data.storage.AppUpdateStateStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.hours
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeUpdateStore(var dismissed: Long? = null) : AppUpdateStateStore {
    override fun getDismissedUpdateVersionCode() = dismissed
    override fun setDismissedUpdateVersionCode(versionCode: Long?) { dismissed = versionCode }
}

private fun version(code: Long, name: String = "1.0.$code") = AndroidAppVersion(code, name, 1234L)

class AppUpdateCheckerTest {

    private var now = 0L
    private var calls = 0

    private fun checker(
        latest: () -> Result<AndroidAppVersion>,
        store: AppUpdateStateStore = FakeUpdateStore(),
        installed: Long? = 10L,
    ) = AppUpdateChecker(
        fetchLatest = { calls++; latest() },
        store = store,
        installedVersionCode = { installed },
        nowMillis = { now },
    )

    @Test
    fun shouldOfferUpdateComparesInstalledAndDismissed() {
        assertTrue(shouldOfferUpdate(11, 10, null))
        assertFalse(shouldOfferUpdate(10, 10, null))
        assertFalse(shouldOfferUpdate(9, 10, null))
        assertFalse(shouldOfferUpdate(11, 10, 11))
        assertFalse(shouldOfferUpdate(11, 10, 12))
        assertTrue(shouldOfferUpdate(12, 10, 11))
    }

    @Test
    fun newerVersionIsOffered() = runTest {
        val c = checker({ Result.success(version(11, "1.2.0")) })
        c.checkIfDue()
        assertEquals("1.2.0", c.available?.versionName)
    }

    @Test
    fun sameOrOlderVersionIsNotOffered() = runTest {
        val c = checker({ Result.success(version(10)) })
        c.checkIfDue()
        assertNull(c.available)
    }

    @Test
    fun failureMeansNoBanner() = runTest {
        val c = checker({ Result.failure(ApiException(404, "not found", "{}")) })
        c.checkIfDue()
        assertNull(c.available)
    }

    @Test
    fun noInstalledVersionCodeSkipsTheCall() = runTest {
        val c = checker({ Result.success(version(11)) }, installed = null)
        c.checkIfDue()
        assertEquals(0, calls)
        assertNull(c.available)
    }

    @Test
    fun checksAreThrottledToOncePerSixHours() = runTest {
        val c = checker({ Result.success(version(11)) })
        c.checkIfDue()
        now += 6.hours.inWholeMilliseconds - 1
        c.checkIfDue()
        assertEquals(1, calls)
        now += 1
        c.checkIfDue()
        assertEquals(2, calls)
    }

    @Test
    fun failedCheckAlsoCountsForTheThrottle() = runTest {
        val c = checker({ Result.failure(IllegalStateException("offline")) })
        c.checkIfDue()
        now += 1.hours.inWholeMilliseconds
        c.checkIfDue()
        assertEquals(1, calls)
    }

    @Test
    fun dismissHidesUntilANewerVersionAppears() = runTest {
        val store = FakeUpdateStore()
        var published = version(11)
        val c = checker({ Result.success(published) }, store = store)
        c.checkIfDue()
        c.dismiss()
        assertNull(c.available)
        assertEquals(11L, store.dismissed)

        now += 7.hours.inWholeMilliseconds
        c.checkIfDue()
        assertNull(c.available, "same version stays dismissed")

        published = version(12)
        now += 7.hours.inWholeMilliseconds
        c.checkIfDue()
        assertEquals(12L, c.available?.versionCode)
    }

    @Test
    fun apiParsesVersionAndSurfaces404AsFailure() = runTest {
        var status = HttpStatusCode.OK
        val engine = MockEngine { request ->
            assertTrue(request.url.encodedPath.endsWith("/devices/android-app/version/"))
            if (status == HttpStatusCode.OK) {
                respond(
                    """{"version_code":42,"version_name":"1.4.2","size":123456}""",
                    status, headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond("""{"detail":"Not found."}""", status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val api = PushItApi(FakeTokenStore(access = "a"), baseUrl = "https://test/api/v1/", engine = engine)

        assertEquals(AndroidAppVersion(42, "1.4.2", 123456), api.getAndroidAppVersion().getOrThrow())
        status = HttpStatusCode.NotFound
        assertIs<ApiException>(api.getAndroidAppVersion().exceptionOrNull())
    }
}
