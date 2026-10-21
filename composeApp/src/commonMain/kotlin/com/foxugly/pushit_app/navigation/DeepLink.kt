package com.foxugly.pushit_app.navigation

private val NOTIFICATION_ID = Regex("""[1-9][0-9]{0,8}""")

/**
 * The notification id carried by a tapped push (`notification_id` extra), or null
 * when it is missing or not a plain positive id. MainActivity is exported (it is
 * the launcher), so the extra is untrusted input: only digits are accepted, and
 * the server returns the detail only to the owner of the app or to a user with a
 * delivery on one of their own devices (404 otherwise).
 */
fun parseDeepLinkNotificationId(raw: String?): Int? =
    raw?.trim()?.takeIf { NOTIFICATION_ID.matches(it) }?.toInt()
