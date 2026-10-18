package com.foxugly.pushit_app.ui.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLanguageTest {

    @Test
    fun savedChoiceWinsOverDeviceLanguage() {
        assertEquals(AppLanguage.EN, AppLanguage.resolve(stored = "en", deviceLanguage = "nl"))
    }

    @Test
    fun deviceLanguageUsedWhenNothingSaved() {
        assertEquals(AppLanguage.NL, AppLanguage.resolve(stored = null, deviceLanguage = "nl-BE"))
        assertEquals(AppLanguage.EN, AppLanguage.resolve(stored = null, deviceLanguage = "en_US"))
    }

    @Test
    fun unsupportedDeviceLanguageFallsBackToDefault() {
        assertEquals(AppLanguage.DEFAULT, AppLanguage.resolve(stored = null, deviceLanguage = "de"))
        assertEquals(AppLanguage.DEFAULT, AppLanguage.resolve(stored = null, deviceLanguage = null))
    }

    @Test
    fun unsupportedSavedValueFallsThroughToDevice() {
        assertEquals(AppLanguage.NL, AppLanguage.resolve(stored = "xx", deviceLanguage = "nl"))
    }
}
