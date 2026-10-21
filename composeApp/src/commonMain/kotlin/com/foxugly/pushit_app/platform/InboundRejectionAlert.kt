package com.foxugly.pushit_app.platform

import com.foxugly.pushit_app.ui.i18n.Strings

/**
 * Why the server refused an inbound e-mail to one of the owner's applications.
 * Maps the server's `_REASON_TEXT` (PushIT_server `notifications/inbound_mailbox.py`).
 */
enum class InboundRejectionReason { NOT_ALLOWED, BLOCKED, NOT_AUTHENTICATED, UNKNOWN }

/**
 * System push sent to an application OWNER when an e-mail to the app's inbound
 * alias was refused (sender policy / blocklist / DMARC). It is not a PushIT
 * notification: the server creates no `Notification` row, so it never appears in
 * the inbox list.
 *
 * Server data (all strings): `type=inbound_email_rejected`, `application_id`,
 * `application_name`, `title` ("PushIT: inbound e-mail refused") and `message`
 * ("Mail from <masked sender> to <app> refused: <English reason>."). Newer servers
 * (data-only on Android) add `reason` (`sender_not_allowed` | `sender_blocked` |
 * `dmarc_failed`) and `sender_masked` (already masked, e.g. `a***@example.com`);
 * those win when present. Older servers send neither: [reason] is then recognised
 * from the English reason text and [maskedSender] read from the same text.
 */
data class InboundRejectionAlert(
    val applicationId: Int?,
    val applicationName: String?,
    val reason: InboundRejectionReason,
    val maskedSender: String?,
)

const val INBOUND_EMAIL_REJECTED_TYPE = "inbound_email_rejected"

private val SENDER_PATTERN = Regex("""^Mail from (\S+@\S+) to """)

/** The alert carried by an FCM data map, or null when it is another kind of push. */
fun parseInboundRejectionAlert(data: Map<String, String>): InboundRejectionAlert? {
    if (data["type"]?.trim() != INBOUND_EMAIL_REJECTED_TYPE) return null
    val message = data["message"].orEmpty()
    return InboundRejectionAlert(
        applicationId = data["application_id"]?.trim()?.toIntOrNull()?.takeIf { it > 0 },
        applicationName = data["application_name"]?.trim()?.takeIf { it.isNotEmpty() },
        reason = inboundRejectionReasonFromCode(data["reason"]) ?: inboundRejectionReasonOf(message),
        maskedSender = data["sender_masked"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: SENDER_PATTERN.find(message.trim())?.groupValues?.get(1),
    )
}

/** Reason from the server's `reason` code, or null when absent/unknown (fall back to the text). */
fun inboundRejectionReasonFromCode(code: String?): InboundRejectionReason? =
    when (code?.trim()?.lowercase()) {
        "sender_not_allowed" -> InboundRejectionReason.NOT_ALLOWED
        "sender_blocked" -> InboundRejectionReason.BLOCKED
        "dmarc_failed" -> InboundRejectionReason.NOT_AUTHENTICATED
        else -> null
    }

/** Reason recognised from the server's English push text. */
fun inboundRejectionReasonOf(message: String): InboundRejectionReason {
    val text = message.lowercase()
    return when {
        "blocklist" in text -> InboundRejectionReason.BLOCKED
        "dmarc" in text || "failed authentication" in text -> InboundRejectionReason.NOT_AUTHENTICATED
        "allowlist" in text || "neither the owner" in text -> InboundRejectionReason.NOT_ALLOWED
        else -> InboundRejectionReason.UNKNOWN
    }
}

/** "Mail refusé — <app>" (or without the app name when the payload lacks it). */
fun Strings.inboundRejectionTitle(alert: InboundRejectionAlert): String =
    alert.applicationName?.let { inboundRejectedTitle.replace("{app}", it) } ?: inboundRejectedTitleNoApp

/** The reason in plain words, then the (masked) sender when the payload carries one. */
fun Strings.inboundRejectionBody(alert: InboundRejectionAlert): String {
    val reason = when (alert.reason) {
        InboundRejectionReason.NOT_ALLOWED -> inboundRejectedNotAllowed
        InboundRejectionReason.BLOCKED -> inboundRejectedBlocked
        InboundRejectionReason.NOT_AUTHENTICATED -> inboundRejectedNotAuthenticated
        InboundRejectionReason.UNKNOWN -> inboundRejectedUnknown
    }
    return alert.maskedSender?.let { "$reason " + inboundRejectedFrom.replace("{sender}", it) } ?: reason
}
