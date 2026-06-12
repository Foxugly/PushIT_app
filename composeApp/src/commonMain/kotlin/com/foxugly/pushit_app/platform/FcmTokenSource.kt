package com.foxugly.pushit_app.platform

/**
 * FCM token seam used by [DeviceLinkManager]. [FcmTokenProvider] is an
 * `expect class` and so can't be faked in commonTest; depending on this
 * interface — with [FcmTokenProviderSource] adapting the real provider — lets
 * tests inject a fake without touching the expect/actual declarations (same
 * pattern as TokenStore).
 */
interface FcmTokenSource {
    fun getCurrentToken(): String?
    fun observeTokenChanges(onNewToken: (String) -> Unit)
    fun stopObservingTokenChanges()
}

/** Adapts the platform [FcmTokenProvider] to [FcmTokenSource] (pure commonMain). */
class FcmTokenProviderSource(private val provider: FcmTokenProvider) : FcmTokenSource {
    override fun getCurrentToken(): String? = provider.getCurrentToken()
    override fun observeTokenChanges(onNewToken: (String) -> Unit) = provider.observeTokenChanges(onNewToken)
    override fun stopObservingTokenChanges() = provider.stopObservingTokenChanges()
}
