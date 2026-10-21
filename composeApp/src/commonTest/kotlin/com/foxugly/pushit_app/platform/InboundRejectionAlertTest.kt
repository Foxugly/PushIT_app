package com.foxugly.pushit_app.platform

import com.foxugly.pushit_app.ui.i18n.AppLanguage
import com.foxugly.pushit_app.ui.i18n.stringsFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InboundRejectionAlertTest {

    // Exact shape sent by PushIT_server (notifications/inbound_mailbox.py).
    private fun payload(reasonText: String, appName: String = "Mon App") = mapOf(
        "type" to "inbound_email_rejected",
        "application_id" to "42",
        "application_name" to appName,
        "title" to "PushIT: inbound e-mail refused",
        "message" to "Mail from a***@example.com to $appName refused: $reasonText.",
    )

    @Test
    fun otherPushTypesAreNotAlerts() {
        assertNull(parseInboundRejectionAlert(mapOf("notification_id" to "7", "title" to "Hi")))
        assertNull(parseInboundRejectionAlert(mapOf("type" to "notification")))
        assertNull(parseInboundRejectionAlert(emptyMap()))
    }

    @Test
    fun parsesNotAllowed() {
        val alert = parseInboundRejectionAlert(
            payload("the sender is neither the owner nor on the application's allowlist"),
        )!!
        assertEquals(42, alert.applicationId)
        assertEquals("Mon App", alert.applicationName)
        assertEquals(InboundRejectionReason.NOT_ALLOWED, alert.reason)
        assertEquals("a***@example.com", alert.maskedSender)
    }

    @Test
    fun parsesBlocked() {
        val alert = parseInboundRejectionAlert(payload("the sender is on the application's blocklist"))!!
        assertEquals(InboundRejectionReason.BLOCKED, alert.reason)
    }

    @Test
    fun parsesDmarcFailureWithVerdictDetail() {
        val alert = parseInboundRejectionAlert(
            payload("the sender failed authentication (SPF/DKIM/DMARC) (dmarc=fail)"),
        )!!
        assertEquals(InboundRejectionReason.NOT_AUTHENTICATED, alert.reason)
    }

    @Test
    fun missingOrOddFieldsDoNotInventData() {
        val alert = parseInboundRejectionAlert(
            mapOf("type" to "inbound_email_rejected", "application_id" to "x", "application_name" to " "),
        )!!
        assertNull(alert.applicationId)
        assertNull(alert.applicationName)
        assertNull(alert.maskedSender)
        assertEquals(InboundRejectionReason.UNKNOWN, alert.reason)
    }

    @Test
    fun mapsToLocalizedTitleAndBody() {
        val alert = parseInboundRejectionAlert(payload("the sender is on the application's blocklist"))!!
        val fr = stringsFor(AppLanguage.FR)
        assertEquals("Mail refusé — Mon App", fr.inboundRejectionTitle(alert))
        assertEquals(
            "L'expéditeur est bloqué pour cette application. Expéditeur : a***@example.com",
            fr.inboundRejectionBody(alert),
        )
        val en = stringsFor(AppLanguage.EN)
        assertEquals("E-mail refused — Mon App", en.inboundRejectionTitle(alert))
        assertTrue(en.inboundRejectionBody(alert).startsWith("The sender is blocked"))
        val nl = stringsFor(AppLanguage.NL)
        assertEquals("Mail geweigerd — Mon App", nl.inboundRejectionTitle(alert))
    }

    @Test
    fun bodyWithoutSenderOrAppNameStaysGeneric() {
        val alert = InboundRejectionAlert(null, null, InboundRejectionReason.NOT_AUTHENTICATED, null)
        val en = stringsFor(AppLanguage.EN)
        assertEquals("E-mail refused", en.inboundRejectionTitle(alert))
        assertEquals("The anti-spoofing check (SPF/DKIM/DMARC) failed.", en.inboundRejectionBody(alert))
        assertFalse("@" in en.inboundRejectionBody(alert))
    }

    @Test
    fun everyLanguageHasTheAlertStrings() {
        AppLanguage.entries.map(::stringsFor).forEach { s ->
            assertTrue("{app}" in s.inboundRejectedTitle)
            assertTrue("{sender}" in s.inboundRejectedFrom)
            assertTrue(s.inboundRejectedChannel.isNotBlank())
        }
    }
}
