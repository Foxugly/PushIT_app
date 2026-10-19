package com.foxugly.pushit_app.ui.qrscanner

/**
 * Ce que le scanner accepte : le code d'enrôlement `apk_`, et lui seul.
 *
 * `apt_` est un **jeton d'émission** : il sert à envoyer des notifications, pas
 * à rattacher un terminal, et il ne doit jamais atterrir sur un téléphone de
 * destinataire. On le reconnaît pour expliquer l'erreur à l'utilisateur au
 * lieu d'un « QR invalide » muet.
 *
 * Ne PAS élargir cette liste : la chaîne scannée est postée telle quelle à
 * `/devices/link/`, et c'est le serveur qui décide ce qu'elle ouvre. Ce filtre
 * n'est là que pour éviter d'enregistrer n'importe quel QR croisé dans la rue.
 */
private const val ENROLMENT_PREFIX = "apk_"
private const val SEND_TOKEN_PREFIX = "apt_"

enum class EnrolmentCodeCheck {
    /** Un code d'enrôlement `apk_`. */
    VALID,

    /** Un jeton d'émission `apt_` : pas un code d'enrôlement. */
    SEND_TOKEN,

    /** Autre chose. */
    INVALID,
}

fun checkEnrolmentCode(value: String): EnrolmentCodeCheck = when {
    value.startsWith(ENROLMENT_PREFIX) -> EnrolmentCodeCheck.VALID
    value.startsWith(SEND_TOKEN_PREFIX) -> EnrolmentCodeCheck.SEND_TOKEN
    else -> EnrolmentCodeCheck.INVALID
}

fun looksLikeEnrolmentCode(value: String): Boolean =
    checkEnrolmentCode(value) == EnrolmentCodeCheck.VALID
