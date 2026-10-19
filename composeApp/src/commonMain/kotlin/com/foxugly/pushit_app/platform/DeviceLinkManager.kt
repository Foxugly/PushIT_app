package com.foxugly.pushit_app.platform

import com.foxugly.pushit_app.data.api.DeviceIdentifyRequest
import com.foxugly.pushit_app.data.api.DeviceIdentifyResponse
import com.foxugly.pushit_app.data.api.DeviceLinkRequest
import com.foxugly.pushit_app.data.api.DeviceUnlinkByApplicationRequest
import com.foxugly.pushit_app.data.api.DeviceUnlinkRequest
import com.foxugly.pushit_app.data.api.isEnrolmentCodeRejection
import com.foxugly.pushit_app.data.api.LinkedApplication
import com.foxugly.pushit_app.data.api.PushItApi
import com.foxugly.pushit_app.data.storage.TokenStore
import com.foxugly.pushit_app.diagnostics.AppLogger
import com.foxugly.pushit_app.getPlatform
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DeviceConnectionState(
    val deviceId: Int,
    val linkedApplications: List<LinkedApplication>,
    val linkCreated: Boolean = false,
)

class DeviceLinkManager(
    private val api: PushItApi,
    private val tokenStorage: TokenStore,
    private val fcmTokenProvider: FcmTokenSource,
    // Scope used to route the FCM-callback-thread cache reset through [linkMutex]
    // instead of writing the cache directly off-thread. Overridable in tests.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val tag = "PushIT/DeviceLink"

    // The link "already-linked" cache. Read from coroutines (linkWithEnrolmentCode) and
    // reset from the FCM callback thread (startObservingTokenChanges / forgetEnrolmentCode),
    // so the fields are @Volatile for safe cross-thread visibility and every mutating
    // path runs single-flight under [linkMutex].
    @Volatile
    private var lastLinkedFcmToken: String? = null
    @Volatile
    private var lastLinkedEnrolmentCode: String? = null

    // Single-flight guard around the link critical section: serializes the
    // check-then-link-then-update-cache sequence so two concurrent coroutines can't
    // both decide to link and clobber the cache.
    private val linkMutex = Mutex()

    // Serializes identify so two concurrent calls can't both hand the device over
    // from the same `previous_push_token` (read-send-store of the last push token).
    private val identifyMutex = Mutex()

    /**
     * The FCM token to send as `previous_push_token`: the one last registered
     * with the server, when FCM has since rotated it. The server then moves the
     * existing Device (app links + inbox history) to [currentToken] instead of
     * creating a fresh, unlinked one. Null when nothing rotated.
     */
    private fun rotatedFrom(currentToken: String): String? =
        tokenStorage.getLastPushToken()?.takeIf { it != currentToken }

    suspend fun identify(): Result<DeviceIdentifyResponse?> {
        tokenStorage.getAccessToken() ?: run {
            AppLogger.info(tag, "Device identify skipped: no access token")
            return Result.success(null)
        }
        val fcmToken = fcmTokenProvider.getCurrentToken() ?: run {
            AppLogger.warn(tag, "Device identify skipped: no FCM token yet")
            return Result.success(null)
        }

        val platform = getPlatform()
        return identifyMutex.withLock {
            val previous = rotatedFrom(fcmToken)
            AppLogger.info(
                tag,
                "Identifying device platform=${platform.platformType} name=${platform.deviceName} rotated=${previous != null}",
            )
            api.identifyDevice(
                DeviceIdentifyRequest(
                    pushToken = fcmToken,
                    platform = platform.platformType,
                    deviceName = platform.deviceName,
                    previousPushToken = previous,
                )
            ).onSuccess {
                // Only once the server knows the new token: a failed hand-over is
                // retried with the same previous token on the next identify.
                tokenStorage.setLastPushToken(fcmToken)
                AppLogger.info(
                    tag,
                    "Device identified id=${it.deviceId} deviceCreated=${it.deviceCreated} linkedApps=${it.linkedApplications.size}",
                )
            }.onFailure {
                AppLogger.error(tag, "Device identify failed: ${it.message}", it)
            }
        }
    }

    suspend fun syncAuthenticatedDevice(): Result<DeviceConnectionState?> {
        val identifyResult = identify()
        if (identifyResult.isFailure) {
            return identifyResult.map { null }
        }
        val identified = identifyResult.getOrNull() ?: return Result.success(null)
        val enrolmentCode = tokenStorage.getEnrolmentCode()
        if (enrolmentCode == null) {
            AppLogger.info(tag, "Device link skipped: no enrolment code")
            return Result.success(
                DeviceConnectionState(
                    deviceId = identified.deviceId,
                    linkedApplications = identified.linkedApplications,
                )
            )
        }
        // Already linked: identify() above already refreshed the FCM push token, and
        // the server keeps existing app links even after an app-token rotation. Re-sending
        // the stored (possibly stale) enrolment code on every launch would only risk a spurious
        // `app_token_invalid`, so skip the redundant automatic re-link. Linking an extra
        // application is done explicitly via QR scan (linkWithStoredEnrolmentCode).
        if (identified.linkedApplications.isNotEmpty()) {
            AppLogger.info(tag, "Device link skipped: already linked to ${identified.linkedApplications.size} app(s)")
            return Result.success(
                DeviceConnectionState(
                    deviceId = identified.deviceId,
                    linkedApplications = identified.linkedApplications,
                )
            )
        }
        // Not yet linked: best-effort automatic link. A refused enrolment code (403
        // `app_token_*`: revoked, regenerated or app disabled) is non-fatal for an automatic
        // sync — linkWithEnrolmentCode already forgot the dead code so it is not retried on
        // every launch; fall back to the identify state instead of surfacing a scary error.
        // Explicit QR-scan linking (linkWithStoredEnrolmentCode) still surfaces failures.
        return linkWithEnrolmentCode(enrolmentCode, identified).recoverCatching { error ->
            if (error.isEnrolmentCodeRejection()) {
                AppLogger.warn(tag, "Automatic device link: stored enrolment code refused, ignoring: ${error.message}")
                DeviceConnectionState(
                    deviceId = identified.deviceId,
                    linkedApplications = identified.linkedApplications,
                )
            } else {
                throw error
            }
        }
    }

    suspend fun linkWithStoredEnrolmentCode(): Result<DeviceConnectionState?> {
        val enrolmentCode = tokenStorage.getEnrolmentCode() ?: run {
            AppLogger.info(tag, "Device link skipped: no enrolment code")
            return Result.success(null)
        }
        return linkWithEnrolmentCode(enrolmentCode, existingIdentify = null)
    }

    private suspend fun linkWithEnrolmentCode(
        enrolmentCode: String,
        existingIdentify: DeviceIdentifyResponse?,
    ): Result<DeviceConnectionState?> {
        tokenStorage.getAccessToken() ?: run {
            AppLogger.info(tag, "Device link skipped: no access token")
            return Result.success(null)
        }
        val fcmToken = fcmTokenProvider.getCurrentToken() ?: run {
            AppLogger.warn(tag, "Device link skipped: no FCM token yet")
            return Result.success(null)
        }
        // Single-flight: serialize the check-then-link-then-cache-update so two
        // concurrent callers can't both pass the dedup check and double-link.
        return linkMutex.withLock {
            if (fcmToken == lastLinkedFcmToken && enrolmentCode == lastLinkedEnrolmentCode) {
                AppLogger.info(tag, "Device link skipped: token pair already linked")
                return@withLock Result.success(
                    existingIdentify?.let {
                        DeviceConnectionState(
                            deviceId = it.deviceId,
                            linkedApplications = it.linkedApplications,
                        )
                    }
                )
            }

            val platform = getPlatform()
            AppLogger.info(tag, "Linking device platform=${platform.platformType} name=${platform.deviceName}")
            val result = identifyMutex.withLock {
                api.linkDevice(
                    DeviceLinkRequest(
                        appToken = enrolmentCode,
                        pushToken = fcmToken,
                        platform = platform.platformType,
                        deviceName = platform.deviceName,
                        previousPushToken = rotatedFrom(fcmToken),
                    )
                ).onSuccess { tokenStorage.setLastPushToken(fcmToken) }
            }

            result.map {
                lastLinkedFcmToken = fcmToken
                lastLinkedEnrolmentCode = enrolmentCode
                AppLogger.info(tag, "Device linked id=${it.deviceId} deviceCreated=${it.deviceCreated} linkCreated=${it.linkCreated}")
                val refreshedLinks = identify().getOrNull()?.linkedApplications ?: existingIdentify?.linkedApplications.orEmpty()
                DeviceConnectionState(
                    deviceId = it.deviceId,
                    linkedApplications = refreshedLinks,
                    linkCreated = it.linkCreated,
                )
            }.onFailure {
                AppLogger.error(tag, "Device link failed: ${it.message}", it)
                // The server refused the enrolment code itself (403 app_token_*): it
                // will never work again, so forget it rather than re-sending it on
                // every launch. Only if it is still the stored one (a new scan wins).
                if (it.isEnrolmentCodeRejection() && tokenStorage.getEnrolmentCode() == enrolmentCode) {
                    AppLogger.warn(tag, "Enrolment code refused by the server; forgetting it")
                    tokenStorage.setEnrolmentCode(null)
                    lastLinkedEnrolmentCode = null
                }
            }
        }
    }

    /**
     * Unlink this device from its linked application: tell the server to
     * deactivate the link, then forget the enrolment code locally. Returns
     * success(false) when there was nothing linked. On a server failure the
     * local enrolment code is kept (so the user can retry) — except when there's no
     * FCM token to identify the device server-side, in which case we can only
     * clear locally.
     */
    suspend fun unlinkCurrentDevice(): Result<Boolean> {
        val enrolmentCode = tokenStorage.getEnrolmentCode() ?: return Result.success(false)
        val fcmToken = fcmTokenProvider.getCurrentToken()
        if (fcmToken == null) {
            AppLogger.warn(tag, "Unlink: no FCM token, clearing the enrolment code locally only")
            forgetEnrolmentCodeLocally()
            return Result.success(true)
        }
        return api.unlinkDevice(DeviceUnlinkRequest(appToken = enrolmentCode, pushToken = fcmToken)).map {
            AppLogger.info(tag, "Device unlinked server-side (unlinked=${it.unlinked})")
            forgetEnrolmentCodeLocally()
            true
        }.onFailure {
            AppLogger.error(tag, "Device unlink failed: ${it.message}", it)
        }
    }

    /** The applications this device is currently linked to (for the Settings list). */
    suspend fun listLinkedApplications(): Result<List<LinkedApplication>> =
        identify().map { it?.linkedApplications.orEmpty() }

    /**
     * Unlink this device from one application by id (recipient inbox per-app
     * unlink). no enrolment code needed — the server resolves the device by its push
     * token. Returns success(false) when there's no FCM token or nothing linked.
     */
    suspend fun unlinkApplication(applicationId: Int): Result<Boolean> {
        val fcmToken = fcmTokenProvider.getCurrentToken() ?: return Result.success(false)
        return api.unlinkDeviceApp(
            DeviceUnlinkByApplicationRequest(pushToken = fcmToken, applicationId = applicationId)
        ).map {
            AppLogger.info(tag, "App $applicationId unlinked (unlinked=${it.unlinked})")
            it.unlinked
        }.onFailure {
            AppLogger.error(tag, "Unlink app $applicationId failed: ${it.message}", it)
        }
    }

    /**
     * Logout step, run while the access token is still valid: unlink this device
     * from every application it is linked to, so pushes stop reaching a signed-out
     * (possibly shared or resold) device, then forget the enrolment code locally.
     * Best effort: offline or on a server error the local state is cleared anyway
     * and the logout goes on. Returns how many apps were unlinked server-side.
     */
    suspend fun deregisterForLogout(): Int {
        var unlinked = 0
        try {
            val fcmToken = fcmTokenProvider.getCurrentToken()
            if (fcmToken == null || tokenStorage.getAccessToken() == null) {
                AppLogger.info(tag, "Logout deregistration skipped: no FCM or access token")
            } else {
                val linked = identify().getOrNull()?.linkedApplications.orEmpty()
                for (app in linked) {
                    api.unlinkDeviceApp(
                        DeviceUnlinkByApplicationRequest(pushToken = fcmToken, applicationId = app.id)
                    ).onSuccess { if (it.unlinked) unlinked++ }
                }
                AppLogger.info(tag, "Logout deregistration: $unlinked/${linked.size} app(s) unlinked")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(tag, "Logout deregistration failed; clearing locally", e)
        }
        forgetEnrolmentCodeLocally()
        // Signed out: the next account on this phone must not inherit this Device
        // through a `previous_push_token` hand-over.
        tokenStorage.setLastPushToken(null)
        return unlinked
    }

    /**
     * Forget the enrolment code and the in-memory link cache without calling the
     * server — after account deletion, when the server already dropped the devices.
     */
    suspend fun forgetLocalLinkState() {
        forgetEnrolmentCodeLocally()
        tokenStorage.setLastPushToken(null)
    }

    private suspend fun forgetEnrolmentCodeLocally() = linkMutex.withLock {
        tokenStorage.setEnrolmentCode(null)
        lastLinkedEnrolmentCode = null
        lastLinkedFcmToken = null
    }

    /**
     * React to an FCM token rotation. [onLink] is expected to run
     * [syncAuthenticatedDevice], whose identify sends the new token together with
     * the last registered one (`previous_push_token`): the server moves the
     * existing Device to the new token, keeping ALL its app links and inbox
     * history — not just the app of the stored enrolment code.
     */
    fun startObservingTokenChanges(onLink: (Result<Boolean>) -> Unit) {
        fcmTokenProvider.observeTokenChanges { newToken ->
            AppLogger.info(tag, "Observed FCM token change")
            // Route the cache reset through linkMutex (off the FCM callback thread)
            // so it can't race a concurrent link.
            scope.launch {
                linkMutex.withLock { lastLinkedFcmToken = null }
                onLink(Result.success(false))
            }
        }
    }

    fun stopObservingTokenChanges() {
        fcmTokenProvider.stopObservingTokenChanges()
    }
}
