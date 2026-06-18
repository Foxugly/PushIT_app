package com.foxugly.pushit_app.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.foxugly.pushit_app.data.api.NetworkErrorKind
import com.foxugly.pushit_app.data.api.NetworkException
import com.foxugly.pushit_app.platform.formatLocalShort
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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
    val ok: String,
    val errorTitle: String,
    val sessionExpired: String,
    val email: String,
    // Login
    val password: String,
    val showPassword: String,
    val hidePassword: String,
    val login: String,
    val loginFailed: String,
    val invalidCredentials: String,
    val serverError: String,
    val registerHint: String,
    val backendProd: String,
    val backendLocal: String,
    val credit: String,
    // Notifications list
    val notificationsTitle: String,
    val settingsAction: String,
    val noNotifications: String,
    val loadNotificationsFailed: String,
    // Inbox (unread section + per-app folders)
    val inboxUnread: String,
    val inboxFolders: String,
    val noUnread: String,
    val markAllRead: String,
    val loadOlder: String,
    val markUnread: String,
    val deleteAction: String,
    // Notification detail
    val notificationTitle: String,
    val loadNotificationFailed: String,
    val createdLabel: String,
    val sentLabel: String,
    val timestampAt: String,
    // Relative timestamps (notification list) — "{n}" is replaced with the count
    val justNow: String,
    val minutesAgo: String,
    val hoursAgo: String,
    val yesterday: String,
    val daysAgo: String,
    // Settings
    val settingsTitle: String,
    val account: String,
    val loadUserFailed: String,
    val userKey: String,
    val appTokenSection: String,
    val linkedAppsSection: String,
    val linkedPrefix: String,
    val notLinked: String,
    val rescanQr: String,
    val scanQr: String,
    val unlinkDevice: String,
    val unlinkApp: String,
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
    val cameraPermissionRationale: String,
    val grantPermission: String,
    val cameraSetupFailed: String,
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

/**
 * Human, device-local relative time for a list row: "just now", "5 min ago",
 * "3 h ago", "yesterday", "4 d ago", and the absolute short date beyond a week.
 * Minutes/hours use elapsed duration; "yesterday"/days use calendar days in the
 * device's time zone. Unparseable input or a future timestamp (clock skew)
 * degrade gracefully (absolute date / "just now"), never throw.
 */
@OptIn(ExperimentalTime::class)
fun Strings.relativeTime(isoTimestamp: String): String {
    val instant = runCatching { Instant.parse(isoTimestamp) }.getOrNull()
        ?: return formatLocalShort(isoTimestamp)
    val now = Clock.System.now()
    val diff = now - instant
    val minutes = diff.inWholeMinutes
    return when {
        minutes < 1L -> justNow
        minutes < 60L -> minutesAgo.replace("{n}", minutes.toString())
        diff.inWholeHours < 24L -> hoursAgo.replace("{n}", diff.inWholeHours.toString())
        else -> {
            val tz = TimeZone.currentSystemDefault()
            val dayDiff = instant.toLocalDateTime(tz).date.daysUntil(now.toLocalDateTime(tz).date)
            when {
                dayDiff <= 1 -> yesterday
                dayDiff < 7 -> daysAgo.replace("{n}", dayDiff.toString())
                else -> formatLocalShort(isoTimestamp)
            }
        }
    }
}

private val FR = Strings(
    retry = "Réessayer",
    back = "Retour",
    ok = "OK",
    errorTitle = "Erreur",
    sessionExpired = "Votre session a expiré. Veuillez vous reconnecter.",
    email = "E-mail",
    password = "Mot de passe",
    showPassword = "Afficher le mot de passe",
    hidePassword = "Masquer le mot de passe",
    login = "Se connecter",
    loginFailed = "Échec de la connexion",
    invalidCredentials = "E-mail ou mot de passe incorrect.",
    serverError = "Erreur serveur, réessayez plus tard.",
    registerHint = "Nouveau ? Créez un compte sur pushit.foxugly.com",
    backendProd = "Production",
    backendLocal = "Local",
    credit = "par ",
    notificationsTitle = "Notifications",
    settingsAction = "Paramètres",
    noNotifications = "Aucune notification pour l'instant",
    loadNotificationsFailed = "Échec du chargement des notifications",
    inboxUnread = "Non lues",
    inboxFolders = "Dossiers",
    noUnread = "Aucune notification non lue",
    markAllRead = "Tout marquer comme lu",
    loadOlder = "Charger plus ancien",
    markUnread = "Marquer comme non lu",
    deleteAction = "Supprimer",
    notificationTitle = "Notification",
    loadNotificationFailed = "Échec du chargement de la notification",
    createdLabel = "Créée",
    sentLabel = "Envoyée",
    timestampAt = "à",
    justNow = "à l'instant",
    minutesAgo = "il y a {n} min",
    hoursAgo = "il y a {n} h",
    yesterday = "hier",
    daysAgo = "il y a {n} j",
    settingsTitle = "Paramètres",
    account = "Compte",
    loadUserFailed = "Échec du chargement des infos utilisateur",
    userKey = "Clé utilisateur",
    appTokenSection = "Token applicatif",
    linkedAppsSection = "Applications liées",
    linkedPrefix = "Lié",
    notLinked = "Non lié à une application",
    rescanQr = "Re-scanner le QR code",
    scanQr = "Lier une application (QR)",
    unlinkDevice = "Dissocier cet appareil",
    unlinkApp = "Dissocier",
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
    cameraPermissionRationale = "L'accès à la caméra est nécessaire pour scanner les QR codes.",
    grantPermission = "Autoriser l'accès",
    cameraSetupFailed = "Impossible d'initialiser la caméra.",
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
    ok = "OK",
    errorTitle = "Fout",
    sessionExpired = "Je sessie is verlopen. Log opnieuw in.",
    email = "E-mail",
    password = "Wachtwoord",
    showPassword = "Wachtwoord tonen",
    hidePassword = "Wachtwoord verbergen",
    login = "Inloggen",
    loginFailed = "Inloggen mislukt",
    invalidCredentials = "Onjuist e-mailadres of wachtwoord.",
    serverError = "Serverfout, probeer het later opnieuw.",
    registerHint = "Nieuw hier? Maak een account aan op pushit.foxugly.com",
    backendProd = "Productie",
    backendLocal = "Lokaal",
    credit = "door ",
    notificationsTitle = "Notificaties",
    settingsAction = "Instellingen",
    noNotifications = "Nog geen notificaties",
    loadNotificationsFailed = "Laden van notificaties mislukt",
    inboxUnread = "Ongelezen",
    inboxFolders = "Mappen",
    noUnread = "Geen ongelezen notificaties",
    markAllRead = "Alles als gelezen markeren",
    loadOlder = "Oudere laden",
    markUnread = "Markeren als ongelezen",
    deleteAction = "Verwijderen",
    notificationTitle = "Notificatie",
    loadNotificationFailed = "Laden van notificatie mislukt",
    createdLabel = "Aangemaakt",
    sentLabel = "Verzonden",
    timestampAt = "om",
    justNow = "zojuist",
    minutesAgo = "{n} min geleden",
    hoursAgo = "{n} u geleden",
    yesterday = "gisteren",
    daysAgo = "{n} d geleden",
    settingsTitle = "Instellingen",
    account = "Account",
    loadUserFailed = "Laden van gebruikersgegevens mislukt",
    userKey = "Gebruikerssleutel",
    appTokenSection = "Applicatietoken",
    linkedAppsSection = "Gekoppelde applicaties",
    linkedPrefix = "Gekoppeld",
    notLinked = "Niet aan een applicatie gekoppeld",
    rescanQr = "QR-code opnieuw scannen",
    scanQr = "Een applicatie koppelen (QR)",
    unlinkDevice = "Dit toestel ontkoppelen",
    unlinkApp = "Ontkoppelen",
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
    cameraPermissionRationale = "Cameratoegang is vereist om QR-codes te scannen.",
    grantPermission = "Toegang verlenen",
    cameraSetupFailed = "Kan de camera niet starten.",
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
    ok = "OK",
    errorTitle = "Error",
    sessionExpired = "Your session has expired. Please log in again.",
    email = "Email",
    password = "Password",
    showPassword = "Show password",
    hidePassword = "Hide password",
    login = "Login",
    loginFailed = "Login failed",
    invalidCredentials = "Incorrect email or password.",
    serverError = "Server error, please try again later.",
    registerHint = "New here? Create an account at pushit.foxugly.com",
    backendProd = "Production",
    backendLocal = "Local",
    credit = "by ",
    notificationsTitle = "Notifications",
    settingsAction = "Settings",
    noNotifications = "No notifications yet",
    loadNotificationsFailed = "Failed to load notifications",
    inboxUnread = "Unread",
    inboxFolders = "Folders",
    noUnread = "No unread notifications",
    markAllRead = "Mark all as read",
    loadOlder = "Load older",
    markUnread = "Mark as unread",
    deleteAction = "Delete",
    notificationTitle = "Notification",
    loadNotificationFailed = "Failed to load notification",
    createdLabel = "Created",
    sentLabel = "Sent",
    timestampAt = "at",
    justNow = "just now",
    minutesAgo = "{n} min ago",
    hoursAgo = "{n} h ago",
    yesterday = "yesterday",
    daysAgo = "{n} d ago",
    settingsTitle = "Settings",
    account = "Account",
    loadUserFailed = "Failed to load user info",
    userKey = "User key",
    appTokenSection = "App token",
    linkedAppsSection = "Linked applications",
    linkedPrefix = "Linked",
    notLinked = "Not linked to an application",
    rescanQr = "Re-scan QR code",
    scanQr = "Link an application (QR)",
    unlinkDevice = "Unlink this device",
    unlinkApp = "Unlink",
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
    cameraPermissionRationale = "Camera permission is required to scan QR codes.",
    grantPermission = "Grant permission",
    cameraSetupFailed = "Could not start the camera.",
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
