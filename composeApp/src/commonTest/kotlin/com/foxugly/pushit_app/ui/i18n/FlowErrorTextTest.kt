package com.foxugly.pushit_app.ui.i18n

import com.foxugly.pushit_app.data.api.ApiException
import com.foxugly.pushit_app.data.api.SessionExpiredException
import com.foxugly.pushit_app.data.repository.AuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Login and enrolment-code error messages (flow audit 2026-10-19). */
class FlowErrorTextTest {

    private val en = stringsFor(AppLanguage.EN)

    @Test
    fun unverifiedEmailIsNotReportedAsBadCredentials() {
        val error = ApiException(403, "LoginResponse", "", AuthRepository.ERROR_EMAIL_NOT_VERIFIED)
        assertEquals(en.emailNotVerified, en.loginErrorText(error))
    }

    @Test
    fun rateLimitIsNotReportedAsBadCredentials() {
        val error = ApiException(429, "LoginResponse", "", AuthRepository.ERROR_THROTTLED)
        assertEquals(en.tooManyAttempts, en.loginErrorText(error))
    }

    @Test
    fun badCredentialsAndServerErrorsKeepTheirMessages() {
        assertEquals(en.invalidCredentials, en.loginErrorText(ApiException(400, "LoginResponse", "", "validation_error")))
        assertEquals(en.serverError, en.loginErrorText(ApiException(502, "LoginResponse", "")))
    }

    @Test
    fun refusedEnrolmentCodesGetFriendlyMessages() {
        assertEquals(en.enrolmentCodeInvalid, en.errorText(ApiException(403, "link", "Invalid app token. | app_token_invalid", "app_token_invalid"), "x"))
        assertEquals(en.enrolmentCodeInvalid, en.errorText(ApiException(403, "link", "", "app_token_invalid_format"), "x"))
        assertEquals(en.enrolmentCodeInactive, en.errorText(ApiException(403, "link", "", "app_token_inactive"), "x"))
        assertEquals(en.enrolmentCodeRevoked, en.errorText(ApiException(403, "link", "", "app_token_revoked"), "x"))
        // Servers before the 403 switch answered 401 with the same codes.
        assertEquals(en.enrolmentCodeInvalid, en.errorText(ApiException(401, "link", "", "app_token_invalid"), "x"))
    }

    @Test
    fun newMessagesAreTranslatedInEveryLanguage() {
        val pick: List<(Strings) -> String> = listOf(
            { it.emailNotVerified }, { it.resendConfirmation }, { it.confirmationResent }, { it.tooManyAttempts },
            { it.sendTokenNotEnrolmentCode }, { it.enrolmentCodeInvalid }, { it.enrolmentCodeInactive },
            { it.enrolmentCodeRevoked },
        )
        for (field in pick) {
            val messages = AppLanguage.entries.map { field(stringsFor(it)) }
            messages.forEach { assertTrue(it.isNotBlank()) }
            assertEquals(messages.size, messages.toSet().size, "each language needs its own text: $messages")
        }
    }

    @Test
    fun expiredSessionRaisesNoErrorMessage() {
        // The raw 401 `token_not_valid` body must never reach an error dialog.
        val expired = SessionExpiredException(ApiException(401, "auth getMe", "token_not_valid", "token_not_valid"))
        assertNull(en.errorText(expired, "fallback"))
    }
}
