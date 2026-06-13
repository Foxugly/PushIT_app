package com.foxugly.pushit_app.ui.i18n

/**
 * The languages the app UI is translated into (fleet parity: FR/NL/EN).
 *
 * [code] is the lowercase ISO code persisted locally and used to map to/from the
 * backend `UserProfile.language` (which is upper-case, e.g. "FR"). The display
 * language is a LOCAL preference (chosen in Settings, stored in TokenStorage):
 * the mobile API has no language PATCH endpoint, so we don't write it back.
 */
enum class AppLanguage(val code: String, val label: String) {
    FR("fr", "Français"),
    NL("nl", "Nederlands"),
    EN("en", "English");

    companion object {
        val DEFAULT = FR

        /** Resolve a stored/locale code ("fr", "FR", "fr-BE", null) to a language. */
        fun fromCode(raw: String?): AppLanguage {
            val head = raw?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_')
            return entries.firstOrNull { it.code == head } ?: DEFAULT
        }
    }
}
