package com.foxugly.pushit_app.platform

expect class FcmTokenProvider {
    fun getCurrentToken(): String?
    fun observeTokenChanges(onNewToken: (String) -> Unit)

    /** Detach the observer registered by [observeTokenChanges]. Must be called
     * when the observing scope is disposed, else the singleton retains the
     * callback (and its captured scope) for the whole process lifetime. */
    fun stopObservingTokenChanges()
}
