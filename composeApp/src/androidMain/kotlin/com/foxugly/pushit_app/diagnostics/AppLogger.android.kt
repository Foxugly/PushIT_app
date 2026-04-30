package com.foxugly.pushit_app.diagnostics

import android.util.Log

actual object AppLogger {
    actual fun debug(tag: String, message: String) {
        Log.d(tag, message)
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

