package com.onemanarmy.radar.ui.common

import androidx.compose.runtime.Composable
import com.onemanarmy.radar.domain.model.AppLanguage

@Composable
expect fun ProvideAppLocale(
    language: AppLanguage,
    content: @Composable () -> Unit
)
