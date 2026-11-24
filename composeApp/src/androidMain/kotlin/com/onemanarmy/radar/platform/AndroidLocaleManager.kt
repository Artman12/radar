package com.onemanarmy.radar.platform

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.onemanarmy.radar.domain.model.AppLanguage
import java.util.Locale

object AndroidLocaleHelper {
    private const val PREFS_NAME = "radar_locale_prefs"
    private const val KEY_LANGUAGE_TAG = "app_language_tag"

    @Volatile
    private var cachedLanguage: AppLanguage? = null

    fun getSavedLanguage(context: Context): AppLanguage {
        cachedLanguage?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LANGUAGE_TAG, AppLanguage.UKRAINIAN.tag)
        val resolved = AppLanguage.fromTag(raw)
        cachedLanguage = resolved
        return resolved
    }

    fun saveLanguage(context: Context, language: AppLanguage) {
        cachedLanguage = language
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, language.tag)
            .apply()
    }

    fun resolveLocaleList(
        context: Context,
        language: AppLanguage = getSavedLanguage(context)
    ): LocaleList {
        val resolved = when (language) {
            AppLanguage.SYSTEM -> resolveSystemOrAppInfoLocales(context)
            AppLanguage.UKRAINIAN,
            AppLanguage.ENGLISH -> LocaleList(Locale.forLanguageTag(language.tag))
        }
        return if (resolved.isEmpty) {
            LocaleList(Locale.forLanguageTag(AppLanguage.UKRAINIAN.tag))
        } else {
            resolved
        }
    }

    private fun resolveSystemOrAppInfoLocales(context: Context): LocaleList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val appLocales = runCatching {
                context.getSystemService(LocaleManager::class.java)?.applicationLocales
            }.getOrNull()
            if (appLocales != null && !appLocales.isEmpty) {
                return appLocales
            }
        }
        return Resources.getSystem().configuration.locales
    }

    fun applyDefaultLocale(
        context: Context,
        language: AppLanguage = getSavedLanguage(context)
    ) {
        val localeList = resolveLocaleList(context, language)
        if (!localeList.isEmpty) {
            Locale.setDefault(localeList.get(0))
            LocaleList.setDefault(localeList)
        }
    }

    fun wrapContext(
        base: Context,
        language: AppLanguage = getSavedLanguage(base)
    ): Context {
        val localeList = resolveLocaleList(base, language)
        if (localeList.isEmpty) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(localeList.get(0))
        config.setLocales(localeList)
        return base.createConfigurationContext(config)
    }
}

class AndroidPlatformLocaleController(
    private val context: Context
) : PlatformLocaleController {
    override fun applyLanguage(language: AppLanguage) {
        AndroidLocaleHelper.saveLanguage(context, language)
        AndroidLocaleHelper.applyDefaultLocale(context, language)
    }
}
