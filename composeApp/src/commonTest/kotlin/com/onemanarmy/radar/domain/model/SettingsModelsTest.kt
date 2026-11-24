package com.onemanarmy.radar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsModelsTest {

    @Test
    fun defaultLanguageIsUkrainian() {
        assertEquals(AppLanguage.UKRAINIAN, AppSettings().appLanguage)
    }

    @Test
    fun fromTagResolvesSupportedLanguagesAndDefaultsToUkrainian() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("system"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("SYSTEM"))
        assertEquals(AppLanguage.UKRAINIAN, AppLanguage.fromTag("uk"))
        assertEquals(AppLanguage.UKRAINIAN, AppLanguage.fromTag("UK-ua"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-US"))
        assertEquals(AppLanguage.UKRAINIAN, AppLanguage.fromTag("de"))
        assertEquals(AppLanguage.UKRAINIAN, AppLanguage.fromTag(null))
    }
}
