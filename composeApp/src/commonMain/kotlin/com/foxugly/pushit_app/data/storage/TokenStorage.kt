package com.foxugly.pushit_app.data.storage

expect class TokenStorage {
    fun getAccessToken(): String?
    fun setAccessToken(token: String?)
    fun getRefreshToken(): String?
    fun setRefreshToken(token: String?)
    // Le code d'enrolement scanne (apk_...), ou l'ancien jeton apt_ pour une
    // install qui n'a pas re-scanne. Il n'ouvre que le rattachement : la capacite
    // d'emettre vit desormais dans un jeton distinct, qui ne descend jamais ici.
    fun getEnrolmentCode(): String?
    fun setEnrolmentCode(code: String?)
    fun clearAuthTokens()
    // UI language preference (lowercase ISO code, e.g. "fr"). Local-only: the
    // mobile API has no language PATCH endpoint, so this is never sent server-side.
    fun getLanguage(): String?
    fun setLanguage(code: String?)
    // Local inbox state (read / dismissed notification ids), persisted as a JSON
    // blob. Local-only — the backend has no per-device read state (yet).
    fun getNotificationState(): String?
    fun setNotificationState(json: String?)
}
