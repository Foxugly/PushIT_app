package com.foxugly.pushit_app.diagnostics

actual object AppLogger {
    actual fun debug(tag: String, message: String) {
        println("DEBUG/$tag: $message")
    }

    actual fun info(tag: String, message: String) {
        println("INFO/$tag: $message")
    }

    actual fun warn(tag: String, message: String, throwable: Throwable?) {
        println("WARN/$tag: $message ${throwable?.message.orEmpty()}")
    }

    actual fun error(tag: String, message: String, throwable: Throwable?) {
        println("ERROR/$tag: $message ${throwable?.message.orEmpty()}")
    }
}

