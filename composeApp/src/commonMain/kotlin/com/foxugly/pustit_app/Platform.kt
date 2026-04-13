package com.foxugly.pustit_app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform