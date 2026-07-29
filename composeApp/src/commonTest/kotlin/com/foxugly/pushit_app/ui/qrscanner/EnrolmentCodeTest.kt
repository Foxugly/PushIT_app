package com.foxugly.pushit_app.ui.qrscanner

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnrolmentCodeTest {

    @Test
    fun acceptsTheEnrolmentCode() {
        // Ce que la console met desormais dans le QR. Le refuser rendrait tout
        // nouveau QR inutilisable -- c'est exactement ce que faisait le filtre
        // precedent, qui n'acceptait que `apt_`.
        assertTrue(looksLikeEnrolmentCode("apk_Ab12Cd34Ef56"))
    }

    @Test
    fun stillAcceptsTheLegacyToken() {
        // Des applications affichent encore leur ancien jeton, et le serveur
        // l'accepte toujours a l'enrolement.
        assertTrue(looksLikeEnrolmentCode("apt_0123456789abcdef0123456789abcdef"))
    }

    @Test
    fun rejectsAnythingElse() {
        assertFalse(looksLikeEnrolmentCode("https://example.com"))
        assertFalse(looksLikeEnrolmentCode(""))
        assertFalse(looksLikeEnrolmentCode("APK_uppercase"))
    }
}
