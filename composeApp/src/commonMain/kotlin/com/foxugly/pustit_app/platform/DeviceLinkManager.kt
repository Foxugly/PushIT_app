package com.foxugly.pustit_app.platform

import com.foxugly.pustit_app.data.api.DeviceLinkRequest
import com.foxugly.pustit_app.data.api.PushItApi
import com.foxugly.pustit_app.data.storage.TokenStorage
import com.foxugly.pustit_app.getPlatform

class DeviceLinkManager(
    private val api: PushItApi,
    private val tokenStorage: TokenStorage,
    private val fcmTokenProvider: FcmTokenProvider,
) {
    private var lastLinkedFcmToken: String? = null
    private var lastLinkedAppToken: String? = null

    suspend fun tryLink(): Result<Boolean> {
        val accessToken = tokenStorage.getAccessToken() ?: return Result.success(false)
        val appToken = tokenStorage.getAppToken() ?: return Result.success(false)
        val fcmToken = fcmTokenProvider.getCurrentToken() ?: return Result.success(false)

        if (fcmToken == lastLinkedFcmToken && appToken == lastLinkedAppToken) {
            return Result.success(true)
        }

        val platform = getPlatform()
        val result = api.linkDevice(
            appToken = appToken,
            request = DeviceLinkRequest(
                pushToken = fcmToken,
                platform = platform.platformType,
                deviceName = platform.deviceName,
            )
        )

        return result.map {
            lastLinkedFcmToken = fcmToken
            lastLinkedAppToken = appToken
            true
        }
    }

    fun startObservingTokenChanges(onLink: (Result<Boolean>) -> Unit) {
        fcmTokenProvider.observeTokenChanges { newToken ->
            lastLinkedFcmToken = null
        }
    }
}
