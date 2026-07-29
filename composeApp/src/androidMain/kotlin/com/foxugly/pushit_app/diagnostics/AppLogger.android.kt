package com.foxugly.pushit_app.diagnostics

import android.util.Log

actual object AppLogger {

    /**
     * `Log.isLoggable` plutôt que `BuildConfig.DEBUG` : ce module est partagé,
     * il n'a pas de `BuildConfig` à lui. Le seuil par défaut d'Android est INFO,
     * donc debug est muet en release et s'active à la demande avec
     * `adb shell setprop log.tag.PushIT/DeviceLink DEBUG`.
     *
     * Aucun appel ne journalise aujourd'hui la valeur d'un secret, et c'est une
     * règle à tenir : ce garde-fou limite les dégâts d'un oubli, il ne le
     * remplace pas.
     */
    actual fun debug(tag: String, message: String) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message)
        }
    }

    actual fun info(tag: String, message: String) {
        Log.i(tag, message)
    }

    actual fun warn(tag: String, message: String, throwable: Throwable?) {
        Log.w(tag, message, throwable)
    }

    actual fun error(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}

