package com.onemanarmy.radar.ui.common

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.onemanarmy.radar.domain.model.AppLanguage
import com.onemanarmy.radar.platform.AndroidLocaleHelper

@Composable
actual fun ProvideAppLocale(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseConfig = LocalConfiguration.current
    AndroidLocaleHelper.applyDefaultLocale(context, language)
    val localizedConfig = remember(context, baseConfig, language) {
        val localeList = AndroidLocaleHelper.resolveLocaleList(context, language)
        Configuration(baseConfig).apply {
            if (!localeList.isEmpty) {
                setLocale(localeList.get(0))
                setLocales(localeList)
            }
        }
    }
    CompositionLocalProvider(
        LocalConfiguration provides localizedConfig,
        content = content
    )
}
