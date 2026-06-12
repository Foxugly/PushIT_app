package com.foxugly.pushit_app.navigation

sealed class Screen {
    data object Login : Screen()
    data object QrScanner : Screen()
    data object NotificationList : Screen()
    data class NotificationDetail(val notificationId: Int) : Screen()
    data object Settings : Screen()
}
