package com.foxugly.pustit_app.platform

expect class FcmTokenProvider {
    fun getCurrentToken(): String?
    fun observeTokenChanges(onNewToken: (String) -> Unit)
}
