package com.foxugly.pushit_app.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.foxugly.pushit_app.data.api.NetworkErrorKind
import com.foxugly.pushit_app.data.api.NetworkException

/**
 * All user-facing UI strings, one immutable instance per [AppLanguage] (FR/NL/EN).
 * Mirrors the web `AppCopyService` map approach — no compose-resources/locale
 * machinery, so in-app language switching is a plain state change and behaves
 * identically on Android and iOS.
 *
 * Provided through [LocalStrings]; read in composables via `LocalStrings.current`.
 */
data class Strings(
    // Common
    val retry: String,
    val back: String,
    val email: String,
    // Login
    val password: String,
    val login: String,
    val loginFailed: String,
    val registerHint: String,
    val credit: String,
    // Notifications list
    val notificationsTitle: String,
    val settingsAction: String,
    val noNotifications: String,
    val loadNotificationsFailed: String,
    // Notification detail
    val notificationTitle: String,
    val loadNotificationFailed: String,
    val createdLabel: String,
    val sentLabel: String,
    val timestampAt: String,
    // Settings
    val settingsTitle: String,
    val account: String,
    val loadUserFailed: String,
    val userKey: String,
    val appTokenSection: String,
    val linkedPrefix: String,
    val notLinked: String,
    val rescanQr: String,
    val scanQr: String,
    val unlinkDevice: String,
    val unlinkFailed: String,
    val logout: String,
    val language: String,
    // QR scanner
    val scanAppToken: String,
    val enterManually: String,
    val enterAppToken: String,
    val tokenFieldLabel: String,
    val saveToken: String,
    val backToScanner: String,
    val invalidQrToken: String,
    val tokenMustStartWith: String,
    // App-level error fallbacks
    val startupFailed: String,
    val deviceConnectionFailed: String,
    val deviceLinkFailed: String,
    // Transport errors (NetworkException)
    val networkOffline: String,
    val networkTimeout: String,
    // Notification status badges (lowercase backend value -> label)
    val statusLabels: Map<String, String>,
) {
    /** Localized label for a backend status string, falling back to the raw value. */
    fun statusLabel(raw: String): String = statusLabels[raw.lowercase()] ?: raw
}

/**
 * Localized text for an error surfaced to the UI, or `null` when there is
 * nothing worth showing (coroutine cancellation — e.g. a screen left the
 * composition mid-request). Transport failures ([NetworkException]) become a
 * localized message; anything else keeps its own message, falling back to
 * [fallback]. Keeps the data layer language-agnostic.
 */
fun Strings.errorText(throwable: Throwable, fallback: String): String? = when {
    throwable is kotlin.coroutines.cancellation.CancellationException -> null
    throwable is NetworkException && throwable.kind == NetworkErrorKind.TIMEOUT -> networkTimeout
    throwable is NetworkException -> networkOffline
    else -> throwable.message ?: fallback
}

private val FR = Strings(
    retry = "Réessayer",
    back = "Retour",
    email = "E-mail",
    password = "Mot de passe",
    login = "Se connecter",
    loginFailed = "Échec de la connexion",
    registerHint = "Nouveau ? Créez un compte sur pushit.foxugly.com",
    credit = "par ",
    notificationsTitle = "Notifications",
    settingsAction = "Paramètres",
    noNotifications = "Aucune notification pour l'instant",
    loadNotificationsFailed = "Échec du chargement des notifications",
    notificationTitle = "Notification",
    loadNotificationFailed = "Échec du chargement de la notification",
    createdLabel = "Créée",
    sentLabel = "Envoyée",
    timestampAt = "à",
    settingsTitle = "Paramètres",
    account = "Compte",
    loadUserFailed = "Échec du chargement des infos utilisateur",
    userKey = "Clé utilisateur",
    appTokenSection = "Token applicatif",
    linkedPrefix = "Lié",
    notLinked = "Non lié à une application",
    rescanQr = "Re-scanner le QR code",
    scanQr = "Scanner le QR code",
    unlinkDevice = "Dissocier cet appareil",
    unlinkFailed = "Échec de la dissociation",
    logout = "Déconnexion",
    language = "Langue",
    scanAppToken = "Scanner le token applicatif",
    enterManually = "Saisir le token manuellement",
    enterAppToken = "Saisir le token applicatif",
    tokenFieldLabel = "Token (commence par apt_)",
    saveToken = "Enregistrer le token",
    backToScanner = "Retour au scanner",
    invalidQrToken = "QR code invalide. Le token doit commencer par « apt_ ».",
    tokenMustStartWith = "Le token doit commencer par « apt_ ».",
    startupFailed = "Échec du démarrage",
    deviceConnectionFailed = "Échec de la connexion de l'appareil",
    deviceLinkFailed = "Échec de l'association de l'appareil",
    networkOffline = "Impossible de joindre le serveur.",
    networkTimeout = "La requête a expiré.",
    statusLabels = mapOf(
        "pending" to "en attente",
        "scheduled" to "planifiée",
        "queued" to "en file",
        "processing" to "en cours",
        "sent" to "envoyée",
        "delivered" to "délivrée",
        "partial" to "partielle",
        "failed" to "échouée",
        "no_target" to "sans cible",
        "shifted" to "décalée",
        "draft" to "brouillon",
    ),
)

private val NL = Strings(
    retry = "Opnieuw proberen",
    back = "Terug",
    email = "E-mail",
    password = "Wachtwoord",
    login = "Inloggen",
    loginFailed = "Inloggen mislukt",
    registerHint = "Nieuw hier? Maak een account aan op pushit.foxugly.com",
    credit = "door ",
    notificationsTitle = "Notificaties",
    settingsAction = "Instellingen",
    noNotifications = "Nog geen notificaties",
    loadNotificationsFailed = "Laden van notificaties mislukt",
    notificationTitle = "Notificatie",
    loadNotificationFailed = "Laden van notificatie mislukt",
    createdLabel = "Aangemaakt",
    sentLabel = "Verzonden",
    timestampAt = "om",
    settingsTitle = "Instellingen",
    account = "Account",
    loadUserFailed = "Laden van gebruikersgegevens mislukt",
    userKey = "Gebruikerssleutel",
    appTokenSection = "Applicatietoken",
    linkedPrefix = "Gekoppeld",
    notLinked = "Niet aan een applicatie gekoppeld",
    rescanQr = "QR-code opnieuw scannen",
    scanQr = "QR-code scannen",
    unlinkDevice = "Dit toestel ontkoppelen",
    unlinkFailed = "Ontkoppelen mislukt",
    logout = "Uitloggen",
    language = "Taal",
    scanAppToken = "Applicatietoken scannen",
    enterManually = "Token handmatig invoeren",
    enterAppToken = "Applicatietoken invoeren",
    tokenFieldLabel = "Token (begint met apt_)",
    saveToken = "Token opslaan",
    backToScanner = "Terug naar scanner",
    invalidQrToken = "Ongeldige QR-code. Het token moet met 'apt_' beginnen.",
    tokenMustStartWith = "Het token moet met 'apt_' beginnen.",
    startupFailed = "Opstarten mislukt",
    deviceConnectionFailed = "Verbinden van toestel mislukt",
    deviceLinkFailed = "Koppelen van toestel mislukt",
    networkOffline = "Kan de server niet bereiken.",
    networkTimeout = "De aanvraag is verlopen.",
    statusLabels = mapOf(
        "pending" to "in afwachting",
        "scheduled" to "gepland",
        "queued" to "in wachtrij",
        "processing" to "bezig",
        "sent" to "verzonden",
        "delivered" to "afgeleverd",
        "partial" to "gedeeltelijk",
        "failed" to "mislukt",
        "no_target" to "zonder doel",
        "shifted" to "verschoven",
        "draft" to "concept",
    ),
)

private val EN = Strings(
    retry = "Retry",
    back = "Back",
    email = "Email",
    password = "Password",
    login = "Login",
    loginFailed = "Login failed",
    registerHint = "New here? Create an account at pushit.foxugly.com",
    credit = "by ",
    notificationsTitle = "Notifications",
    settingsAction = "Settings",
    noNotifications = "No notifications yet",
    loadNotificationsFailed = "Failed to load notifications",
    notificationTitle = "Notification",
    loadNotificationFailed = "Failed to load notification",
    createdLabel = "Created",
    sentLabel = "Sent",
    timestampAt = "at",
    settingsTitle = "Settings",
    account = "Account",
    loadUserFailed = "Failed to load user info",
    userKey = "User key",
    appTokenSection = "App token",
    linkedPrefix = "Linked",
    notLinked = "Not linked to an application",
    rescanQr = "Re-scan QR code",
    scanQr = "Scan QR code",
    unlinkDevice = "Unlink this device",
    unlinkFailed = "Unlink failed",
    logout = "Logout",
    language = "Language",
    scanAppToken = "Scan app token",
    enterManually = "Enter token manually",
    enterAppToken = "Enter app token",
    tokenFieldLabel = "Token (starts with apt_)",
    saveToken = "Save token",
    backToScanner = "Back to scanner",
    invalidQrToken = "Invalid QR code. Token must start with \"apt_\".",
    tokenMustStartWith = "Token must start with \"apt_\".",
    startupFailed = "Startup failed",
    deviceConnectionFailed = "Device connection failed",
    deviceLinkFailed = "Device link failed",
    networkOffline = "Could not reach the server.",
    networkTimeout = "The request timed out.",
    statusLabels = mapOf(
        "pending" to "pending",
        "scheduled" to "scheduled",
        "queued" to "queued",
        "processing" to "processing",
        "sent" to "sent",
        "delivered" to "delivered",
        "partial" to "partial",
        "failed" to "failed",
        "no_target" to "no target",
        "shifted" to "shifted",
        "draft" to "draft",
    ),
)

fun stringsFor(language: AppLanguage): Strings = when (language) {
    AppLanguage.FR -> FR
    AppLanguage.NL -> NL
    AppLanguage.EN -> EN
}

/** UI language strings for the current composition. Defaults to the fleet default (FR). */
val LocalStrings = staticCompositionLocalOf { stringsFor(AppLanguage.DEFAULT) }
