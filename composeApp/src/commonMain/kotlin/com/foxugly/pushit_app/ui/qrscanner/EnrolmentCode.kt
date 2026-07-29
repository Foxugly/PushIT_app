package com.foxugly.pushit_app.ui.qrscanner

/**
 * Ce que le scanner accepte.
 *
 * `apk_` est le code d'enrôlement : c'est lui que la console met désormais dans
 * le QR. `apt_` reste accepté parce que des applications affichent encore leur
 * ancien jeton, et que le serveur l'accepte toujours à l'enrôlement.
 *
 * Ne PAS élargir cette liste : la chaîne scannée est postée telle quelle à
 * `/devices/link/`, et c'est le serveur qui décide ce qu'elle ouvre. Ce filtre
 * n'est là que pour éviter d'enregistrer n'importe quel QR croisé dans la rue.
 */
private val ACCEPTED_PREFIXES = listOf("apk_", "apt_")

fun looksLikeEnrolmentCode(value: String): Boolean =
    ACCEPTED_PREFIXES.any { value.startsWith(it) }
