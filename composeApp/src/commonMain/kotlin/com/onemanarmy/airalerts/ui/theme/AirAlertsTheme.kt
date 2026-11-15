package com.onemanarmy.airalerts.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AlertColors {
    val SafeGreen = Color(0xFF22C55E)
    val SafeGreenContainerLight = Color(0xFFD6F5E3)
    val SafeGreenContainerDark = Color(0xFF0D3321)
    val SafeOnContainerLight = Color(0xFF09381E)
    val SafeOnContainerDark = Color(0xFFD1FAE5)

    val AlertRed = Color(0xFFEF4444)
    val CriticalRed = Color(0xFFC81E1E)
    val AlertRedContainerLight = Color(0xFFFFDAD6)
    val AlertRedContainerDark = Color(0xFF7A161A)
    val AlertOnContainerLight = Color(0xFF410002)
    val AlertOnContainerDark = Color(0xFFFEE2E2)

    val WarningAmber = Color(0xFFF59E0B)
    val WarningAmberContainerLight = Color(0xFFFFF0D4)
    val WarningAmberContainerDark = Color(0xFF472905)
    val WarningOnContainerDark = Color(0xFFFDE68A)
}

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1B5E8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E8FF),
    onPrimaryContainer = Color(0xFF001D33),
    secondary = Color(0xFF4E616D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E5F4),
    onSecondaryContainer = Color(0xFF0A1E28),
    tertiary = AlertColors.SafeGreen,
    error = AlertColors.AlertRed,
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFE7EDF3),
    onSurfaceVariant = Color(0xFF42474E)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF5BB2FF),
    onPrimary = Color(0xFF002845),
    primaryContainer = Color(0xFF113756),
    onPrimaryContainer = Color(0xFFD4ECFF),
    secondary = Color(0xFF9CB3C9),
    onSecondary = Color(0xFF172736),
    secondaryContainer = Color(0xFF213040),
    onSecondaryContainer = Color(0xFFD7E6F5),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF003824),
    tertiaryContainer = Color(0xFF0E3B2A),
    onTertiaryContainer = Color(0xFFA7F3D0),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF6E161B),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF0B1017),
    onBackground = Color(0xFFE8EEF5),
    surface = Color(0xFF121923),
    onSurface = Color(0xFFE8EEF5),
    surfaceVariant = Color(0xFF1C2533),
    onSurfaceVariant = Color(0xFF9AA8B8),
    surfaceContainerLowest = Color(0xFF080C12),
    surfaceContainerLow = Color(0xFF101720),
    surfaceContainer = Color(0xFF151E2A),
    surfaceContainerHigh = Color(0xFF1B2635),
    surfaceContainerHighest = Color(0xFF233042),
    outline = Color(0xFF2D3A4B),
    outlineVariant = Color(0xFF1E2836)
)

private val AirAlertsTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.15).sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.05.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    )
)

@Composable
fun AirAlertsTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AirAlertsTypography,
        content = content
    )
}
