package com.foxugly.pushit_app.platform

import com.foxugly.pushit_app.data.api.DeviceIdentifyRequest
import com.foxugly.pushit_app.data.api.DeviceIdentifyResponse
import com.foxugly.pushit_app.data.api.DeviceLinkRequest
import com.foxugly.pushit_app.data.api.DeviceUnlinkRequest
import com.foxugly.pushit_app.data.api.LinkedApplication
import com.foxugly.pushit_app.data.api.PushItApi
import com.foxugly.pushit_app.data.storage.TokenStore
import com.foxugly.pushit_app.diagnostics.AppLogger
import com.foxugly.pushit_app.getPlatform

data class DeviceConnectionState(
    val deviceId: Int,
    val linkedApplications: List<LinkedApplication>,
    val linkCreated: Boolean = false,
)

class DeviceLinkManager(
    private val api: PushItApi,
    private val tokenStorage: TokenStore,
    private val fcmTokenProvider: FcmTokenSource,
) {
    private val tag = "PushIT/DeviceLink"
    private var lastLinkedFcmToken: String? = null
    private var lastLinkedAppToken: String? = null

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
        AppLogger.info(tag, "Identifying device platform=${platform.platformType} name=${platform.deviceName}")
        return api.identifyDevice(
            DeviceIdentifyRequest(
                pushToken = fcmToken,
                platform = platform.platformType,
                deviceName = platform.deviceName,
            )
        ).onSuccess {
            AppLogger.info(
                tag,
                "Device identified id=${it.deviceId} deviceCreated=${it.deviceCreated} linkedApps=${it.linkedApplications.size}",
            )
        }.onFailure {
            AppLogger.error(tag, "Device identify failed: ${it.message}", it)
        }
    }

    suspend fun syncAuthenticatedDevice(): Result<DeviceConnectionState?> {
        val identifyResult = identify()
        if (identifyResult.isFailure) {
            return identifyResult.map { null }
        }
        val identified = identifyResult.getOrNull() ?: return Result.success(null)
        val appToken = tokenStorage.getAppToken()
        if (appToken == null) {
            AppLogger.info(tag, "Device link skipped: no app token")
            return Result.success(
                DeviceConnectionState(
                    deviceId = identified.deviceId,
                    linkedApplications = identified.linkedApplications,
                )
            )
        }
        return linkWithAppToken(appToken, identified)
    }

    suspend fun linkWithStoredAppToken(): Result<DeviceConnectionState?> {
        val appToken = tokenStorage.getAppToken() ?: run {
            AppLogger.info(tag, "Device link skipped: no app token")
            return Result.success(null)
        }
        return linkWithAppToken(appToken, existingIdentify = null)
    }

    private suspend fun linkWithAppToken(
        appToken: String,
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
        if (fcmToken == lastLinkedFcmToken && appToken == lastLinkedAppToken) {
            AppLogger.info(tag, "Device link skipped: token pair already linked")
            return Result.success(
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
        val result = api.linkDevice(
            DeviceLinkRequest(
                appToken = appToken,
                pushToken = fcmToken,
                platform = platform.platformType,
                deviceName = platform.deviceName,
            )
        )

        return result.map {
            lastLinkedFcmToken = fcmToken
            lastLinkedAppToken = appToken
            AppLogger.info(tag, "Device linked id=${it.deviceId} deviceCreated=${it.deviceCreated} linkCreated=${it.linkCreated}")
            val refreshedLinks = identify().getOrNull()?.linkedApplications ?: existingIdentify?.linkedApplications.orEmpty()
            DeviceConnectionState(
                deviceId = it.deviceId,
                linkedApplications = refreshedLinks,
                linkCreated = it.linkCreated,
            )
        }.onFailure {
            AppLogger.error(tag, "Device link failed: ${it.message}", it)
        }
    }

    /**
     * Unlink this device from its linked application: tell the server to
     * deactivate the link, then forget the app token locally. Returns
     * success(false) when there was nothing linked. On a server failure the
     * local app token is kept (so the user can retry) — except when there's no
     * FCM token to identify the device server-side, in which case we can only
     * clear locally.
     */
    suspend fun unlinkCurrentDevice(): Result<Boolean> {
        val appToken = tokenStorage.getAppToken() ?: return Result.success(false)
        val fcmToken = fcmTokenProvider.getCurrentToken()
        if (fcmToken == null) {
            AppLogger.warn(tag, "Unlink: no FCM token, clearing app token locally only")
            forgetAppTokenLocally()
            return Result.success(true)
        }
        return api.unlinkDevice(DeviceUnlinkRequest(appToken = appToken, pushToken = fcmToken)).map {
            AppLogger.info(tag, "Device unlinked server-side (unlinked=${it.unlinked})")
            forgetAppTokenLocally()
            true
        }.onFailure {
            AppLogger.error(tag, "Device unlink failed: ${it.message}", it)
        }
    }

    private fun forgetAppTokenLocally() {
        tokenStorage.setAppToken(null)
        lastLinkedAppToken = null
        lastLinkedFcmToken = null
    }

    fun startObservingTokenChanges(onLink: (Result<Boolean>) -> Unit) {
        fcmTokenProvider.observeTokenChanges { newToken ->
            AppLogger.info(tag, "Observed FCM token change")
            lastLinkedFcmToken = null
            onLink(Result.success(false))
        }
    }

    fun stopObservingTokenChanges() {
        fcmTokenProvider.stopObservingTokenChanges()
    }
}
