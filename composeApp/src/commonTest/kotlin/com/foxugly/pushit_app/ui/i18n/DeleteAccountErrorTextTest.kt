package com.foxugly.pushit_app.ui.i18n

import com.foxugly.pushit_app.data.api.ApiException
import com.foxugly.pushit_app.data.repository.AuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DeleteAccountErrorTextTest {

    private val en = stringsFor(AppLanguage.EN)

    @Test
    fun billingUnavailableMapsToDedicatedMessage() {
        val error = ApiException(503, "deleteAccount", "", AuthRepository.ERROR_BILLING_UNAVAILABLE)
        assertEquals(en.deleteAccountBillingUnavailable, en.deleteAccountErrorText(error))
    }

    @Test
    fun wrongPasswordMapsToPasswordMessage() {
        val error = ApiException(400, "deleteAccount", "", AuthRepository.ERROR_CURRENT_PASSWORD_INVALID)
        assertEquals(en.currentPasswordInvalid, en.deleteAccountErrorText(error))
    }

    @Test
    fun otherServerErrorDoesNotUseBillingMessage() {
        val error = ApiException(500, "deleteAccount", "")
        assertNotEquals(en.deleteAccountBillingUnavailable, en.deleteAccountErrorText(error))
    }

    @Test
    fun billingMessageIsTranslatedInEveryLanguage() {
        val messages = AppLanguage.entries.map { stringsFor(it).deleteAccountBillingUnavailable }
        messages.forEach { assert(it.isNotBlank()) }
        assertEquals(messages.size, messages.toSet().size)
    }
}
