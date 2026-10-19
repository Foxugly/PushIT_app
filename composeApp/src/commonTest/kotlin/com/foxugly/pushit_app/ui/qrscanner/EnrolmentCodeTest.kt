package com.foxugly.pushit_app.ui.qrscanner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnrolmentCodeTest {

    @Test
    fun acceptsTheEnrolmentCode() {
        // Ce que la console met dans le QR.
        assertTrue(looksLikeEnrolmentCode("apk_Ab12Cd34Ef56"))
        assertEquals(EnrolmentCodeCheck.VALID, checkEnrolmentCode("apk_Ab12Cd34Ef56"))
    }

    @Test
    fun rejectsASendTokenWithItsOwnVerdict() {
        // `apt_` est un jeton d'emission : il ne doit jamais etre stocke comme
        // code d'enrolement, et l'utilisateur doit savoir pourquoi.
        assertFalse(looksLikeEnrolmentCode("apt_0123456789abcdef0123456789abcdef"))
        assertEquals(EnrolmentCodeCheck.SEND_TOKEN, checkEnrolmentCode("apt_0123456789abcdef"))
    }

    @Test
    fun rejectsAnythingElse() {
        assertFalse(looksLikeEnrolmentCode("https://example.com"))
        assertFalse(looksLikeEnrolmentCode(""))
        assertFalse(looksLikeEnrolmentCode("APK_uppercase"))
        assertEquals(EnrolmentCodeCheck.INVALID, checkEnrolmentCode("https://example.com"))
    }
}
