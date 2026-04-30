package com.foxugly.pushit_app.platform

expect class FcmTokenProvider {
    fun getCurrentToken(): String?
    fun observeTokenChanges(onNewToken: (String) -> Unit)
}
