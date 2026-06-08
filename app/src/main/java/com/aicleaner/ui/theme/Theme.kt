package com.aicleaner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color(0xFF001F17),
    primaryContainer = PrimaryLight,
    onPrimaryContainer = Color(0xFF003827),
    secondary = AccentGreen,
    onSecondary = Color(0xFF00210F),
    tertiary = AccentOrange,
    onTertiary = Color(0xFF291800),
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceRaisedLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight,
    error = AccentRed,
    onError = Color(0xFF3B000A),
    errorContainer = AccentRedLight,
    onErrorContainer = Color(0xFF4B0010),
)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color(0xFF00261C),
    primaryContainer = PrimaryDark,
    onPrimaryContainer = Color(0xFFD6FFF2),
    secondary = AccentGreen,
    onSecondary = Color(0xFF00210F),
    tertiary = AccentOrange,
    onTertiary = Color(0xFF2B1800),
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceRaisedDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    error = AccentRed,
    onError = Color(0xFF3B000A),
    errorContainer = Color(0xFF3A141B),
    onErrorContainer = Color(0xFFFFD7DD),
)

@Composable
fun AIStorageCleanerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
