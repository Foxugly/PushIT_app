package com.foxugly.pushit_app.navigation

sealed class Screen {
    data object Login : Screen()
    data object QrScanner : Screen()
    data object NotificationList : Screen()
    data class AppFolder(val applicationId: Int?, val applicationName: String) : Screen()
    data class NotificationDetail(val notificationId: Int) : Screen()
    data object Settings : Screen()
}
